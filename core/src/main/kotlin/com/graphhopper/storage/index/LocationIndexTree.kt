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
package com.graphhopper.storage.index

import com.carrotsearch.hppc.IntHashSet
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.storage.Directory
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.DistancePlaneProjection.Companion.DIST_PLANE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.Helper
import com.graphhopper.util.StopWatch
import com.graphhopper.util.shapes.BBox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

/**
 * This class implements a Quadtree to get the closest node or edge from GPS coordinates.
 * The following properties are different to an ordinary implementation:
 * <ol>
 * <li>To reduce overall size it can use 16 instead of just 4 cell if required</li>
 * <li>Still all leafs are at the same depth, otherwise it is too complicated to calculate the Bresenham line for different
 * resolutions, especially if a leaf node could be split into a tree-node and resolution changes.</li>
 * <li>To further reduce size this Quadtree avoids storing the bounding box of every cell and calculates this per request instead.</li>
 * <li>To simplify this querying and avoid a slow down for the most frequent queries ala "lat,lon" it encodes the point
 * into a spatial key {@see SpatialKeyAlgo} and can the use the resulting raw bits as cell index to recurse
 * into the subtrees. E.g. if there are 3 layers with 16, 4 and 4 cells each, then the spatial key has
 * three parts: 4 bits for the cellIndex into the 16 cells, 2 bits for the next layer and 2 bits for the last layer.</li>
 * <li>An array structure (DataAccess) is internally used and stores the offset to the next cell.
 * E.g. in case of 4 cells, the offset is 0,1,2 or 3. Except when the leaf-depth is reached, then the value
 * is the number of node IDs stored in the cell or, if negative, just a single node ID.</li>
 * </ol>
 *
 * @author Peter Karich
 */
class LocationIndexTree(g: Graph, dir: Directory) : LocationIndex {
    private val directory: Directory = dir
    private val graph: Graph = g
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val nodeAccess: NodeAccess = g.nodeAccess
    private var maxRegionSearch = 4
    private var minResolutionInMeter = 300
    private var initialized = false

    private val lineIntIndex: LineIntIndex

    /**
     * If normed distance is smaller than this value the node or edge is 'identical' and the
     * algorithm can stop search.
     */
    private val equalNormedDelta: Double = DIST_PLANE.calcNormalizedDist(0.1) // 0.1 meters
    private lateinit var indexStructureInfo: IndexStructureInfo

    /**
     * @param g the graph for which this index should do the lookup based on latitude,longitude.
     */
    init {
        // Clone this defensively -- In case something funny happens and things get added to the Graph after
        // this index is built. Reason is that the expected structure of the index is a function of the bbox, so we
        // need it to be immutable.
        var bounds = graph.bounds.clone()

        // I want to be able to create a location index for the empty graph without error, but for that
        // I need valid bounds so that the initialization logic works.
        if (!bounds.isValid())
            bounds = BBox(-10.0, 10.0, -10.0, 10.0)

        lineIntIndex = LineIntIndex(bounds, directory, "location_index")
    }

    fun getMinResolutionInMeter(): Int = minResolutionInMeter

    /**
     * Minimum width in meter of one tile. Decrease this if you need faster queries, but keep in
     * mind that then queries with different coordinates are more likely to fail.
     */
    fun setMinResolutionInMeter(minResolutionInMeter: Int): LocationIndexTree {
        this.minResolutionInMeter = minResolutionInMeter
        return this
    }

    /**
     * Searches also neighbouring tiles until the maximum distance from the query point is reached
     * (minResolutionInMeter*regionAround). Set to 1 to only search one tile. Good if you
     * have strict performance requirements and want the search to terminate early, and you can tolerate
     * that edges that may be in neighboring tiles are not found. Default is 4, which means approximately
     * that a square of three tiles upwards, downwards, leftwards and rightwards from the tile the query tile
     * is in is searched.
     */
    fun setMaxRegionSearch(numTiles: Int): LocationIndexTree {
        if (numTiles < 1)
            throw IllegalArgumentException("Region of location index must be at least 1 but was $numTiles")
        this.maxRegionSearch = numTiles
        return this
    }

    fun setResolution(minResolutionInMeter: Int): LocationIndex {
        if (minResolutionInMeter <= 0)
            throw IllegalStateException("Negative precision is not allowed!")

        setMinResolutionInMeter(minResolutionInMeter)
        return this
    }

    fun loadExisting(): Boolean {
        if (!lineIntIndex.loadExisting())
            return false

        if (lineIntIndex.checksum != checksum())
            throw IllegalStateException("location index was opened with incorrect graph: "
                    + lineIntIndex.checksum + " vs. " + checksum())
        minResolutionInMeter = lineIntIndex.minResolutionInMeter
        indexStructureInfo = IndexStructureInfo.create(graph.bounds, minResolutionInMeter)
        initialized = true
        return true
    }

