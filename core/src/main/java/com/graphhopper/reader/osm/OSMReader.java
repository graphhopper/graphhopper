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

import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongSet;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.coll.GHLongLongHashMap;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.EdgeElevationSmoothing;
import com.graphhopper.reader.dem.EdgeSampling;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.Landuse;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.RestrictionSetter;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.openjdk.jol.info.GraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.LongToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.graphhopper.search.KVStorage.KeyValue.*;
import static com.graphhopper.util.GHUtility.OSM_WARNING_LOGGER;
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

    private static final Pattern WAY_NAME_PATTERN = Pattern.compile("; *");

    private final OSMReaderConfig config;
    private final BaseGraph baseGraph;
    private final EdgeIntAccess edgeIntAccess;
    private final NodeAccess nodeAccess;
    private final TurnCostStorage turnCostStorage;
    private final OSMParsers osmParsers;
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    private final RestrictionSetter restrictionSetter;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private AreaIndex<CustomArea> areaIndex;
    private CountryRuleFactory countryRuleFactory = null;
    private File osmFile;
    private final RamerDouglasPeucker simplifyAlgo = new RamerDouglasPeucker();

    private final IntsRef tempRelFlags;
    private Date osmDataDate;
    private long zeroCounter = 0;

    private GHLongLongHashMap osmWayIdToRelationFlagsMap = new GHLongLongHashMap(200, .5f);
    private WayToEdgesMap restrictedWaysToEdgesMap = new WayToEdgesMap();
    private List<ReaderRelation> restrictionRelations = new ArrayList<>();

    private OSMAreaData osmAreaData;
    private AreaIndex<CustomArea> osmAreaIndex;
    private final EncodingManager encodingManager;

    public OSMReader(BaseGraph baseGraph, EncodingManager encodingManager, OSMParsers osmParsers, OSMReaderConfig config) {
        this.baseGraph = baseGraph;
        this.encodingManager = encodingManager;
        this.edgeIntAccess = baseGraph.createEdgeIntAccess();
        this.config = config;
        this.nodeAccess = baseGraph.getNodeAccess();
        this.osmParsers = osmParsers;
        this.restrictionSetter = new RestrictionSetter(baseGraph);

        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance());
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance());
        turnCostStorage = baseGraph.getTurnCostStorage();

        tempRelFlags = osmParsers.createRelationFlags();
        if (tempRelFlags.length != 2)
            // we use a long to store relation flags currently, so the relation flags ints ref must have length 2
            throw new IllegalArgumentException("OSMReader cannot use relation flags with != 2 integers");

        osmAreaData = new OSMAreaData(baseGraph.getDirectory());
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
        if (osmParsers == null)
            throw new IllegalStateException("Tag parsers were not set.");

        if (osmFile == null)
            throw new IllegalStateException("No OSM file specified");

        if (!osmFile.exists())
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmFile.getAbsolutePath());

        if (!baseGraph.isInitialized())
            throw new IllegalStateException("BaseGraph must be initialize before we can read OSM");

        WaySegmentParser.Builder waySegmentParserBuilder = new WaySegmentParser.Builder(baseGraph.getNodeAccess(), baseGraph.getDirectory())
                .setElevationProvider(eleProvider)
                .setWayFilter(this::acceptWay)
                .setSplitNodeFilter(this::isBarrierNode)
                .setWayPreprocessor(this::preprocessWay)
                .setRelationPreprocessor(this::preprocessRelations)
                .setRelationProcessor(this::processRelation)
                .setEdgeHandler(this::addEdge)
                .setWorkerThreads(config.getWorkerThreads());
        if (encodingManager.hasEncodedValue(Landuse.KEY)) {
            waySegmentParserBuilder
                    .setPass0RelationHook(this::keepOSMAreasFromRelations)
                    .setPass1WayPreHook(this::keepOSMAreas)
                    .setPass2NodePreHook(this::resolveOSMAreaCoordinates)
                    .setPass2AfterNodesHook(this::buildOSMAreaIndex);
        }
        WaySegmentParser waySegmentParser = waySegmentParserBuilder.build();
        waySegmentParser.readOSM(osmFile);
        osmDataDate = waySegmentParser.getTimeStamp();
        if (baseGraph.getNodes() == 0)
            throw new RuntimeException("Graph after reading OSM must not be empty");
        releaseEverythingExceptRestrictionData();
        addRestrictionsToGraph();
        releaseRestrictionData();
        LOGGER.info("Finished reading OSM file: {}, nodes: {}, edges: {}, zero distance edges: {}",
                osmFile.getAbsolutePath(), nf(baseGraph.getNodes()), nf(baseGraph.getEdges()), nf(zeroCounter));
    }

    /**
     * @return the timestamp given in the OSM file header or null if not found
     */
    public Date getDataDate() {
        return osmDataDate;
    }

    void keepOSMAreasFromRelations(ReaderRelation relation) {
        if (relation.hasTag("landuse") && relation.hasTag("type", "multipolygon"))
            osmAreaData.addRelation(relation);
    }

    void keepOSMAreas(ReaderWay way) {
        if (way.hasTag("landuse"))
            osmAreaData.addArea(way.getTags(), way.getNodes());

        osmAreaData.handleWay(way);
    }

    void resolveOSMAreaCoordinates(ReaderNode node) {
        osmAreaData.setCoordinate(node.getId(), node.getLat(), node.getLon());
    }

    void buildOSMAreaIndex() {
        List<CustomArea> osmAreas = osmAreaData.buildOSMAreas();
        osmAreaIndex = new AreaIndex<>(osmAreas);
        // todonow: remove later
        System.out.println(GraphLayout.parseInstance(osmAreaIndex).toFootprint());
        osmAreaData = null;
    }

    /**
     * This method is called for each way during the first and second pass of the {@link WaySegmentParser}. All OSM
     * ways that are not accepted here and all nodes that are not referenced by any such way will be ignored.
     */
    protected boolean acceptWay(ReaderWay way) {
        // ignore broken geometry
        if (way.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return false;

        return osmParsers.acceptWay(way);
    }

    /**
     * @return true if the given node should be duplicated to create an artificial edge. If the node turns out to be a
     * junction between different ways this will be ignored and no artificial edge will be created.
     */
    protected boolean isBarrierNode(ReaderNode node) {
        return node.hasTag("barrier") || node.hasTag("ford");
    }

    /**
     * @return true if the length of the way shall be calculated and added as an artificial way tag
     */
    protected boolean isCalculateWayDistance(ReaderWay way) {
        return isFerry(way);
    }

    private boolean isFerry(ReaderWay way) {
        return way.hasTag("route", "ferry", "shuttle_train");
    }

    /**
     * This method is called during the second pass of {@link WaySegmentParser} and provides an entry point to enrich
     * the given OSM way with additional tags before it is passed on to the tag parsers.
     */
    protected void setArtificialWayTags(PointList pointList, ReaderWay way, double distance, List<Map<String, Object>> nodeTags) {
        way.setTag("node_tags", nodeTags);
        way.setTag("edge_distance", distance);
        way.setTag("point_list", pointList);

        // we have to remove existing artificial tags, because we modify the way even though there can be multiple edges
        // per way. sooner or later we should separate the artificial ('edge') tags from the way, see discussion here:
        // https://github.com/graphhopper/graphhopper/pull/2457#discussion_r751155404
        way.removeTag("country");
        way.removeTag("country_rule");
        way.removeTag("custom_areas");
        way.removeTag("gh:osm_areas");

        List<CustomArea> customAreas = emptyList();
        List<CustomArea> osmAreas = emptyList();
        if (areaIndex != null || osmAreaIndex != null) {
            double middleLat;
            double middleLon;
            if (pointList.size() > 2) {
                middleLat = pointList.getLat(pointList.size() / 2);
                middleLon = pointList.getLon(pointList.size() / 2);
            } else {
                double firstLat = pointList.getLat(0), firstLon = pointList.getLon(0);
                double lastLat = pointList.getLat(pointList.size() - 1), lastLon = pointList.getLon(pointList.size() - 1);
                middleLat = (firstLat + lastLat) / 2;
                middleLon = (firstLon + lastLon) / 2;
            }
            if (areaIndex != null) customAreas = areaIndex.query(middleLat, middleLon);
            if (osmAreaIndex != null) osmAreas = osmAreaIndex.query(middleLat, middleLon);
        }

        // special handling for countries: since they are built-in with GraphHopper they are always fed to the EncodingManager
        Country country = Country.MISSING;
        CustomArea prevCustomArea = null;
        for (CustomArea customArea : customAreas) {
            if (customArea.getProperties() == null) continue;
            Object alpha3 = customArea.getProperties().get(Country.ISO_ALPHA3);
            if (alpha3 == null)
                continue;

            // multiple countries are available -> pick the smaller one, see #2663
            if (prevCustomArea != null && prevCustomArea.getArea() < customArea.getArea())
                break;

            prevCustomArea = customArea;
            country = Country.valueOf((String) alpha3);
        }
        way.setTag("country", country);

        if (countryRuleFactory != null) {
            CountryRule countryRule = countryRuleFactory.getCountryRule(country);
            if (countryRule != null)
                way.setTag("country_rule", countryRule);
        }

        // also add all custom areas as artificial tag
        way.setTag("custom_areas", customAreas);
        way.setTag("gh:osm_areas", osmAreas);
    }

    /**
     * This method is called for each segment an OSM way is split into during the second pass of {@link WaySegmentParser}.
     *
     * @param fromIndex a unique integer id for the first node of this segment
     * @param toIndex   a unique integer id for the last node of this segment
     * @param pointList coordinates of this segment
     * @param way       the OSM way this segment was taken from
     * @param nodeTags  node tags of this segment. there is one map of tags for each point.
     */
    protected void addEdge(int fromIndex, int toIndex, PointList pointList, ReaderWay way, List<Map<String, Object>> nodeTags) {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());
        if (pointList.size() != nodeTags.size())
            throw new AssertionError("there should be as many maps of node tags as there are points. node tags: " + nodeTags.size() + ", points: " + pointList.size());

        // todo: in principle it should be possible to delay elevation calculation so we do not need to store
        // elevations during import (saves memory in pillar info during import). also note that we already need to
        // to do some kind of elevation processing (bridge+tunnel interpolation in GraphHopper class, maybe this can
        // go together

        if (pointList.is3D()) {
            // sample points along long edges
            if (config.getLongEdgeSamplingDistance() < Double.MAX_VALUE)
                pointList = EdgeSampling.sample(pointList, config.getLongEdgeSamplingDistance(), distCalc, eleProvider);

            // smooth the elevation before calculating the distance because the distance will be incorrect if calculated afterwards
            if (config.getElevationSmoothing().equals("ramer"))
                EdgeElevationSmoothing.smoothRamer(pointList, config.getElevationSmoothingRamerMax());
            else if (config.getElevationSmoothing().equals("moving_average"))
                EdgeElevationSmoothing.smoothMovingAverage(pointList);
        }

        if (config.getMaxWayPointDistance() > 0 && pointList.size() > 2)
            simplifyAlgo.simplify(pointList);

        double distance = distCalc.calcDistance(pointList);

        if (distance < 0.001) {
            // As investigation shows often two paths should have crossed via one identical point
            // but end up in two very close points.
            zeroCounter++;
            distance = 0.001;
        }

        double maxDistance = (Integer.MAX_VALUE - 1) / 1000d;
        if (Double.isNaN(distance)) {
            LOGGER.warn("Bug in OSM or GraphHopper. Illegal tower node distance " + distance + " reset to 1m, osm way " + way.getId());
            distance = 1;
        }

        if (Double.isInfinite(distance) || distance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            // For example this happens here: https://www.openstreetmap.org/way/672506453 (Cape Town - Tristan da Cunha ferry)
            LOGGER.warn("Bug in OSM or GraphHopper. Too big tower node distance " + distance + " reset to large value, osm way " + way.getId());
            distance = maxDistance;
        }

        setArtificialWayTags(pointList, way, distance, nodeTags);
        IntsRef relationFlags = getRelFlagsMap(way.getId());
        EdgeIteratorState edge = baseGraph.edge(fromIndex, toIndex).setDistance(distance);
        osmParsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, relationFlags);
        List<KVStorage.KeyValue> list = way.getTag("key_values", Collections.emptyList());
        if (!list.isEmpty())
            edge.setKeyValues(list);

        // If the entire way is just the first and last point, do not waste space storing an empty way geometry
        if (pointList.size() > 2) {
            // the geometry consists only of pillar nodes, but we check that the first and last points of the pointList
            // are equal to the tower node coordinates
            checkCoordinates(fromIndex, pointList.get(0));
            checkCoordinates(toIndex, pointList.get(pointList.size() - 1));
            edge.setWayGeometry(pointList.shallowCopy(1, pointList.size() - 1, false));
        }

        checkDistance(edge);
        restrictedWaysToEdgesMap.putIfReserved(way.getId(), edge.getEdge());
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
        if (Double.isInfinite(edgeDistance))
            throw new IllegalStateException("Infinite edge distance should never occur, as we are supposed to limit each distance to the maximum distance we can store, #435");
        else if (edgeDistance > 2_000_000)
            LOGGER.warn("Very long edge detected: " + edge + " dist: " + edgeDistance);
        else if (Math.abs(edgeDistance - geometryDistance) > tolerance)
            throw new IllegalStateException("Suspicious distance for edge: " + edge + " " + edgeDistance + " vs. " + geometryDistance
                    + ", difference: " + (edgeDistance - geometryDistance));
    }

    /**
     * This method is called for each way during the second pass and before the way is split into edges.
     * We currently use it to parse road names and calculate the distance of a way to determine the speed based on
     * the duration tag when it is present. The latter cannot be done on a per-edge basis, because the duration tag
     * refers to the duration of the entire way.
     */
    protected void preprocessWay(ReaderWay way, WaySegmentParser.CoordinateSupplier coordinateSupplier) {
        // storing the road name does not yet depend on the flagEncoder so manage it directly
        List<KVStorage.KeyValue> list = new ArrayList<>();
        if (config.isParseWayNames()) {
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = "";
            if (!config.getPreferredLanguage().isEmpty())
                name = fixWayName(way.getTag("name:" + config.getPreferredLanguage()));
            if (name.isEmpty())
                name = fixWayName(way.getTag("name"));
            if (!name.isEmpty())
                list.add(new KVStorage.KeyValue(STREET_NAME, name));

            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!refName.isEmpty())
                list.add(new KVStorage.KeyValue(STREET_REF, refName));

            if (way.hasTag("destination:ref")) {
                list.add(new KVStorage.KeyValue(STREET_DESTINATION_REF, fixWayName(way.getTag("destination:ref"))));
            } else {
                if (way.hasTag("destination:ref:forward"))
                    list.add(new KVStorage.KeyValue(STREET_DESTINATION_REF, fixWayName(way.getTag("destination:ref:forward")), true, false));
                if (way.hasTag("destination:ref:backward"))
                    list.add(new KVStorage.KeyValue(STREET_DESTINATION_REF, fixWayName(way.getTag("destination:ref:backward")), false, true));
            }
            if (way.hasTag("destination")) {
                list.add(new KVStorage.KeyValue(STREET_DESTINATION, fixWayName(way.getTag("destination"))));
            } else {
                if (way.hasTag("destination:forward"))
                    list.add(new KVStorage.KeyValue(STREET_DESTINATION, fixWayName(way.getTag("destination:forward")), true, false));
                if (way.hasTag("destination:backward"))
                    list.add(new KVStorage.KeyValue(STREET_DESTINATION, fixWayName(way.getTag("destination:backward")), false, true));
            }
        }
        way.setTag("key_values", list);

        if (!isCalculateWayDistance(way))
            return;

        double distance = calcDistance(way, coordinateSupplier);
        if (Double.isNaN(distance)) {
            // Some nodes were missing, and we cannot determine the distance. This can happen when ways are only
            // included partially in an OSM extract. In this case we cannot calculate the speed either, so we return.
            LOGGER.warn("Could not determine distance for OSM way: " + way.getId());
            return;
        }
        way.setTag("way_distance", distance);

        // For ways with a duration tag we determine the average speed. This is needed for e.g. ferry routes, because
        // the duration tag is only valid for the entire way, and it would be wrong to use it after splitting the way
        // into edges.
        String durationTag = way.getTag("duration");
        if (durationTag == null) {
            // no duration tag -> we cannot derive speed. happens very frequently for short ferries, but also for some long ones, see: #2532
            if (isFerry(way) && distance > 500_000)
                OSM_WARNING_LOGGER.warn("Long ferry OSM way without duration tag: " + way.getId() + ", distance: " + Math.round(distance / 1000.0) + " km");
            return;
        }
        long durationInSeconds;
        try {
            durationInSeconds = OSMReaderUtility.parseDuration(durationTag);
        } catch (Exception e) {
            OSM_WARNING_LOGGER.warn("Could not parse duration tag '" + durationTag + "' in OSM way: " + way.getId());
            return;
        }

        double speedInKmPerHour = distance / 1000 / (durationInSeconds / 60.0 / 60.0);
        if (speedInKmPerHour < 0.1d) {
            // Often there are mapping errors like duration=30:00 (30h) instead of duration=00:30 (30min). In this case we
            // ignore the duration tag. If no such cases show up anymore, because they were fixed, maybe raise the limit to find some more.
            OSM_WARNING_LOGGER.warn("Unrealistic low speed calculated from duration. Maybe the duration is too long, or it is applied to a way that only represents a part of the connection? OSM way: "
                    + way.getId() + ". duration=" + durationTag + " (= " + Math.round(durationInSeconds / 60.0) +
                    " minutes), distance=" + distance + " m");
            return;
        }
        // These tags will be present if 1) isCalculateWayDistance was true for this way, 2) no OSM nodes were missing
        // such that the distance could actually be calculated, 3) there was a duration tag we could parse, and 4) the
        // derived speed was not unrealistically slow.
        way.setTag("speed_from_duration", speedInKmPerHour);
        way.setTag("duration:seconds", durationInSeconds);
    }

    static String fixWayName(String str) {
        if (str == null)
            return "";
        // the KVStorage does not accept too long strings -> Helper.cutStringForKV
        return KVStorage.cutString(WAY_NAME_PATTERN.matcher(str).replaceAll(", "));
    }

    /**
     * @return the distance of the given way or NaN if some nodes were missing
     */
    private double calcDistance(ReaderWay way, WaySegmentParser.CoordinateSupplier coordinateSupplier) {
        LongArrayList nodes = way.getNodes();
        // every way has at least two nodes according to our acceptWay function
        GHPoint3D prevPoint = coordinateSupplier.getCoordinate(nodes.get(0));
        if (prevPoint == null)
            return Double.NaN;
        boolean is3D = !Double.isNaN(prevPoint.ele);
        double distance = 0;
        for (int i = 1; i < nodes.size(); i++) {
            GHPoint3D point = coordinateSupplier.getCoordinate(nodes.get(i));
            if (point == null)
                return Double.NaN;
            if (Double.isNaN(point.ele) == is3D)
                throw new IllegalStateException("There should be elevation data for either all points or no points at all. OSM way: " + way.getId());
            distance += is3D
                    ? distCalc.calcDist3D(prevPoint.lat, prevPoint.lon, prevPoint.ele, point.lat, point.lon, point.ele)
                    : distCalc.calcDist(prevPoint.lat, prevPoint.lon, point.lat, point.lon);
            prevPoint = point;
        }
        return distance;
    }

    /**
     * This method is called for each relation during the first pass of {@link WaySegmentParser}
     */
    protected void preprocessRelations(ReaderRelation relation) {
        if (!relation.isMetaRelation() && relation.hasTag("type", "route")) {
            // we keep track of all route relations, so they are available when we create edges later
            for (ReaderRelation.Member member : relation.getMembers()) {
                if (member.getType() != ReaderElement.Type.WAY)
                    continue;
                IntsRef oldRelationFlags = getRelFlagsMap(member.getRef());
                IntsRef newRelationFlags = osmParsers.handleRelationTags(relation, oldRelationFlags);
                putRelFlagsMap(member.getRef(), newRelationFlags);
            }
        }

        Arrays.stream(RestrictionConverter.getRestrictedWayIds(relation))
                .forEach(restrictedWaysToEdgesMap::reserve);
    }

    /**
     * This method is called for each relation during the second pass of {@link WaySegmentParser}
     * We use it to save the relations and process them afterwards.
     */
    protected void processRelation(ReaderRelation relation, LongToIntFunction getIdForOSMNodeId) {
        if (turnCostStorage != null)
            if (RestrictionConverter.isTurnRestriction(relation)) {
                long osmViaNode = RestrictionConverter.getViaNodeIfViaNodeRestriction(relation);
                if (osmViaNode >= 0) {
                    int viaNode = getIdForOSMNodeId.applyAsInt(osmViaNode);
                    // only include the restriction if the corresponding node wasn't excluded
                    if (viaNode >= 0) {
                        relation.setTag("graphhopper:via_node", viaNode);
                        restrictionRelations.add(relation);
                    }
                } else
                    // not a via-node restriction -> simply add it as is
                    restrictionRelations.add(relation);
            }
    }

    private void addRestrictionsToGraph() {
        // The OSM restriction format is explained here: https://wiki.openstreetmap.org/wiki/Relation:restriction
        List<Triple<ReaderRelation, GraphRestriction, RestrictionMembers>> restrictions = new ArrayList<>(restrictionRelations.size());
        for (ReaderRelation restrictionRelation : restrictionRelations) {
            try {
                // convert the OSM relation topology to the graph representation. this only needs to be done once for all
                // vehicle types (we also want to print warnings only once)
                restrictions.add(RestrictionConverter.convert(restrictionRelation, baseGraph, restrictedWaysToEdgesMap::getEdges));
            } catch (OSMRestrictionException e) {
                warnOfRestriction(restrictionRelation, e);
            }
        }
        // The restriction type depends on the vehicle, or at least not all restrictions affect every vehicle type.
        // We handle the restrictions for one vehicle after another.
        for (RestrictionTagParser restrictionTagParser : osmParsers.getRestrictionTagParsers()) {
            LongSet viaWaysUsedByOnlyRestrictions = new LongHashSet();
            List<Pair<GraphRestriction, RestrictionType>> restrictionsWithType = new ArrayList<>(restrictions.size());
            for (Triple<ReaderRelation, GraphRestriction, RestrictionMembers> r : restrictions) {
                if (r.second == null)
                    // this relation was found to be invalid by another restriction tag parser already
                    continue;
                try {
                    RestrictionTagParser.Result res = restrictionTagParser.parseRestrictionTags(r.first.getTags());
                    if (res == null)
                        // this relation is ignored by the current restriction tag parser
                        continue;
                    RestrictionConverter.checkIfCompatibleWithRestriction(r.second, res.getRestriction());
                    // we ignore 'only' via-way restrictions that share the same via way, because these would require adding
                    // multiple artificial edges, see here: https://github.com/graphhopper/graphhopper/pull/2689#issuecomment-1306769694
                    if (r.second.isViaWayRestriction() && res.getRestrictionType() == RestrictionType.ONLY)
                        for (LongCursor viaWay : r.third.getViaWays())
                            if (!viaWaysUsedByOnlyRestrictions.add(viaWay.value))
                                throw new OSMRestrictionException("has a member with role 'via' that is also used as 'via' member by another 'only' restriction. GraphHopper cannot handle this.");
                    restrictionsWithType.add(new Pair<>(r.second, res.getRestrictionType()));
                } catch (OSMRestrictionException e) {
                    warnOfRestriction(r.first, e);
                    // we only want to print a warning once for each restriction relation, so we make sure this
                    // restriction is ignored for the other vehicles
                    r.second = null;
                }
            }
            restrictionSetter.setRestrictions(restrictionsWithType, restrictionTagParser.getTurnCostEnc());
        }
    }

    public IntIntMap getArtificialEdgesByEdges() {
        return restrictionSetter.getArtificialEdgesByEdges();
    }

    private static void warnOfRestriction(ReaderRelation restrictionRelation, OSMRestrictionException e) {
        // we do not log exceptions with an empty message
        if (!e.isWithoutWarning()) {
            restrictionRelation.getTags().remove("graphhopper:via_node");
            List<String> members = restrictionRelation.getMembers().stream().map(m -> m.getRole() + " " + m.getType().toString().toLowerCase() + " " + m.getRef()).collect(Collectors.toList());
            OSM_WARNING_LOGGER.warn("Restriction relation " + restrictionRelation.getId() + " " + e.getMessage() + ". tags: " + restrictionRelation.getTags() + ", members: " + members + ". Relation ignored.");
        }
    }

    private void releaseEverythingExceptRestrictionData() {
        eleProvider.release();
        osmWayIdToRelationFlagsMap = null;
    }

    private void releaseRestrictionData() {
        restrictedWaysToEdgesMap = null;
        restrictionRelations = null;
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
