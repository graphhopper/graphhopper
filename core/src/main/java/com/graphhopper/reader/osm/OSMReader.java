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

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.coll.GHLongLongHashMap;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.EdgeElevationSmoothingMovingAverage;
import com.graphhopper.reader.dem.EdgeElevationSmoothingRamer;
import com.graphhopper.reader.dem.EdgeSampling;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.State;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.FerrySpeedCalculator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.LongToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.graphhopper.search.KVStorage.KValue;
import static com.graphhopper.util.GHUtility.OSM_WARNING_LOGGER;
import static com.graphhopper.util.Helper.nf;
import static com.graphhopper.util.Parameters.Details.*;
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
    private int bugCounter = 0;
    private final IntsRef tempRelFlags;
    private Date osmDataDate;
    private long zeroCounter = 0;

    private GHLongLongHashMap osmWayIdToRelationFlagsMap = new GHLongLongHashMap(200, .5f);
    private WayToEdgesMap restrictedWaysToEdgesMap = new WayToEdgesMap();
    private List<ReaderRelation> restrictionRelations = new ArrayList<>();

    public OSMReader(BaseGraph baseGraph, OSMParsers osmParsers, OSMReaderConfig config) {
        this.baseGraph = baseGraph;
        this.edgeIntAccess = baseGraph.getEdgeAccess();
        this.config = config;
        this.nodeAccess = baseGraph.getNodeAccess();
        this.osmParsers = osmParsers;
        this.restrictionSetter = new RestrictionSetter(baseGraph, osmParsers.getRestrictionTagParsers().stream().map(RestrictionTagParser::getTurnRestrictionEnc).toList());

        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance());
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance());
        turnCostStorage = baseGraph.getTurnCostStorage();

        tempRelFlags = osmParsers.createRelationFlags();
        if (tempRelFlags.length != 2)
            // we use a long to store relation flags currently, so the relation flags ints ref must have length 2
            throw new IllegalArgumentException("OSMReader cannot use relation flags with != 2 integers");
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

        WaySegmentParser waySegmentParser = new WaySegmentParser.Builder(baseGraph.getNodeAccess(), baseGraph.getDirectory())
                .setElevationProvider(this::getElevation)
                .setWayFilter(this::acceptWay)
                .setSplitNodeFilter(this::isBarrierNode)
                .setWayPreprocessor(this::preprocessWay)
                .setRelationPreprocessor(this::preprocessRelations)
                .setRelationProcessor(this::processRelation)
                .setEdgeHandler(this::addEdge)
                .setWorkerThreads(config.getWorkerThreads())
                .build();
        waySegmentParser.readOSM(osmFile);
        osmDataDate = waySegmentParser.getTimestamp();
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

    protected double getElevation(ReaderNode node) {
        double ele = eleProvider.getEle(node);
        return Double.isNaN(ele) ? config.getDefaultElevation() : ele;
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
        return FerrySpeedCalculator.isFerry(way);
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

        List<CustomArea> customAreas;
        if (areaIndex != null) {
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
            customAreas = areaIndex.query(middleLat, middleLon);
        } else {
            customAreas = emptyList();
        }

        // special handling for countries: since they are built-in with GraphHopper they are always fed to the EncodingManager
        Country country = Country.MISSING;
        State state = State.MISSING;
        double countryArea = Double.POSITIVE_INFINITY;
        for (CustomArea customArea : customAreas) {
            // ignore areas that aren't countries
            if (customArea.getProperties() == null) continue;
            String alpha2WithSubdivision = (String) customArea.getProperties().get(State.ISO_3166_2);
            if (alpha2WithSubdivision == null)
                continue;

            // the country string must be either something like US-CA (including subdivision) or just DE
            String[] strs = alpha2WithSubdivision.split("-");
            if (strs.length == 0 || strs.length > 2)
                throw new IllegalStateException("Invalid alpha2: " + alpha2WithSubdivision);
            Country c = Country.find(strs[0]);
            if (c == null)
                throw new IllegalStateException("Unknown country: " + strs[0]);

            if (
                // countries with subdivision overrule those without subdivision as well as bigger ones with subdivision
                    strs.length == 2 && (state == State.MISSING || customArea.getArea() < countryArea)
                            // countries without subdivision only overrule bigger ones without subdivision
                            || strs.length == 1 && (state == State.MISSING && customArea.getArea() < countryArea)) {
                country = c;
                state = State.find(alpha2WithSubdivision);
                countryArea = customArea.getArea();
            }
        }
        way.setTag("country", country);
        way.setTag("country_state", state);

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
                EdgeElevationSmoothingRamer.smooth(pointList, config.getElevationSmoothingRamerMax());
            else if (config.getElevationSmoothing().equals("moving_average"))
                EdgeElevationSmoothingMovingAverage.smooth(pointList, config.getSmoothElevationAverageWindowSize());
            else if (!config.getElevationSmoothing().isEmpty())
                throw new AssertionError("Unsupported elevation smoothing algorithm: '" + config.getElevationSmoothing() + "'");
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
            LOGGER.warn("Bug in OSM or GraphHopper (" + bugCounter++ + "). Illegal tower node distance " + distance + " reset to 1m, osm way " + way.getId());
            distance = 1;
        }

        if (Double.isInfinite(distance) || distance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            // For example this happens here: https://www.openstreetmap.org/way/672506453 (Cape Town - Tristan da Cunha ferry)
            LOGGER.warn("Bug in OSM or GraphHopper (" + bugCounter++ + "). Too big tower node distance " + distance + " reset to large value, osm way " + way.getId());
            distance = maxDistance;
        }

        if (bugCounter > 30)
            throw new IllegalStateException("Too many bugs in OSM or GraphHopper encountered " + bugCounter);

        setArtificialWayTags(pointList, way, distance, nodeTags);
        IntsRef relationFlags = getRelFlagsMap(way.getId());
        EdgeIteratorState edge = baseGraph.edge(fromIndex, toIndex).setDistance(distance);
        osmParsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, relationFlags);

        Map<String, KValue> map = way.getTag("key_values", Collections.emptyMap());
        if (!map.isEmpty())
            edge.setKeyValues(map);

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
    protected void preprocessWay(ReaderWay way, WaySegmentParser.CoordinateSupplier coordinateSupplier,
                                 WaySegmentParser.NodeTagSupplier nodeTagSupplier) {
        Map<String, KValue> map = new LinkedHashMap<>();
        if (config.isParseWayNames()) {
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = "";
            if (!config.getPreferredLanguage().isEmpty())
                name = fixWayName(way.getTag("name:" + config.getPreferredLanguage()));
            if (name.isEmpty())
                name = fixWayName(way.getTag("name"));
            if (!name.isEmpty())
                map.put(STREET_NAME, new KValue(name));

            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!refName.isEmpty())
                map.put(STREET_REF, new KValue(refName));

            if (way.hasTag("destination:ref")) {
                map.put(STREET_DESTINATION_REF, new KValue(fixWayName(way.getTag("destination:ref"))));
            } else {
                String fwdStr = fixWayName(way.getTag("destination:ref:forward"));
                String bwdStr = fixWayName(way.getTag("destination:ref:backward"));
                if (!fwdStr.isEmpty() || !bwdStr.isEmpty())
                    map.put(STREET_DESTINATION_REF, new KValue(fwdStr.isEmpty() ? null : fwdStr, bwdStr.isEmpty() ? null : bwdStr));
            }
            if (way.hasTag("destination")) {
                map.put(STREET_DESTINATION, new KValue(fixWayName(way.getTag("destination"))));
            } else {
                String fwdStr = fixWayName(way.getTag("destination:forward"));
                String bwdStr = fixWayName(way.getTag("destination:backward"));
                if (!fwdStr.isEmpty() || !bwdStr.isEmpty())
                    map.put(STREET_DESTINATION, new KValue(fwdStr.isEmpty() ? null : fwdStr, bwdStr.isEmpty() ? null : bwdStr));
            }

            // copy node name of motorway_junction
            LongArrayList nodes = way.getNodes();
            if (!nodes.isEmpty() && (way.hasTag("highway", "motorway") || way.hasTag("highway", "motorway_link"))) {
                // index 0 assumes oneway=yes
                Map<String, Object> nodeTags = nodeTagSupplier.getTags(nodes.get(0));
                String nodeName = (String) nodeTags.getOrDefault("name", "");
                if (!nodeName.isEmpty() && "motorway_junction".equals(nodeTags.getOrDefault("highway", "")))
                    map.put(MOTORWAY_JUNCTION, new KValue(nodeName));
            }
        }

        if (way.getTags().size() > 1) // at least highway tag
            for (Map.Entry<String, Object> entry : way.getTags().entrySet()) {
                if (entry.getKey().endsWith(":conditional") && entry.getValue() instanceof String &&
                        // for now reduce index size a bit and focus on access tags
                        !entry.getKey().startsWith("maxspeed") && !entry.getKey().startsWith("maxweight")) {
                    // remove spaces as they unnecessarily increase the unique number of values:
                    String value = KVStorage.cutString(((String) entry.getValue()).
                            replace(" ", "").replace("bicycle", "bike"));
                    String key = entry.getKey().replace(':', '_').replace("bicycle", "bike");
                    boolean fwd = key.contains("forward");
                    boolean bwd = key.contains("backward");
                    if (!value.isEmpty()) {
                        if (fwd == bwd)
                            map.put(key, new KValue(value));
                        else
                            map.put(key, new KValue(fwd ? value : null, bwd ? value : null));
                    }
                }
            }

        way.setTag("key_values", map);

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
        // tag will be present if 1) isCalculateWayDistance was true for this way, 2) no OSM nodes were missing
        // such that the distance could actually be calculated, 3) there was a duration tag we could parse, and 4) the
        // derived speed was not unrealistically slow.
        way.setTag("speed_from_duration", speedInKmPerHour);
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

        Arrays.stream(OSMRestrictionConverter.getRestrictedWayIds(relation))
                .forEach(restrictedWaysToEdgesMap::reserve);
    }

    /**
     * This method is called for each relation during the second pass of {@link WaySegmentParser}
     * We use it to save the relations and process them afterwards.
     */
    protected void processRelation(ReaderRelation relation, LongToIntFunction getIdForOSMNodeId) {
        if (turnCostStorage != null)
            if (OSMRestrictionConverter.isTurnRestriction(relation)) {
                long osmViaNode = OSMRestrictionConverter.getViaNodeIfViaNodeRestriction(relation);
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
        if (turnCostStorage == null)
            return;
        StopWatch sw = StopWatch.started();
        // The OSM restriction format is explained here: https://wiki.openstreetmap.org/wiki/Relation:restriction
        List<Triple<ReaderRelation, RestrictionTopology, RestrictionMembers>> restrictionRelationsWithTopology = new ArrayList<>(restrictionRelations.size());
        for (ReaderRelation restrictionRelation : restrictionRelations) {
            try {
                // Build the topology of the OSM relation in the graph representation. This only needs to be done once for all
                // vehicle types (we also want to print warnings only once)
                restrictionRelationsWithTopology.add(OSMRestrictionConverter.buildRestrictionTopologyForGraph(baseGraph, restrictionRelation, restrictedWaysToEdgesMap::getEdges));
            } catch (OSMRestrictionException e) {
                warnOfRestriction(restrictionRelation, e);
            }
        }
        // It is important to set the restrictions for all parsers/encoded values at once to make
        // sure the resulting turn restrictions do not interfere.
        List<RestrictionSetter.Restriction> restrictions = new ArrayList<>();
        // For every restriction we set flags that indicate the validity for the different parsers
        List<BitSet> encBits = new ArrayList<>();
        for (Triple<ReaderRelation, RestrictionTopology, RestrictionMembers> r : restrictionRelationsWithTopology) {
            try {
                BitSet bits = new BitSet(osmParsers.getRestrictionTagParsers().size());
                RestrictionType restrictionType = null;
                for (int i = 0; i < osmParsers.getRestrictionTagParsers().size(); i++) {
                    RestrictionTagParser restrictionTagParser = osmParsers.getRestrictionTagParsers().get(i);
                    RestrictionTagParser.Result res = restrictionTagParser.parseRestrictionTags(r.first.getTags());
                    if (res == null)
                        // this relation is ignored by this restriction tag parser
                        continue;
                    OSMRestrictionConverter.checkIfTopologyIsCompatibleWithRestriction(r.second, res.getRestriction());
                    if (restrictionType != null && res.getRestrictionType() != restrictionType)
                        // so far we restrict ourselves to restriction relations that use the same type for all vehicles
                        throw new OSMRestrictionException("has different restriction type for different vehicles.");
                    restrictionType = res.getRestrictionType();
                    bits.set(i);
                }
                if (bits.cardinality() > 0) {
                    List<RestrictionSetter.Restriction> tmpRestrictions = OSMRestrictionConverter.buildRestrictionsForOSMRestriction(baseGraph, r.second, restrictionType);
                    restrictions.addAll(tmpRestrictions);
                    tmpRestrictions.forEach(__ -> encBits.add(RestrictionSetter.copyEncBits(bits)));
                }
            } catch (OSMRestrictionException e) {
                warnOfRestriction(r.first, e);
            }
        }
        restrictionSetter.setRestrictions(restrictions, encBits);
        LOGGER.info("Finished adding turn restrictions. total turn cost entries: {}, took: {}",
                Helper.nf(baseGraph.getTurnCostStorage().getTurnCostsCount()), sw.stop().getTimeString());
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