    fun flush() {
        lineIntIndex.flush()
    }

    fun prepareIndex(): LocationIndex = prepareIndex(EdgeFilter.ALL_EDGES)

    fun prepareIndex(edgeFilter: EdgeFilter): LocationIndex {
        if (initialized)
            throw IllegalStateException("Call prepareIndex only once")

        val sw = StopWatch().start()

        // Clone this defensively -- In case something funny happens and things get added to the Graph after
        // this index is built. Reason is that the expected structure of the index is a function of the bbox, so we
        // need it to be immutable.
        var bounds = graph.bounds.clone()

        // I want to be able to create a location index for the empty graph without error, but for that
        // I need valid bounds so that the initialization logic works.
        if (!bounds.isValid())
            bounds = BBox(-10.0, 10.0, -10.0, 10.0)

        val inMemConstructionIndex = prepareInMemConstructionIndex(bounds, edgeFilter)

        lineIntIndex.minResolutionInMeter = minResolutionInMeter
        lineIntIndex.store(inMemConstructionIndex)
        lineIntIndex.checksum = checksum()
        flush()
        logger.info("location index created in " + sw.stop().getSeconds()
                + "s, size:" + Helper.nf(lineIntIndex.size.toLong())
                + ", leafs:" + Helper.nf(lineIntIndex.leafs.toLong())
                + ", precision:" + minResolutionInMeter
                + ", depth:" + indexStructureInfo.entries.size
                + ", checksum:" + checksum()
                + ", entries:" + indexStructureInfo.entries.contentToString()
                + ", entriesPerLeaf:" + lineIntIndex.size.toFloat() / lineIntIndex.leafs)

        return this
    }

    internal fun prepareInMemConstructionIndex(bounds: BBox, edgeFilter: EdgeFilter): InMemConstructionIndex {
        indexStructureInfo = IndexStructureInfo.create(bounds, minResolutionInMeter)
        val inMem = InMemConstructionIndex(indexStructureInfo)
        val allIter = graph.allEdges
        try {
            while (allIter.next()) {
                if (!edgeFilter.accept(allIter))
                    continue
                val edge = allIter.edge
                val nodeA = allIter.baseNode
                val nodeB = allIter.adjNode
                var lat1 = nodeAccess.getLat(nodeA)
                var lon1 = nodeAccess.getLon(nodeA)
                var lat2: Double
                var lon2: Double
                val points = allIter.fetchWayGeometry(FetchMode.PILLAR_ONLY)
                val len = points.size()
                for (i in 0 until len) {
                    lat2 = points.getLat(i)
                    lon2 = points.getLon(i)
                    inMem.addToAllTilesOnLine(edge, lat1, lon1, lat2, lon2)
                    lat1 = lat2
                    lon1 = lon2
                }
                lat2 = nodeAccess.getLat(nodeB)
                lon2 = nodeAccess.getLon(nodeB)
                inMem.addToAllTilesOnLine(edge, lat1, lon1, lat2, lon2)
            }
        } catch (ex1: Exception) {
            logger.error("Problem! base:" + allIter.baseNode + ", adj:" + allIter.adjNode
                    + ", edge:" + allIter.edge, ex1)
        }
        return inMem
    }

    private fun checksum(): Int = graph.nodes xor graph.allEdges.length()

    override fun close() {
        lineIntIndex.close()
    }

    val isClosed: Boolean
        get() = lineIntIndex.isClosed

    val capacity: Long
        get() = lineIntIndex.capacity

    /**
     * Calculates the distance to the nearest tile border, where the tile border is the rectangular
     * region with dimension 2*paddingTiles + 1 and where the center tile contains the given lat/lon
     * coordinate
     */
    @JvmName("calculateRMin")
    internal fun calculateRMin(lat: Double, lon: Double, paddingTiles: Int): Double {
        val x = indexStructureInfo.keyAlgo.x(lon)
        val y = indexStructureInfo.keyAlgo.y(lat)

        val minLat = graph.bounds.minLat + (y - paddingTiles) * indexStructureInfo.deltaLat
        val maxLat = graph.bounds.minLat + (y + paddingTiles + 1) * indexStructureInfo.deltaLat
        val minLon = graph.bounds.minLon + (x - paddingTiles) * indexStructureInfo.deltaLon
        val maxLon = graph.bounds.minLon + (x + paddingTiles + 1) * indexStructureInfo.deltaLon

        val dSouthernLat = lat - minLat
        val dNorthernLat = maxLat - lat
        val dWesternLon = lon - minLon
        val dEasternLon = maxLon - lon

        // convert degree deltas into a radius in meter
        val dMinLat: Double
        val dMinLon: Double
        if (dSouthernLat < dNorthernLat) {
            dMinLat = DIST_PLANE.calcDist(lat, lon, minLat, lon)
        } else {
            dMinLat = DIST_PLANE.calcDist(lat, lon, maxLat, lon)
        }

        if (dWesternLon < dEasternLon) {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, minLon)
        } else {
            dMinLon = DIST_PLANE.calcDist(lat, lon, lat, maxLon)
        }

