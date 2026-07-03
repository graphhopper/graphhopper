/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.osm

import com.carrotsearch.hppc.BitSet
import com.graphhopper.coll.GHLongLongHashMap
import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.ReaderNode
import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderWay
import com.graphhopper.reader.dem.EdgeElevationSmoothingMovingAverage
import com.graphhopper.reader.dem.EdgeElevationSmoothingRamer
import com.graphhopper.reader.dem.EdgeSampling
import com.graphhopper.reader.dem.ElevationProvider
import com.graphhopper.routing.OSMReaderConfig
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.State
import com.graphhopper.routing.util.AreaIndex
import com.graphhopper.routing.util.CustomArea
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.OSMParsers
import com.graphhopper.routing.util.parsers.RestrictionSetter
import com.graphhopper.search.KVStorage
import com.graphhopper.search.KVStorage.KValue
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility.OSM_WARNING_LOGGER
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.Parameters.Details.MOTORWAY_JUNCTION
import com.graphhopper.util.Parameters.Details.STREET_DESTINATION
import com.graphhopper.util.Parameters.Details.STREET_DESTINATION_REF
import com.graphhopper.util.Parameters.Details.STREET_NAME
import com.graphhopper.util.Parameters.Details.STREET_REF
import com.graphhopper.util.PointList
import com.graphhopper.util.RamerDouglasPeucker
import com.graphhopper.util.StopWatch
import com.graphhopper.util.shapes.GHPoint
import com.graphhopper.util.shapes.GHPoint3D
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.Date
import java.util.regex.Pattern

/**
 * Parses an OSM file (xml, zipped xml or pbf) and creates a graph from it. The OSM file is actually read twice.
 * During the first scan we determine the 'type' of each node, i.e. we check whether a node only appears in a single way
 * or represents an intersection of multiple ways, or connects two ways. We also scan the relations and store them for
 * each way ID in memory.
 * During the second scan we store the coordinates of the nodes that belong to ways in memory and then split each way
 * into several segments that are divided by intersections or barrier nodes. Each segment is added as an edge of the
 * resulting graph. Afterwards we scan the relations again to determine turn restrictions.
 */
open class OSMReader(private val baseGraph: BaseGraph, private val osmParsers: OSMParsers, private val config: OSMReaderConfig) {

    private val edgeIntAccess = baseGraph.edgeAccess
    private val nodeAccess = baseGraph.nodeAccess
    private val turnCostStorage = baseGraph.turnCostStorage
    private val distCalc = DistanceCalcEarth.DIST_EARTH
    private val restrictionSetter: RestrictionSetter
    private var eleProvider: ElevationProvider = ElevationProvider.NOOP
    private var areaIndex: AreaIndex<CustomArea>? = null
    private var osmFile: File? = null
    private val simplifyAlgo = RamerDouglasPeucker()
    private var bugCounter = 0
    private val tempRelFlags: IntsRef
    private var osmDataDate: Date? = null
    private var zeroCounter = 0L

    private var osmWayIdToRelationFlagsMap: GHLongLongHashMap? = GHLongLongHashMap(200, .5)
    private var restrictedWaysToEdgesMap: WayToEdgesMap? = WayToEdgesMap()
    private var restrictionRelations: MutableList<ReaderRelation>? = ArrayList()

