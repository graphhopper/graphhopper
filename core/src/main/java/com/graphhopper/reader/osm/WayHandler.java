package com.graphhopper.reader.osm;

import static com.graphhopper.reader.osm.OSMNodeData.isNodeId;
import static com.graphhopper.reader.osm.OSMNodeData.isPillarNode;
import static com.graphhopper.reader.osm.OSMNodeData.isTowerNode;
import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.EdgeElevationSmoothing;
import com.graphhopper.reader.dem.EdgeSampling;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RamerDouglasPeucker;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

public class WayHandler extends WayHandlerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(WayHandler.class);
    private static final Pattern WAY_NAME_PATTERN = Pattern.compile("; *");
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    
    private long wayCounter;
    private long zeroCounter = 0;
    
    private BaseGraph baseGraph;
    private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    private RelationFlagsData relationFlagsData;
    private final OSMReaderConfig config; 
    private final NodeAccess nodeAccess;
    private final RamerDouglasPeucker simplifyAlgo;
    private final ElevationProvider eleProvider;
    private final CountryRuleFactory countryRuleFactory;
    private final AreaIndex<CustomArea> areaIndex;
    private final EncodingManager encodingManager;

    public WayHandler(BaseGraph baseGraph, OSMParsers osmParsers, OSMNodeData nodeData, 
    		OSMTurnRestrictionData restrictionData, RelationFlagsData relationFlagsData, 
    		OSMReaderConfig config, NodeAccess nodeAccess, RamerDouglasPeucker simplifyAlgo,
    		ElevationProvider eleProvider, CountryRuleFactory countryRuleFactory,
    		AreaIndex<CustomArea> areaIndex, EncodingManager encodingManager) {
    	super(osmParsers);
    	this.wayCounter = -1;
    	this.baseGraph = baseGraph;
    	this.nodeData = nodeData;
		this.restrictionData = restrictionData;
		this.relationFlagsData = relationFlagsData;
    	this.config = config;
    	this.nodeAccess = nodeAccess;
    	this.simplifyAlgo = simplifyAlgo;
    	this.eleProvider = eleProvider;
		this.countryRuleFactory = countryRuleFactory;
		this.areaIndex = areaIndex;
		this.encodingManager = encodingManager;
    }
    
    public void handleWay(ReaderWay way) {
        if (++wayCounter % 10_000_000 == 0)
            LOGGER.info("pass2 - processed ways: " + nf(wayCounter) + ", " + Helper.getMemInfo());
        
        if (!acceptWay(way))
            return;
        
        List<SegmentNode> segment = new ArrayList<>(way.getNodes().size());
        for (LongCursor node : way.getNodes())
            segment.add(new SegmentNode(node.value, nodeData.getId(node.value)));
        preprocessWay(way, osmNodeId -> nodeData.getCoordinates(nodeData.getId(osmNodeId)));
        splitWayAtJunctionsAndEmptySections(segment, way);
    }
    
    /**
     * This method is called for each way during the second pass and before the way is split into edges.
     * We currently use it to parse road names and calculate the distance of a way to determine the speed based on
     * the duration tag when it is present. The latter cannot be done on a per-edge basis, because the duration tag
     * refers to the duration of the entire way.
     */
    protected void preprocessWay(ReaderWay way, CoordinateSupplier coordinateSupplier) {
        // storing the road name does not yet depend on the flagEncoder so manage it directly
        List<EdgeKVStorage.KeyValue> list = new ArrayList<>();
        if (config.isParseWayNames()) {
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = "";
            if (!config.getPreferredLanguage().isEmpty())
                name = fixWayName(way.getTag("name:" + config.getPreferredLanguage()));
            if (name.isEmpty())
                name = fixWayName(way.getTag("name"));
            if (!name.isEmpty())
                list.add(new EdgeKVStorage.KeyValue("name", name));

            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!refName.isEmpty())
                list.add(new EdgeKVStorage.KeyValue("ref", refName));

            if (way.hasTag("destination:ref")) {
                list.add(new EdgeKVStorage.KeyValue("destination_ref", fixWayName(way.getTag("destination:ref"))));
            } else {
                if (way.hasTag("destination:ref:forward"))
                    list.add(new EdgeKVStorage.KeyValue("destination_ref", fixWayName(way.getTag("destination:ref:forward")), true, false));
                if (way.hasTag("destination:ref:backward"))
                    list.add(new EdgeKVStorage.KeyValue("destination_ref", fixWayName(way.getTag("destination:ref:backward")), false, true));
            }
            if (way.hasTag("destination")) {
                list.add(new EdgeKVStorage.KeyValue("destination", fixWayName(way.getTag("destination"))));
            } else {
                if (way.hasTag("destination:forward"))
                    list.add(new EdgeKVStorage.KeyValue("destination", fixWayName(way.getTag("destination:forward")), true, false));
                if (way.hasTag("destination:backward"))
                    list.add(new EdgeKVStorage.KeyValue("destination", fixWayName(way.getTag("destination:backward")), false, true));
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
                LOGGER.warn("Long ferry OSM way without duration tag: " + way.getId() + ", distance: " + Math.round(distance / 1000.0) + " km");
            return;
        }
        long durationInSeconds;
        try {
            durationInSeconds = OSMReaderUtility.parseDuration(durationTag);
        } catch (Exception e) {
            LOGGER.warn("Could not parse duration tag '" + durationTag + "' in OSM way: " + way.getId());
            return;
        }

        double speedInKmPerHour = distance / 1000 / (durationInSeconds / 60.0 / 60.0);
        if (speedInKmPerHour < 0.1d) {
            // Often there are mapping errors like duration=30:00 (30h) instead of duration=00:30 (30min). In this case we
            // ignore the duration tag. If no such cases show up anymore, because they were fixed, maybe raise the limit to find some more.
            LOGGER.warn("Unrealistic low speed calculated from duration. Maybe the duration is too long, or it is applied to a way that only represents a part of the connection? OSM way: "
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
        // the EdgeKVStorage does not accept too long strings -> Helper.cutStringForKV
        return EdgeKVStorage.cutString(WAY_NAME_PATTERN.matcher(str).replaceAll(", "));
    }

    /**
     * @return the distance of the given way or NaN if some nodes were missing
     */
    private double calcDistance(ReaderWay way, CoordinateSupplier coordinateSupplier) {
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
     * @return true if the length of the way shall be calculated and added as an artificial way tag
     */
    protected boolean isCalculateWayDistance(ReaderWay way) {
        return isFerry(way);
    }

    private boolean isFerry(ReaderWay way) {
        return way.hasTag("route", "ferry", "shuttle_train");
    }

    /**
     * This method provides an entry point to enrich
     * the given OSM way with additional tags before it is passed on to the tag parsers.
     */
    protected void setArtificialWayTags(PointList pointList, ReaderWay way, double distance, Map<String, Object> nodeTags) {
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

    
    private void splitWayAtJunctionsAndEmptySections(List<SegmentNode> fullSegment, ReaderWay way) {
        List<SegmentNode> segment = new ArrayList<>();
        for (SegmentNode node : fullSegment) {
            if (!isNodeId(node.id)) {
                // this node exists in ways, but not in nodes. we ignore it, but we split the way when we encounter
                // such a missing node. for example an OSM way might lead out of an area where nodes are available and
                // back into it. we do not want to connect the exit/entry points using a straight line. this usually
                // should only happen for OSM extracts
                if (segment.size() > 1) {
                    splitLoopSegments(segment, way);
                    segment = new ArrayList<>();
                }
            } else if (isTowerNode(node.id)) {
                if (!segment.isEmpty()) {
                    segment.add(node);
                    splitLoopSegments(segment, way);
                    segment = new ArrayList<>();
                }
                segment.add(node);
            } else {
                segment.add(node);
            }
        }
        // the last segment might end at the end of the way
        if (segment.size() > 1)
            splitLoopSegments(segment, way);
    }

    private void splitLoopSegments(List<SegmentNode> segment, ReaderWay way) {
        if (segment.size() < 2)
            throw new IllegalStateException("Segment size must be >= 2, but was: " + segment.size());

        boolean isLoop = segment.get(0).osmNodeId == segment.get(segment.size() - 1).osmNodeId;
        if (segment.size() == 2 && isLoop) {
            LOGGER.warn("Loop in OSM way: {}, will be ignored, duplicate node: {}", way.getId(), segment.get(0).osmNodeId);
        } else if (isLoop) {
            // split into two segments
            splitSegmentAtSplitNodes(segment.subList(0, segment.size() - 1), way);
            splitSegmentAtSplitNodes(segment.subList(segment.size() - 2, segment.size()), way);
        } else {
            splitSegmentAtSplitNodes(segment, way);
        }
    }

    private void splitSegmentAtSplitNodes(List<SegmentNode> parentSegment, ReaderWay way) {
        List<SegmentNode> segment = new ArrayList<>();
        for (int i = 0; i < parentSegment.size(); i++) {
            SegmentNode node = parentSegment.get(i);
            Map<String, Object> nodeTags = nodeData.getTags(node.osmNodeId);
            // so far we only consider node tags of split nodes, so if there are node tags we split the node
            if (!nodeTags.isEmpty()) {
                // this node is a barrier. we will copy it and add an extra edge
                SegmentNode barrierFrom = node;
                SegmentNode barrierTo = nodeData.addCopyOfNodeAsPillarNode(node);
                if (i == parentSegment.size() - 1) {
                    // make sure the barrier node is always on the inside of the segment
                    SegmentNode tmp = barrierFrom;
                    barrierFrom = barrierTo;
                    barrierTo = tmp;
                }
                if (!segment.isEmpty()) {
                    segment.add(barrierFrom);
                    handleSegment(segment, way, emptyMap());
                    segment = new ArrayList<>();
                }
                segment.add(barrierFrom);
                segment.add(barrierTo);
                handleSegment(segment, way, nodeTags);
                segment = new ArrayList<>();
                segment.add(barrierTo);

                // ignore this barrier node from now. for example a barrier can be connecting two ways (appear in both
                // ways) and we only want to add a barrier edge once (but we want to add one).
                nodeData.removeTags(node.osmNodeId);
            } else {
                segment.add(node);
            }
        }
        if (segment.size() > 1)
            handleSegment(segment, way, emptyMap());
    }

    void handleSegment(List<SegmentNode> segment, ReaderWay way, Map<String, Object> nodeTags) {
        final PointList pointList = new PointList(segment.size(), nodeData.is3D());
        int from = -1;
        int to = -1;
        for (int i = 0; i < segment.size(); i++) {
            SegmentNode node = segment.get(i);
            int id = node.id;
            if (!isNodeId(id))
                throw new IllegalStateException("Invalid id for node: " + node.osmNodeId + " when handling segment " + segment + " for way: " + way.getId());
            if (isPillarNode(id) && (i == 0 || i == segment.size() - 1)) {
                id = nodeData.convertPillarToTowerNode(id, node.osmNodeId);
                node.id = id;
            }

            if (i == 0)
                from = nodeData.idToTowerNode(id);
            else if (i == segment.size() - 1)
                to = nodeData.idToTowerNode(id);
            else if (isTowerNode(id))
                throw new IllegalStateException("Tower nodes should only appear at the end of segments, way: " + way.getId());
            nodeData.addCoordinatesToPointList(id, pointList);
        }
        if (from < 0 || to < 0)
            throw new IllegalStateException("The first and last nodes of a segment must be tower nodes, way: " + way.getId());
        addEdge(from, to, pointList, way, nodeTags);
    }

    /**
     * This method is called for each segment an OSM way is split into.
     *
     * @param fromIndex a unique integer id for the first node of this segment
     * @param toIndex   a unique integer id for the last node of this segment
     * @param pointList coordinates of this segment
     * @param way       the OSM way this segment was taken from
     * @param nodeTags  node tags of this segment if it is an artificial edge, empty otherwise
     */
    protected void addEdge(int fromIndex, int toIndex, PointList pointList, ReaderWay way, Map<String, Object> nodeTags) {    	
    	if (restrictionData.osmWayIdsToIgnore.contains(way.getId())){
        	return;
        }
    	
    	// sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

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
        IntsRef relationFlags = relationFlagsData.getRelFlagsMap(way.getId());
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        edgeFlags = osmParsers.handleWayTags(edgeFlags, way, relationFlags);
        if (edgeFlags.isEmpty())
            return;

        EdgeIteratorState edge = baseGraph.edge(fromIndex, toIndex).setDistance(distance).setFlags(edgeFlags);
        List<EdgeKVStorage.KeyValue> list = way.getTag("key_values", Collections.emptyList());
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
        osmParsers.applyWayTags(way, edge);

        checkDistance(edge);
        if (restrictionData.osmWayIdSet.contains(way.getId())) {
        	restrictionData.edgeIdToOsmWayIdMap.put(edge.getEdge(), way.getId());
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
        if (Double.isInfinite(edgeDistance))
            throw new IllegalStateException("Infinite edge distance should never occur, as we are supposed to limit each distance to the maximum distance we can store, #435");
        else if (edgeDistance > 2_000_000)
            LOGGER.warn("Very long edge detected: " + edge + " dist: " + edgeDistance);
        else if (Math.abs(edgeDistance - geometryDistance) > tolerance)
            throw new IllegalStateException("Suspicious distance for edge: " + edge + " " + edgeDistance + " vs. " + geometryDistance
                    + ", difference: " + (edgeDistance - geometryDistance));
    }

    // coordinateSupplier maps an OSM node ID (as it can be obtained by way.getNodes()) to the coordinates
    // of this node. If elevation is disabled it will be NaN. Returns null if no such OSM
    // node exists.
    public interface CoordinateSupplier {
        GHPoint3D getCoordinate(long osmNodeId);
    }
}
