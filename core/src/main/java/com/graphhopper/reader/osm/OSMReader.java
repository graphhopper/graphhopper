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
package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntLongMap;
import com.graphhopper.coll.GHIntLongHashMap;
import com.graphhopper.coll.GHLongHashSet;
import com.graphhopper.coll.GHLongLongHashMap;
import com.graphhopper.reader.*;
import com.graphhopper.reader.dem.EdgeSampling;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.GraphElevationSmoothing;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.TurnCostParser;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.LongToIntFunction;

import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyList;

/**
 * Parses an OSM file (xml, zipped xml or pbf) and creates a graph from it. The OSM file is actually read twice.
 * During the first scan we determine the 'type' of each node, i.e. we check whether a node only appears in a single way
 * or represents an intersection of multiple ways, or connects two ways. We also scan the relations and store them for
 * each way ID in memory.
 * During the second scan we store the coordinates of the nodes that belong to ways in memory and then split each way
 * into several segments that are divided by intersections or barrier nodes. Each segment is added as an edge of the
 * resulting graph. Afterwards we scan the relations again to determine turn restrictions.
 **/
public class OSMReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMReader.class);

    private final OSMReaderConfig config;
    private final GraphHopperStorage ghStorage;
    private final NodeAccess nodeAccess;
    private final TurnCostStorage turnCostStorage;
    private final EncodingManager encodingManager;
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private AreaIndex<CustomArea> areaIndex;
    private CountryRuleFactory countryRuleFactory = null;
    private File osmFile;
    private final DouglasPeucker simplifyAlgo = new DouglasPeucker();

    private final IntsRef tempRelFlags;
    private Date osmDataDate;
    private long zeroCounter = 0;

    private GHLongLongHashMap osmWayIdToRelationFlagsMap = new GHLongLongHashMap(200, .5f);
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    private GHLongHashSet osmWayIdSet = new GHLongHashSet();
    private IntLongMap edgeIdToOsmWayIdMap;

    public OSMReader(GraphHopperStorage ghStorage, OSMReaderConfig config) {
        this.ghStorage = ghStorage;
        this.config = config;
        this.nodeAccess = ghStorage.getNodeAccess();
        this.encodingManager = ghStorage.getEncodingManager();

        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance());
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance());
        turnCostStorage = ghStorage.getTurnCostStorage();

        tempRelFlags = encodingManager.createRelationFlags();
        if (tempRelFlags.length != 2)
            throw new IllegalArgumentException("Cannot use relation flags with != 2 integers");
    }

    /**
     * Sets the OSM file to be read.  Supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    public OSMReader setFile(File osmFile) {
        this.osmFile = osmFile;
        return this;
    }

    /**
     * The area index is queried for each OSM way and the associated areas are added to the way's tags
     */
    public OSMReader setAreaIndex(AreaIndex<CustomArea> areaIndex) {
        this.areaIndex = areaIndex;
        return this;
    }

    public OSMReader setElevationProvider(ElevationProvider eleProvider) {
        if (eleProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.eleProvider = eleProvider;
        return this;
    }

    public OSMReader setCountryRuleFactory(CountryRuleFactory countryRuleFactory) {
        this.countryRuleFactory = countryRuleFactory;
        return this;
    }

    public void readGraph() throws IOException {
        if (encodingManager == null)
            throw new IllegalStateException("Encoding manager was not set.");

        if (osmFile == null)
            throw new IllegalStateException("No OSM file specified");

        if (!osmFile.exists())
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmFile.getAbsolutePath());

        WaySegmentParser waySegmentParser = new WaySegmentParser.Builder(ghStorage.getNodeAccess())
                .setDirectory(ghStorage.getDirectory())
                .setElevationProvider(eleProvider)
                .setWayFilter(this::acceptWay)
                .setSplitNodeFilter(this::isBarrierNode)
                .setRelationPreprocessor(this::preprocessRelations)
                .setRelationProcessor(this::processRelation)
                .setEdgeHandler(this::addEdge)
                .setWorkerThreads(config.getWorkerThreads())
                .build();
        ghStorage.create(100);
        waySegmentParser.readOSM(osmFile);
        osmDataDate = waySegmentParser.getTimeStamp();
        if (ghStorage.getNodes() == 0)
            throw new RuntimeException("Graph after reading OSM must not be empty");
        LOGGER.info("Finished reading OSM file: {}, nodes: {}, edges: {}, zero distance edges: {}",
                osmFile.getAbsolutePath(), nf(ghStorage.getNodes()), nf(ghStorage.getEdges()), nf(zeroCounter));
        finishedReading();
    }

    /**
     * @return the timestamp given in the OSM file header or null if not found
     */
    public Date getDataDate() {
        return osmDataDate;
    }

    /**
     * This method is called for each way during the first and second pass of the {@link WaySegmentParser}. All OSM
     * ways that are not accepted here and all nodes that are not referenced by any such way will be ignored.
     */
    protected boolean acceptWay(ReaderWay item) {
        // ignore broken geometry
        if (item.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!item.hasTags())
            return false;

        return encodingManager.acceptWay(item, new EncodingManager.AcceptWay());
    }

    /**
     * @return true if the given node should be duplicated to create an artificial edge. If the node turns out to be a
     * junction between different ways this will be ignored and no artificial edge will be created.
     */
    protected boolean isBarrierNode(ReaderNode node) {
        return node.getTags().containsKey("barrier");
    }

    /**
     * This method is called during the second pass of {@link WaySegmentParser} and provides an entry point to enrich
     * the given OSM way with additional tags before it is passed on to the tag parsers.
     */
    protected void setArtificialWayTags(PointList pointList, ReaderWay way) {
        // Estimate length of ways containing a route tag e.g. for ferry speed calculation
        double firstLat = pointList.getLat(0), firstLon = pointList.getLon(0);
        double lastLat = pointList.getLat(pointList.size() - 1), lastLon = pointList.getLon(pointList.size() - 1);
        // we have to remove existing artificial tags, because we modify the way even though there can be multiple edges
        // per way. sooner or later we should separate the artificial ('edge') tags from the way, see discussion here:
        // https://github.com/graphhopper/graphhopper/pull/2457#discussion_r751155404
        way.removeTag("estimated_distance");
        double estimatedDist = distCalc.calcDist(firstLat, firstLon, lastLat, lastLon);
        way.setTag("estimated_distance", estimatedDist);

        way.removeTag("duration:seconds");
        if (way.getTag("duration") != null) {
            try {
                long dur = OSMReaderUtility.parseDuration(way.getTag("duration"));
                // Provide the duration value in seconds in an artificial graphhopper specific tag:
                way.setTag("duration:seconds", Long.toString(dur));
            } catch (Exception ex) {
                LOGGER.warn("Parsing error in way with OSMID=" + way.getId() + " : " + ex.getMessage());
            }
        }

        way.removeTag("country");
        way.removeTag("country_rule");
        way.removeTag("custom_areas");

        List<CustomArea> customAreas = areaIndex == null
                ? emptyList()
                : areaIndex.query(pointList.getLat(pointList.size() / 2), pointList.getLon(pointList.size() / 2));

        // special handling for countries: since they are built-in with GraphHopper they are always fed to the EncodingManager
        Country country = Country.MISSING;
        for (CustomArea customArea : customAreas) {
            Object countryCode = customArea.getProperties().get("ISO3166-1:alpha3");
            if (countryCode == null)
                continue;
            if (country != Country.MISSING)
                LOGGER.warn("Multiple countries found for way {}: {}, {}", way.getId(), country, countryCode);
            country = Country.valueOf(countryCode.toString());
        }
        way.setTag("country", country);

        if (countryRuleFactory != null) {
            CountryRule countryRule = countryRuleFactory.getCountryRule(country);
            if (countryRule != null)
                way.setTag("country_rule", countryRule);
        }

        // also add all custom areas as artificial tag
        way.setTag("custom_areas", customAreas);
    }

    /**
     * This method is called for each segment an OSM way is split into during the second pass of {@link WaySegmentParser}.
     *
     * @param fromIndex a unique integer id for the first node of this segment
     * @param toIndex   a unique integer id for the last node of this segment
     * @param pointList coordinates of this segment
     * @param way       the OSM way this segment was taken from
     * @param nodeTags  node tags of this segment if it is an artificial edge, empty otherwise
     */
    protected void addEdge(int fromIndex, int toIndex, PointList pointList, ReaderWay way, Map<String, Object> nodeTags) {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

        // todo: in principle it should be possible to delay elevation calculation so we do not need to store
        // elevations during import (saves memory in pillar info during import). also note that we already need to
        // to do some kind of elevation processing (bridge+tunnel interpolation in GraphHopper class, maybe this can
        // go together

        // Smooth the elevation before calculating the distance because the distance will be incorrect if calculated afterwards
        if (config.isSmoothElevation())
            GraphElevationSmoothing.smoothElevation(pointList);

        // sample points along long edges
        if (config.getLongEdgeSamplingDistance() < Double.MAX_VALUE && pointList.is3D())
            pointList = EdgeSampling.sample(pointList, config.getLongEdgeSamplingDistance(), distCalc, eleProvider);

        if (config.getMaxWayPointDistance() > 0 && pointList.size() > 2)
            simplifyAlgo.simplify(pointList);

        double towerNodeDistance = distCalc.calcDistance(pointList);

        if (towerNodeDistance < 0.001) {
            // As investigation shows often two paths should have crossed via one identical point
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.001;
        }

        double maxDistance = (Integer.MAX_VALUE - 1) / 1000d;
        if (Double.isNaN(towerNodeDistance)) {
            LOGGER.warn("Bug in OSM or GraphHopper. Illegal tower node distance " + towerNodeDistance + " reset to 1m, osm way " + way.getId());
            towerNodeDistance = 1;
        }

        if (Double.isInfinite(towerNodeDistance) || towerNodeDistance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            LOGGER.warn("Bug in OSM or GraphHopper. Too big tower node distance " + towerNodeDistance + " reset to large value, osm way " + way.getId());
            towerNodeDistance = maxDistance;
        }

        setArtificialWayTags(pointList, way);
        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        if (!encodingManager.acceptWay(way, acceptWay))
            throw new IllegalStateException("unaccepted way: " + way.getId());

        IntsRef relationFlags = getRelFlagsMap(way.getId());
        IntsRef edgeFlags = encodingManager.handleWayTags(way, acceptWay, relationFlags);
        if (edgeFlags.isEmpty())
            return;

        // update edge flags to potentially block access in case there are node tags
        if (!nodeTags.isEmpty())
            edgeFlags = encodingManager.handleNodeTags(nodeTags, IntsRef.deepCopyOf(edgeFlags));

        EdgeIteratorState iter = ghStorage.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(edgeFlags);

        // If the entire way is just the first and last point, do not waste space storing an empty way geometry
        if (pointList.size() > 2) {
            // the geometry consists only of pillar nodes, but we check that the first and last points of the pointList
            // are equal to the tower node coordinates
            checkCoordinates(fromIndex, pointList.get(0));
            checkCoordinates(toIndex, pointList.get(pointList.size() - 1));
            iter.setWayGeometry(pointList.shallowCopy(1, pointList.size() - 1, false));
        }
        encodingManager.applyWayTags(way, iter);

        checkDistance(iter);
        if (osmWayIdSet.contains(way.getId())) {
            getEdgeIdToOsmWayIdMap().put(iter.getEdge(), way.getId());
        }
    }

    private void checkCoordinates(int nodeIndex, GHPoint point) {
        final double tolerance = 1.e-6;
        if (Math.abs(nodeAccess.getLat(nodeIndex) - point.getLat()) > tolerance || Math.abs(nodeAccess.getLon(nodeIndex) - point.getLon()) > tolerance)
            throw new IllegalStateException("Suspicious coordinates for node " + nodeIndex + ": (" + nodeAccess.getLat(nodeIndex) + "," + nodeAccess.getLon(nodeIndex) + ") vs. (" + point + ")");
    }

    private void checkDistance(EdgeIteratorState edge) {
        final double tolerance = 1;
        final double edgeDistance = edge.getDistance();
        final double geometryDistance = distCalc.calcDistance(edge.fetchWayGeometry(FetchMode.ALL));
        if (edgeDistance > 2_000_000)
            LOGGER.warn("Very long edge detected: " + edge + " dist: " + edgeDistance);
        else if (Math.abs(edgeDistance - geometryDistance) > tolerance)
            throw new IllegalStateException("Suspicious distance for edge: " + edge + " " + edgeDistance + " vs. " + geometryDistance
                    + ", difference: " + (edgeDistance - geometryDistance));
    }

    /**
     * This method is called for each relation during the first pass of {@link WaySegmentParser}
     */
    protected void preprocessRelations(ReaderRelation relation) {
        if (!relation.isMetaRelation() && relation.hasTag("type", "route")) {
            // we keep track of all route relations, so they are available when we create edges later
            for (ReaderRelation.Member member : relation.getMembers()) {
                if (member.getType() != ReaderRelation.Member.WAY)
                    continue;
                IntsRef oldRelationFlags = getRelFlagsMap(member.getRef());
                IntsRef newRelationFlags = encodingManager.handleRelationTags(relation, oldRelationFlags);
                putRelFlagsMap(member.getRef(), newRelationFlags);
            }
        }

        if (relation.hasTag("type", "restriction")) {
            // we keep the osm way ids that occur in turn relations, because this way we know for which GH edges
            // we need to remember the associated osm way id. this is just an optimization that is supposed to save
            // memory compared to simply storing the osm way ids in a long array where the array index is the GH edge
            // id.
            List<OSMTurnRelation> turnRelations = createTurnRelations(relation);
            for (OSMTurnRelation turnRelation : turnRelations) {
                osmWayIdSet.add(turnRelation.getOsmIdFrom());
                osmWayIdSet.add(turnRelation.getOsmIdTo());
            }
        }
    }

    /**
     * This method is called for each relation during the second pass of {@link WaySegmentParser}
     * We use it to set turn restrictions.
     */
    protected void processRelation(ReaderRelation relation, LongToIntFunction getIdForOSMNodeId) {
        if (turnCostStorage != null && relation.hasTag("type", "restriction")) {
            TurnCostParser.ExternalInternalMap map = new TurnCostParser.ExternalInternalMap() {
                @Override
                public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
                    return getIdForOSMNodeId.applyAsInt(nodeOsmId);
                }

                @Override
                public long getOsmIdOfInternalEdge(int edgeId) {
                    return getEdgeIdToOsmWayIdMap().get(edgeId);
                }
            };
            for (OSMTurnRelation turnRelation : createTurnRelations(relation)) {
                int viaNode = map.getInternalNodeIdOfOsmNode(turnRelation.getViaOsmNodeId());
                // street with restriction was not included (access or tag limits etc)
                if (viaNode >= 0)
                    encodingManager.handleTurnRelationTags(turnRelation, map, ghStorage);
            }
        }
    }

    private IntLongMap getEdgeIdToOsmWayIdMap() {
        // todo: is this lazy initialization really advantageous?
        if (edgeIdToOsmWayIdMap == null)
            edgeIdToOsmWayIdMap = new GHIntLongHashMap(osmWayIdSet.size(), 0.5f);

        return edgeIdToOsmWayIdMap;
    }

    /**
     * Creates turn relations out of an unspecified OSM relation
     */
    static List<OSMTurnRelation> createTurnRelations(ReaderRelation relation) {
        List<OSMTurnRelation> osmTurnRelations = new ArrayList<>();
        String vehicleTypeRestricted = "";
        List<String> vehicleTypesExcept = new ArrayList<>();
        if (relation.hasTag("except")) {
            String tagExcept = relation.getTag("except");
            if (!Helper.isEmpty(tagExcept)) {
                List<String> vehicleTypes = new ArrayList<>(Arrays.asList(tagExcept.split(";")));
                for (String vehicleType : vehicleTypes)
                    vehicleTypesExcept.add(vehicleType.trim());
            }
        }
        if (relation.hasTag("restriction")) {
            OSMTurnRelation osmTurnRelation = createTurnRelation(relation, relation.getTag("restriction"), vehicleTypeRestricted, vehicleTypesExcept);
            if (osmTurnRelation != null) {
                osmTurnRelations.add(osmTurnRelation);
            }
            return osmTurnRelations;
        }
        if (relation.hasTagWithKeyPrefix("restriction:")) {
            List<String> vehicleTypesRestricted = relation.getKeysWithPrefix("restriction:");
            for (String vehicleType : vehicleTypesRestricted) {
                String restrictionType = relation.getTag(vehicleType);
                vehicleTypeRestricted = vehicleType.replace("restriction:", "").trim();
                OSMTurnRelation osmTurnRelation = createTurnRelation(relation, restrictionType, vehicleTypeRestricted, vehicleTypesExcept);
                if (osmTurnRelation != null) {
                    osmTurnRelations.add(osmTurnRelation);
                }
            }
        }
        return osmTurnRelations;
    }

    static OSMTurnRelation createTurnRelation(ReaderRelation relation, String restrictionType, String
            vehicleTypeRestricted, List<String> vehicleTypesExcept) {
        OSMTurnRelation.Type type = OSMTurnRelation.Type.getRestrictionType(restrictionType);
        if (type != OSMTurnRelation.Type.UNSUPPORTED) {
            long fromWayID = -1;
            long viaNodeID = -1;
            long toWayID = -1;

            for (ReaderRelation.Member member : relation.getMembers()) {
                if (ReaderElement.WAY == member.getType()) {
                    if ("from".equals(member.getRole())) {
                        fromWayID = member.getRef();
                    } else if ("to".equals(member.getRole())) {
                        toWayID = member.getRef();
                    }
                } else if (ReaderElement.NODE == member.getType() && "via".equals(member.getRole())) {
                    viaNodeID = member.getRef();
                }
            }
            if (fromWayID >= 0 && toWayID >= 0 && viaNodeID >= 0) {
                OSMTurnRelation osmTurnRelation = new OSMTurnRelation(fromWayID, viaNodeID, toWayID, type);
                osmTurnRelation.setVehicleTypeRestricted(vehicleTypeRestricted);
                osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
                return osmTurnRelation;
            }
        }
        return null;
    }

    private void finishedReading() {
        encodingManager.releaseParsers();
        eleProvider.release();
        osmWayIdToRelationFlagsMap = null;
        osmWayIdSet = null;
        edgeIdToOsmWayIdMap = null;
    }

    IntsRef getRelFlagsMap(long osmId) {
        long relFlagsAsLong = osmWayIdToRelationFlagsMap.get(osmId);
        tempRelFlags.ints[0] = (int) relFlagsAsLong;
        tempRelFlags.ints[1] = (int) (relFlagsAsLong >> 32);
        return tempRelFlags;
    }

    void putRelFlagsMap(long osmId, IntsRef relFlags) {
        long relFlagsAsLong = ((long) relFlags.ints[1] << 32) | (relFlags.ints[0] & 0xFFFFFFFFL);
        osmWayIdToRelationFlagsMap.put(osmId, relFlagsAsLong);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