        return min(dMinLat, dMinLon)
    }

    override fun findClosest(queryLat: Double, queryLon: Double, edgeFilter: EdgeFilter): Snap {
        if (isClosed)
            throw IllegalStateException("You need to create a new LocationIndex instance as it is already closed")

        val closestMatch = Snap(queryLat, queryLon)
        val seenEdges = IntHashSet()
        for (iteration in 0 until maxRegionSearch) {
            lineIntIndex.findEdgeIdsInNeighborhood(queryLat, queryLon, iteration) { edgeId ->
                val edgeIteratorState = graph.getEdgeIteratorStateForKey(edgeId * 2)
                if (seenEdges.add(edgeId) && edgeFilter.accept(edgeIteratorState)) { // TODO: or reverse?
                    traverseEdge(queryLat, queryLon, edgeIteratorState) { node, normedDist, wayIndex, pos ->
                        if (normedDist < closestMatch.queryDistance) {
                            closestMatch.queryDistance = normedDist
                            closestMatch.closestNode = node
                            closestMatch.closestEdge = edgeIteratorState.detach(false)
                            closestMatch.wayIndex = wayIndex
                            closestMatch.snappedPosition = pos
                        }
                    }
                }
            }
            if (closestMatch.isValid) {
                // Check if we can stop...
                val rMin = calculateRMin(queryLat, queryLon, iteration)
                val minDistance = DIST_PLANE.calcDenormalizedDist(closestMatch.queryDistance)
                if (minDistance < rMin) {
                    break // We can (approximately?) guarantee that no closer edges are anywhere else
                }
            }
        }

        if (closestMatch.isValid) {
            closestMatch.calcSnappedPoint(DIST_PLANE)
            closestMatch.queryDistance = DIST_PLANE.calcDist(closestMatch.getSnappedPoint().lat, closestMatch.getSnappedPoint().lon, queryLat, queryLon)
        }
        return closestMatch
    }

    override fun query(tileFilter: LocationIndex.TileFilter?, function: LocationIndex.Visitor) {
        lineIntIndex.query(tileFilter, function)
    }

    fun interface EdgeCheck {
        fun check(node: Int, normedDist: Double, wayIndex: Int, pos: Snap.Position)
    }

    fun traverseEdge(queryLat: Double, queryLon: Double, currEdge: EdgeIteratorState, edgeCheck: EdgeCheck) {
        val baseNode = currEdge.baseNode
        val baseLat = nodeAccess.getLat(baseNode)
        val baseLon = nodeAccess.getLon(baseNode)
        val baseDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, baseLat, baseLon)

        val adjNode = currEdge.adjNode
        val adjLat = nodeAccess.getLat(adjNode)
        val adjLon = nodeAccess.getLon(adjNode)
        val adjDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, adjLat, adjLon)

        val pointList = currEdge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ)
        val len = pointList.size()

        val closestTowerNode: Int
        var closestDist: Double
        if (baseDist < adjDist) {
            closestTowerNode = baseNode
            closestDist = baseDist
            edgeCheck.check(baseNode, baseDist, 0, Snap.Position.TOWER)
        } else {
            closestTowerNode = adjNode
            closestDist = adjDist
            edgeCheck.check(adjNode, adjDist, len, Snap.Position.TOWER)
        }
        if (closestDist <= equalNormedDelta)
            // if a tower node is close to the query point we stop
            return

        var lastLat = baseLat
        var lastLon = baseLon
        for (i in 0 until len) {
            val lat = pointList.getLat(i)
            val lon = pointList.getLon(i)
            if (DIST_PLANE.isCrossBoundary(lastLon, lon)) {
                lastLat = lat
                lastLon = lon
                continue
            }

            // +1 because we skipped the base node
            val indexInFullPointList = i + 1
            if (DIST_PLANE.validEdgeDistance(queryLat, queryLon, lastLat, lastLon, lat, lon)) {
                closestDist = DIST_PLANE.calcNormalizedEdgeDistance(queryLat, queryLon, lastLat, lastLon, lat, lon)
                edgeCheck.check(closestTowerNode, closestDist, indexInFullPointList - 1, Snap.Position.EDGE)
            } else if (i < len - 1) {
                closestDist = DIST_PLANE.calcNormalizedDist(queryLat, queryLon, lat, lon)
                edgeCheck.check(closestTowerNode, closestDist, indexInFullPointList, Snap.Position.PILLAR)
            } else {
                // we snapped onto the last tower node, but we already handled this before so do nothing
            }
            if (closestDist <= equalNormedDelta)
                return
            lastLat = lat
            lastLon = lon
        }
    }
}