    init {
        restrictionSetter = RestrictionSetter(baseGraph, osmParsers.restrictionTagParsers.map { it.turnRestrictionEnc!! })

        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance())
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance())

        tempRelFlags = osmParsers.createRelationFlags()
        if (tempRelFlags.length != 2)
            // we use a long to store relation flags currently, so the relation flags ints ref must have length 2
            throw IllegalArgumentException("OSMReader cannot use relation flags with != 2 integers")
    }

    /**
     * Sets the OSM file to be read.  Supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    fun setFile(osmFile: File): OSMReader {
        this.osmFile = osmFile
        return this
    }

    /**
     * The area index is queried for each OSM way and the associated areas are added to the way's tags
     */
    fun setAreaIndex(areaIndex: AreaIndex<CustomArea>): OSMReader {
        this.areaIndex = areaIndex
        return this
    }

    fun setElevationProvider(eleProvider: ElevationProvider?): OSMReader {
        if (eleProvider == null)
            throw IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider")

        if (!nodeAccess.is3D() && ElevationProvider.NOOP !== eleProvider)
            throw IllegalStateException("Make sure you graph accepts 3D data")

        this.eleProvider = eleProvider
        return this
    }

    @Throws(IOException::class)
    fun readGraph() {
        if (osmParsers == null)
            throw IllegalStateException("Tag parsers were not set.")

        val osmFile = this.osmFile
            ?: throw IllegalStateException("No OSM file specified")

        if (!osmFile.exists())
            throw IllegalStateException("Your specified OSM file does not exist:" + osmFile.absolutePath)

        if (!baseGraph.isInitialized)
            throw IllegalStateException("BaseGraph must be initialize before we can read OSM")

        val waySegmentParser = WaySegmentParser.Builder(baseGraph.nodeAccess, baseGraph.directory)
            .setWayFilter { way -> acceptWay(way) }
            .setSplitNodeFilter { node -> isBarrierNode(node) }
            .setWayPreprocessor { way, coordinateSupplier, nodeTagSupplier -> preprocessWay(way, coordinateSupplier, nodeTagSupplier) }
            .setRelationPreprocessor { relation -> preprocessRelations(relation) }
            .setRelationProcessor { relation, map -> processRelation(relation, map) }
            .setEdgeHandler { from, to, pointList, way, nodeTags -> addEdge(from, to, pointList, way, nodeTags) }
            .setWorkerThreads(config.getWorkerThreads())
            .build()
        waySegmentParser.readOSM(osmFile)
        osmDataDate = waySegmentParser.getTimestamp()
        if (baseGraph.nodes == 0)
            throw RuntimeException("Graph after reading OSM must not be empty")
        releaseEverythingExceptRestrictionData()
        addRestrictionsToGraph()
        releaseRestrictionData()
        LOGGER.info("Finished reading OSM file: {}, nodes: {}, edges: {}, zero distance edges: {}",
            osmFile.absolutePath, nf(baseGraph.nodes.toLong()), nf(baseGraph.edges.toLong()), nf(zeroCounter))
    }

    /**
     * @return the timestamp given in the OSM file header or null if not found
     */
    fun getDataDate(): Date? = osmDataDate

    private fun lookupElevation(lat: Double, lon: Double): Double {
        val ele = eleProvider.getEle(lat, lon)
        return if (ele.isNaN()) config.getDefaultElevation() else ele
    }

    /**
     * This method is called for each way during the first and second pass of the [WaySegmentParser]. All OSM
     * ways that are not accepted here and all nodes that are not referenced by any such way will be ignored.
     */
    protected open fun acceptWay(way: ReaderWay): Boolean {
        // ignore broken geometry
        if (way.nodes.size() < 2)
            return false

        // ignore multipolygon geometry
        if (!way.hasTags())
            return false

        return osmParsers.acceptWay(way)
    }

    /**
     * @return true if the given node should be duplicated to create an artificial edge. If the node turns out to be a
     * junction between different ways this will be ignored and no artificial edge will be created.
     */
    protected open fun isBarrierNode(node: ReaderNode): Boolean =
        node.hasTag("barrier") || node.hasTag("ford")

    /**
     * @return true if the length of the way shall be calculated and added as an artificial way tag
     */
    protected open fun isCalculateWayDistance(way: ReaderWay): Boolean = isFerry(way)

    private fun isFerry(way: ReaderWay): Boolean = FerrySpeedCalculator.isFerry(way)

    /**
     * This method is called during the second pass of [WaySegmentParser] and provides an entry point to enrich
     * the given OSM way with additional tags before it is passed on to the tag parsers.
     */
    protected open fun setArtificialWayTags(pointList: PointList, way: ReaderWay, distance: Double, nodeTags: List<Map<String, Any>>) {
        way.setTag("node_tags", nodeTags)
        way.setTag("edge_distance", distance)
        way.setTag("point_list", pointList)

        // we have to remove existing artificial tags, because we modify the way even though there can be multiple edges
        // per way. sooner or later we should separate the artificial ('edge') tags from the way, see discussion here:
        // https://github.com/graphhopper/graphhopper/pull/2457#discussion_r751155404
        way.removeTag("country")
        way.removeTag("country_rule")
        way.removeTag("custom_areas")

        val customAreas: List<CustomArea>
        val areaIndex = this.areaIndex
        if (areaIndex != null) {
            val middleLat: Double
            val middleLon: Double
            if (pointList.size() > 2) {
                middleLat = pointList.getLat(pointList.size() / 2)
                middleLon = pointList.getLon(pointList.size() / 2)
            } else {
                val firstLat = pointList.getLat(0)
                val firstLon = pointList.getLon(0)
                val lastLat = pointList.getLat(pointList.size() - 1)
                val lastLon = pointList.getLon(pointList.size() - 1)
                middleLat = (firstLat + lastLat) / 2
                middleLon = (firstLon + lastLon) / 2
            }
            customAreas = areaIndex.query(middleLat, middleLon)
        } else {
            customAreas = emptyList()
        }

        // special handling for countries: since they are built-in with GraphHopper they are always fed to the EncodingManager
        var country = Country.MISSING
        var state = State.MISSING
        var countryArea = Double.POSITIVE_INFINITY
        for (customArea in customAreas) {
            // ignore areas that aren't countries
            if (customArea.properties == null) continue
            val alpha2WithSubdivision = customArea.properties[State.ISO_3166_2] as String?
                ?: continue

            // the country string must be either something like US-CA (including subdivision) or just DE
            val strs = alpha2WithSubdivision.split("-".toRegex()).dropLastWhile { it.isEmpty() }
            if (strs.isEmpty() || strs.size > 2)
                throw IllegalStateException("Invalid alpha2: $alpha2WithSubdivision")
            val c = Country.find(strs[0])
                ?: throw IllegalStateException("Unknown country: " + strs[0])

            if (
            // countries with subdivision overrule those without subdivision as well as bigger ones with subdivision
                strs.size == 2 && (state == State.MISSING || customArea.area < countryArea)
                // countries without subdivision only overrule bigger ones without subdivision
                || strs.size == 1 && (state == State.MISSING && customArea.area < countryArea)) {
                country = c
                state = State.find(alpha2WithSubdivision)
                countryArea = customArea.area
            }
        }
        way.setTag("country", country)
        way.setTag("country_state", state)

        // also add all custom areas as artificial tag
        way.setTag("custom_areas", customAreas)
    }

    /**
     * This method is called for each segment an OSM way is split into during the second pass of [WaySegmentParser].
     *
     * @param fromIndex a unique integer id for the first node of this segment
     * @param toIndex   a unique integer id for the last node of this segment
     * @param pointList coordinates of this segment
     * @param way       the OSM way this segment was taken from
     * @param nodeTags  node tags of this segment. there is one map of tags for each point.
     */
    protected open fun addEdge(fromIndex: Int, toIndex: Int, pointList: PointList, way: ReaderWay, nodeTags: List<Map<String, Any>>) {
        var pointList = pointList
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw AssertionError("to or from index is invalid for this edge $fromIndex->$toIndex, points:$pointList")
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension())
        if (pointList.size() != nodeTags.size)
            throw AssertionError("there should be as many maps of node tags as there are points. node tags: " + nodeTags.size + ", points: " + pointList.size())

        if (pointList.is3D()) {
            // fill in all elevations (deferred from node scanning for cache-friendliness in elevation provider)
            val last = pointList.size() - 1
            for (i in 0..last) {
                val ele: Double
                if (i == 0 || i == last) {
                    // tower node: reuse elevation if already looked up by a previous edge
                    val towerIndex = if (i == 0) fromIndex else toIndex
                    var towerEle = nodeAccess.getEle(towerIndex)
                    if (towerEle == Helper.ELE_UNKNOWN) {
                        towerEle = lookupElevation(pointList.getLat(i), pointList.getLon(i))
                        nodeAccess.setNode(towerIndex, pointList.getLat(i), pointList.getLon(i), towerEle)
                    }
                    ele = towerEle
                } else {
                    ele = lookupElevation(pointList.getLat(i), pointList.getLon(i))
                }
                pointList.setElevation(i, ele)
            }
            // sample points along long edges
            if (config.getLongEdgeSamplingDistance() < Double.MAX_VALUE && !isFerry(way))
                pointList = EdgeSampling.sample(pointList, config.getLongEdgeSamplingDistance(), distCalc, eleProvider)

            // smooth the elevation before calculating the distance because the distance will be incorrect if calculated afterwards
            if (config.getElevationSmoothing() == "ramer")
                EdgeElevationSmoothingRamer.smooth(pointList, config.getElevationSmoothingRamerMax().toDouble())
            else if (config.getElevationSmoothing() == "moving_average")
                EdgeElevationSmoothingMovingAverage.smooth(pointList, config.getSmoothElevationAverageWindowSize())
            else if (!config.getElevationSmoothing().isEmpty())
                throw AssertionError("Unsupported elevation smoothing algorithm: '" + config.getElevationSmoothing() + "'")
        }

        if (config.getMaxWayPointDistance() > 0 && pointList.size() > 2)
            simplifyAlgo.simplify(pointList)

        var distance = distCalc.calcDistance(pointList)

        if (distance < 0.001) {
            // As investigation shows often two paths should have crossed via one identical point
            // but end up in two very close points.
            zeroCounter++
            distance = 0.001
        }

        val maxDistance = BaseGraph.MAX_DIST_METERS
        if (distance.isNaN()) {
            LOGGER.warn("Bug in OSM or GraphHopper (" + bugCounter++ + "). Illegal tower node distance " + distance + " reset to 1m, osm way " + way.id)
            distance = 1.0
        }

        if (distance.isInfinite() || distance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            // For example this happens here: https://www.openstreetmap.org/way/672506453 (Cape Town - Tristan da Cunha ferry)
            LOGGER.warn("Bug in OSM or GraphHopper (" + bugCounter++ + "). Too big tower node distance " + distance + " reset to large value, osm way " + way.id)
            distance = maxDistance
        }

        if (bugCounter > 30)
            throw IllegalStateException("Too many bugs in OSM or GraphHopper encountered $bugCounter")

        setArtificialWayTags(pointList, way, distance, nodeTags)
        val relationFlags = getRelFlagsMap(way.id)
        val edge = baseGraph.edge(fromIndex, toIndex).setDistance(distance)
        osmParsers.handleWayTags(edge.edge, edgeIntAccess, way, relationFlags)
        val map = way.getTag("key_values", Collections.emptyMap<String, KValue>())
        if (!map.isEmpty())
            edge.setKeyValues(map)

        // If the entire way is just the first and last point, do not waste space storing an empty way geometry
        if (pointList.size() > 2) {
            // the geometry consists only of pillar nodes, but we check that the first and last points of the pointList
            // are equal to the tower node coordinates
            checkCoordinates(fromIndex, pointList.get(0))
            checkCoordinates(toIndex, pointList.get(pointList.size() - 1))
            edge.setWayGeometry(pointList.shallowCopy(1, pointList.size() - 1, false))
        }

        checkDistance(way.id, edge)
        restrictedWaysToEdgesMap!!.putIfReserved(way.id, edge.edge)
    }

    private fun checkCoordinates(nodeIndex: Int, point: GHPoint) {
        val tolerance = 1.0e-6
        if (Math.abs(nodeAccess.getLat(nodeIndex) - point.getLat()) > tolerance || Math.abs(nodeAccess.getLon(nodeIndex) - point.getLon()) > tolerance)
            throw IllegalStateException("Suspicious coordinates for node " + nodeIndex + ": (" + nodeAccess.getLat(nodeIndex) + "," + nodeAccess.getLon(nodeIndex) + ") vs. (" + point + ")")
    }

    private fun checkDistance(readerWayId: Long, edge: EdgeIteratorState) {
        val tolerance = 1.0
        val edgeDistance = edge.distance
        val pointList = edge.fetchWayGeometry(FetchMode.ALL)
        val geometryDistance = distCalc.calcDistance(pointList)
        if (edgeDistance.isInfinite())
            throw IllegalStateException("Infinite edge distance should never occur, as we are supposed to limit each distance to the maximum distance we can store, #435. wayId=$readerWayId")
        else if (edgeDistance > 2_000_000)
            LOGGER.warn("Very long edge detected: $edge ( wayId=$readerWayId), dist: $edgeDistance")
        else if (Math.abs(edgeDistance - geometryDistance) > tolerance)
            throw IllegalStateException("Suspicious distance for edge: " + edge
                    + " ( wayId=" + readerWayId + ") " + edgeDistance + " vs. " + geometryDistance
                    + ", difference: " + (edgeDistance - geometryDistance) + ", geometry: " + pointList)
    }

    /**
     * This method is called for each way during the second pass and before the way is split into edges.
     * We currently use it to parse road names and calculate the distance of a way to determine the speed based on
     * the duration tag when it is present. The latter cannot be done on a per-edge basis, because the duration tag
     * refers to the duration of the entire way.
     */
    protected open fun preprocessWay(way: ReaderWay, coordinateSupplier: WaySegmentParser.CoordinateSupplier,
                                     nodeTagSupplier: WaySegmentParser.NodeTagSupplier) {
        val map = LinkedHashMap<String, KValue>()
        if (config.isParseWayNames()) {
            // http://wiki.openstreetmap.org/wiki/Key:name
            var name = ""
            if (!config.getPreferredLanguage().isEmpty())
                name = fixWayName(way.getTag("name:" + config.getPreferredLanguage()))
            if (name.isEmpty())
                name = fixWayName(way.getTag("name"))
            if (name.isEmpty())
                name = fixWayName(way.getTag("is_sidepath:of:name"))
            if (name.isEmpty())
                name = fixWayName(way.getTag("street:name"))
            if (!name.isEmpty())
                map.put(STREET_NAME, KValue(name))

            // http://wiki.openstreetmap.org/wiki/Key:ref
            val refName = fixWayName(way.getTag("ref"))
            if (!refName.isEmpty())
                map.put(STREET_REF, KValue(refName))

            if (way.hasTag("destination:ref")) {
                map.put(STREET_DESTINATION_REF, KValue(fixWayName(way.getTag("destination:ref"))))
            } else {
                val fwdStr = fixWayName(way.getTag("destination:ref:forward"))
                val bwdStr = fixWayName(way.getTag("destination:ref:backward"))
                if (!fwdStr.isEmpty() || !bwdStr.isEmpty())
                    map.put(STREET_DESTINATION_REF, KValue(if (fwdStr.isEmpty()) null else fwdStr, if (bwdStr.isEmpty()) null else bwdStr))
            }
            if (way.hasTag("destination")) {
                map.put(STREET_DESTINATION, KValue(fixWayName(way.getTag("destination"))))
            } else {
                val fwdStr = fixWayName(way.getTag("destination:forward"))
                val bwdStr = fixWayName(way.getTag("destination:backward"))
                if (!fwdStr.isEmpty() || !bwdStr.isEmpty())
                    map.put(STREET_DESTINATION, KValue(if (fwdStr.isEmpty()) null else fwdStr, if (bwdStr.isEmpty()) null else bwdStr))
            }

            // copy node name of motorway_junction
            val nodes = way.nodes
            if (!nodes.isEmpty && (way.hasTag("highway", "motorway") || way.hasTag("highway", "motorway_link"))) {
                // index 0 assumes oneway=yes
                val nodeTags = nodeTagSupplier.getTags(nodes.get(0))
                val nodeName = nodeTags.getOrDefault("name", "") as String
                if (!nodeName.isEmpty() && "motorway_junction" == nodeTags.getOrDefault("highway", ""))
                    map.put(MOTORWAY_JUNCTION, KValue(nodeName))
            }
        }

        if (way.getTags().size > 1) // at least highway tag
            for ((entryKey, entryValue) in way.getTags()) {
                if (entryKey.endsWith(":conditional") && entryValue is String &&
                    // for now reduce index size a bit and focus on access tags
                    !entryKey.startsWith("maxspeed") && !entryKey.startsWith("maxweight")) {
                    // remove spaces as they unnecessarily increase the unique number of values:
                    val value = KVStorage.cutString(entryValue
                        .replace(" ", "").replace("bicycle", "bike"))
                    val key = entryKey.replace(':', '_').replace("bicycle", "bike")
                    val fwd = key.contains("forward")
                    val bwd = key.contains("backward")
                    if (!value.isEmpty()) {
                        if (fwd == bwd)
                            map.put(key, KValue(value))
                        else
                            map.put(key, KValue(if (fwd) value else null, if (bwd) value else null))
                    }
                }
            }

        way.setTag("key_values", map)

        if (!isCalculateWayDistance(way))
            return

        val distance = calc2DDistance(way, coordinateSupplier)
        if (distance.isNaN()) {
            // Some nodes were missing, and we cannot determine the distance. This can happen when ways are only
            // included partially in an OSM extract. In this case we cannot calculate the speed either, so we return.
            LOGGER.warn("Could not determine distance for OSM way: " + way.id)
            return
        }
        way.setTag("way_distance_2d", distance)

        // For ways with a duration tag we determine the average speed. This is needed for e.g. ferry routes, because
        // the duration tag is only valid for the entire way, and it would be wrong to use it after splitting the way
        // into edges.
        val durationTag = way.getTag("duration")
        if (durationTag == null) {
            // no duration tag -> we cannot derive speed. happens very frequently for short ferries, but also for some long ones, see: #2532
            if (isFerry(way) && distance > 500_000)
                OSM_WARNING_LOGGER.warn("Long ferry OSM way without duration tag: " + way.id + ", distance: " + Math.round(distance / 1000.0) + " km")
            return
        }
        val durationInSeconds: Long
        try {
            durationInSeconds = OSMReaderUtility.parseDuration(durationTag)
        } catch (e: Exception) {
            OSM_WARNING_LOGGER.warn("Could not parse duration tag '" + durationTag + "' in OSM way: " + way.id)
            return
        }

        if (distance / 1000 / (durationInSeconds / 60.0 / 60.0) < 0.1) {
            // Often there are mapping errors like duration=30:00 (30h) instead of duration=00:30 (30min). In this case we
            // ignore the duration tag. If no such cases show up anymore, because they were fixed, maybe raise the limit to find some more.
            OSM_WARNING_LOGGER.warn("Unrealistic low speed calculated from duration. Maybe the duration is too long, or it is applied to a way that only represents a part of the connection? OSM way: "
                    + way.id + ". duration=" + durationTag + " (= " + Math.round(durationInSeconds / 60.0) +
                    " minutes), distance=" + distance + " m")
            return
        }
        // tag will be present if 1) isCalculateWayDistance was true for this way, 2) no OSM nodes were missing
        // such that the distance could actually be calculated, 3) there was a duration tag we could parse, and 4) the
        // derived speed was not unrealistically slow.
        way.setTag("duration_in_seconds", durationInSeconds)
    }

    /**
     * This method is called for each relation during the first pass of [WaySegmentParser]
     */
    protected open fun preprocessRelations(relation: ReaderRelation) {
        if (relation.hasTag("type", "route")) {
            // we keep track of all route relations, so they are available when we create edges later
            for (member in relation.getMembers()) {
                if (member.type != ReaderElement.Type.WAY)
                    continue
                val oldRelationFlags = getRelFlagsMap(member.ref)
                val newRelationFlags = osmParsers.handleRelationTags(relation, oldRelationFlags)
                putRelFlagsMap(member.ref, newRelationFlags)
            }
        }

        for (wayId in OSMRestrictionConverter.getRestrictedWayIds(relation))
            restrictedWaysToEdgesMap!!.reserve(wayId)
    }

    /**
     * This method is called for each relation during the second pass of [WaySegmentParser]
     * We use it to save the relations and process them afterwards.
     */
    protected open fun processRelation(relation: ReaderRelation, getIdForOSMNodeId: java.util.function.LongToIntFunction) {
        if (turnCostStorage != null)
            if (OSMRestrictionConverter.isTurnRestriction(relation)) {
                val osmViaNode = OSMRestrictionConverter.getViaNodeIfViaNodeRestriction(relation)
                if (osmViaNode >= 0) {
                    val viaNode = getIdForOSMNodeId.applyAsInt(osmViaNode)
                    // only include the restriction if the corresponding node wasn't excluded
                    if (viaNode >= 0) {
                        relation.setTag("graphhopper:via_node", viaNode)
                        restrictionRelations!!.add(relation)
                    }
                } else
                    // not a via-node restriction -> simply add it as is
                    restrictionRelations!!.add(relation)
            }
    }

    private fun addRestrictionsToGraph() {
        if (turnCostStorage == null)
            return
        val sw = StopWatch.started()
        // The OSM restriction format is explained here: https://wiki.openstreetmap.org/wiki/Relation:restriction
        val restrictionRelations = this.restrictionRelations!!
        val restrictionRelationsWithTopology = ArrayList<Triple<ReaderRelation, RestrictionTopology, RestrictionMembers>>(restrictionRelations.size)
        for (restrictionRelation in restrictionRelations) {
            try {
                // Build the topology of the OSM relation in the graph representation. This only needs to be done once for all
                // vehicle types (we also want to print warnings only once)
                restrictionRelationsWithTopology.add(OSMRestrictionConverter.buildRestrictionTopologyForGraph(baseGraph, restrictionRelation) { restrictedWaysToEdgesMap!!.getEdges(it) })
            } catch (e: OSMRestrictionException) {
                warnOfRestriction(restrictionRelation, e)
            }
        }
        // It is important to set the restrictions for all parsers/encoded values at once to make
        // sure the resulting turn restrictions do not interfere.
        val restrictions = ArrayList<RestrictionSetter.Restriction>()
        // For every restriction we set flags that indicate the validity for the different parsers
        val encBits = ArrayList<BitSet>()
        for (r in restrictionRelationsWithTopology) {
            try {
                val bits = BitSet(osmParsers.restrictionTagParsers.size.toLong())
                var restrictionType: RestrictionType? = null
                for (i in 0 until osmParsers.restrictionTagParsers.size) {
                    val restrictionTagParser = osmParsers.restrictionTagParsers[i]
                    val res = restrictionTagParser.parseRestrictionTags(r.first.getTags())
                        ?: // this relation is ignored by this restriction tag parser
                        continue
                    OSMRestrictionConverter.checkIfTopologyIsCompatibleWithRestriction(r.second, res.restriction)
                    if (restrictionType != null && res.restrictionType != restrictionType)
                        // so far we restrict ourselves to restriction relations that use the same type for all vehicles
                        throw OSMRestrictionException("has different restriction type for different vehicles.")
                    restrictionType = res.restrictionType
                    bits.set(i.toLong())
                }
                if (bits.cardinality() > 0) {
                    val tmpRestrictions = OSMRestrictionConverter.buildRestrictionsForOSMRestriction(baseGraph, r.second, restrictionType)
                    restrictions.addAll(tmpRestrictions)
                    tmpRestrictions.forEach { encBits.add(RestrictionSetter.copyEncBits(bits)) }
                }
            } catch (e: OSMRestrictionException) {
                warnOfRestriction(r.first, e)
            }
        }
        restrictionSetter.setRestrictions(restrictions, encBits)
        LOGGER.info("Finished adding turn restrictions. total turn cost entries: {}, took: {}",
            Helper.nf(baseGraph.turnCostStorage!!.turnCostsCount.toLong()), sw.stop().getTimeString())
    }

    private fun releaseEverythingExceptRestrictionData() {
        eleProvider.release()
        osmWayIdToRelationFlagsMap = null
    }

    private fun releaseRestrictionData() {
        restrictedWaysToEdgesMap = null
        restrictionRelations = null
    }

    private fun getRelFlagsMap(osmId: Long): IntsRef {
        val relFlagsAsLong = osmWayIdToRelationFlagsMap!!.get(osmId)
        tempRelFlags.ints[0] = relFlagsAsLong.toInt()
        tempRelFlags.ints[1] = (relFlagsAsLong shr 32).toInt()
        return tempRelFlags
    }

    private fun putRelFlagsMap(osmId: Long, relFlags: IntsRef) {
        val relFlagsAsLong = (relFlags.ints[1].toLong() shl 32) or (relFlags.ints[0].toLong() and 0xFFFFFFFFL)
        osmWayIdToRelationFlagsMap!!.put(osmId, relFlagsAsLong)
    }

    override fun toString(): String = javaClass.simpleName

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(OSMReader::class.java)

        private val WAY_NAME_PATTERN = Pattern.compile("; *")

        @JvmStatic
        @JvmName("fixWayName")
        internal fun fixWayName(str: String?): String {
            if (str == null)
                return ""
            // the KVStorage does not accept too long strings -> Helper.cutStringForKV
            return KVStorage.cutString(WAY_NAME_PATTERN.matcher(str).replaceAll(", "))
        }

        /**
         * @return the 2D distance of the given way or NaN if some nodes were missing
         */
        @JvmStatic
        @JvmName("calc2DDistance")
        internal fun calc2DDistance(way: ReaderWay, coordinateSupplier: WaySegmentParser.CoordinateSupplier): Double {
            val nodes = way.nodes
            // every way has at least two nodes according to our acceptWay function
            var prevPoint: GHPoint3D = coordinateSupplier.getCoordinate(nodes.get(0))
                ?: return Double.NaN
            // Use 2D distance: pillar node elevation is not yet available during preprocessing
            var distance = 0.0
            for (i in 1 until nodes.size()) {
                val point = coordinateSupplier.getCoordinate(nodes.get(i))
                    ?: return Double.NaN
                distance += DistanceCalcEarth.DIST_EARTH.calcDist(prevPoint.lat, prevPoint.lon, point.lat, point.lon)
                prevPoint = point
            }
            return distance
        }

        private fun warnOfRestriction(restrictionRelation: ReaderRelation, e: OSMRestrictionException) {
            // we do not log exceptions with an empty message
            if (!e.isWithoutWarning) {
                restrictionRelation.getTags().remove("graphhopper:via_node")
                val members = restrictionRelation.getMembers().map { m -> "${m.role} ${m.type.toString().lowercase()} ${m.ref}" }
                OSM_WARNING_LOGGER.warn("Restriction relation " + restrictionRelation.id + " " + e.message + ". tags: " + restrictionRelation.getTags() + ", members: " + members + ". Relation ignored.")
            }
        }
    }
}
