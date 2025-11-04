package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.buffer.BufferFeature;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Stream;

@Path("buffer")
public class BufferResource {
    private final LocationIndex locationIndex;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;
    private final BooleanEncodedValue roundaboutAccessEnc;
    private final BooleanEncodedValue carAccessEnc;
    private final Graph graph;
    private final GraphHopperConfig config;
    private final DistanceCalculationHelper distanceHelper;
    private final EdgeAngleCalculator angleCalculator;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));

    //region Constants, Enums and Records
    private static final double PROXIMITY_THRESHOLD_METERS = 6.096; // 20 feet
    private static final double INITIAL_SEARCH_RADIUS_DEGREES = 0.0001; // Roughly 11 meters


    enum Direction {
        UNKNOWN,
        NORTH,
        SOUTH,
        EAST,
        WEST,
        NORTHWEST,
        NORTHEAST,
        SOUTHWEST,
        SOUTHEAST,
        BOTH
    }

    /**
     * Represents the result of edge matching logic for the given location.
     * Contains the road name to use, whether to use unnamed fallback logic,
     * and the pre-filtered edges from proximity checks.
     * Eliminates redundancy by making determineEdgeMatchingStrategy() the single decision point.
     */
    public record EdgeMatchingDecision(
        String roadName,                    // The road name to use (null for unnamed)
        boolean useUnnamedFallback,         // Whether to use unnamed edge logic
        List<Integer> preferredEdges        // Pre-filtered edges from proximity check
    ) {
        public EdgeMatchingDecision {
            preferredEdges = preferredEdges != null ? preferredEdges : Collections.emptyList();
        }
    }

    //endregion
    //region Constructor

    @Inject
    public BufferResource(GraphHopperConfig config, GraphHopper graphHopper) {
        this.config = config;
        this.graph = graphHopper.getBaseGraph();
        this.locationIndex = graphHopper.getLocationIndex();
        this.nodeAccess = graph.getNodeAccess();
        this.edgeExplorer = graph.createEdgeExplorer();
        this.distanceHelper = new DistanceCalculationHelper(graph, nodeAccess);
        this.angleCalculator = new EdgeAngleCalculator(graph);

        EncodingManager encodingManager = graphHopper.getEncodingManager();
        this.roundaboutAccessEnc = encodingManager.getBooleanEncodedValue(Roundabout.KEY);
        this.carAccessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key("car"));
    }

    //endregion
    //region Public Endpoint

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("roadName") @Nullable String roadName,
            @QueryParam("thresholdDistance") @NotNull Double thresholdDistance,
            @QueryParam("direction") @DefaultValue("unknown") String direction,
            @QueryParam("queryMultiplier") @DefaultValue(".000075") Double queryMultiplier, // Default of ~8.33 meters in degrees
            @QueryParam("buildUpstream") @DefaultValue("false") Boolean buildUpstream,
            @QueryParam("requireRoadNameMatch") @DefaultValue("false") Boolean requireRoadNameMatch) {

        validateInputParameters(queryMultiplier, requireRoadNameMatch, roadName);

        StopWatch stopWatch = new StopWatch().start();
        Direction directionEnum = Direction.valueOf(direction.toUpperCase());

        EdgeMatchingDecision edgeMatchingDecision = determineEdgeMatchingStrategy(roadName, requireRoadNameMatch, point);
        String edgeRoadName = edgeMatchingDecision.roadName();

        BufferFeature primaryStartFeature = calculatePrimaryStartFeature(point.get().lat, point.get().lon, edgeMatchingDecision, queryMultiplier);
        EdgeIteratorState primaryStartEdgeState = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);

        List<LineString> generatedPaths = new ArrayList<>();
        // Start feature edge is bidirectional. Build both upstream and downstream paths from the primary start feature
        if (isBidirectional(primaryStartEdgeState)) {
            generatedPaths.add(buildCompletePath(primaryStartFeature, edgeRoadName, thresholdDistance, buildUpstream, true));
            generatedPaths.add(buildCompletePath(primaryStartFeature, edgeRoadName, thresholdDistance, buildUpstream, false));
        }
        // Start feature edge is unidirectional. Requires finding sister road.
        else {
            generatedPaths.add(buildCompletePath(primaryStartFeature, edgeRoadName, thresholdDistance, buildUpstream, buildUpstream));
            // Only attempt to find sister road when not using unnamed fallback logic
            if (!edgeMatchingDecision.useUnnamedFallback()) {
                BufferFeature secondaryStartFeature = calculateSecondaryStartFeature(primaryStartFeature, edgeRoadName, .005); // Scale up query box for sister road
                generatedPaths.add(buildCompletePath(secondaryStartFeature, edgeRoadName, thresholdDistance, buildUpstream, buildUpstream));
            }
        }

        List<LineString> filteredLineStrings = filterPathsByDirection(generatedPaths, buildUpstream, directionEnum);
        return createGeoJsonResponse(filteredLineStrings, stopWatch);
    }

    //endregion
    //region Validation

    /**
     * Validates input parameters and throws IllegalArgumentException for invalid values.
     *
     * @param queryMultiplier       base dimension of query box
     * @param requireRoadNameMatch  whether a road name match is required
     * @param roadName              name of road
     */
    private void validateInputParameters(Double queryMultiplier, Boolean requireRoadNameMatch, String roadName) {
        if (queryMultiplier > 1) {
            throw new IllegalArgumentException("Query multiplier is too high.");
        } else if (queryMultiplier <= 0) {
            throw new IllegalArgumentException("Query multiplier cannot be zero or negative.");
        } else if (requireRoadNameMatch && (roadName == null || roadName.isEmpty())) {
            throw new IllegalArgumentException("Road name is required when requireRoadNameMatch is true.");
        }
    }

    //endregion
    //region Edge Discovery

    /**
     * Determines road matching strategy based on proximity.
     * Within 20 feet (~6m), prefers the closest edge regardless of whether it's named or unnamed.
     * Beyond 20 feet, maintains closest road name matching logic.
     *
     * @param roadName              road name
     * @param requireRoadNameMatch  require road name match
     * @param point                 point to use for road name matching
     * @return EdgeMatchingDecision containing decision and pre-filtered edges
     */
    private EdgeMatchingDecision determineEdgeMatchingStrategy(String roadName, Boolean requireRoadNameMatch, GHPointParam point) {

        if (requireRoadNameMatch) {
            return new EdgeMatchingDecision(sanitizeRoadNames(roadName).get(0), false, Collections.emptyList());
        }

        // Use a larger bounding box for initial query, then filter by actual distance
        BBox searchBbox = new BBox(
                point.get().lon - INITIAL_SEARCH_RADIUS_DEGREES, point.get().lon + INITIAL_SEARCH_RADIUS_DEGREES,
                point.get().lat - INITIAL_SEARCH_RADIUS_DEGREES, point.get().lat + INITIAL_SEARCH_RADIUS_DEGREES
        );

        // Separate named and unnamed edges within proximity
        List<Integer> namedEdges = new ArrayList<>();
        List<Integer> unnamedEdges = new ArrayList<>();

        this.locationIndex.query(searchBbox, edgeId -> {
            EdgeIteratorState state = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            double distanceToEdge = distanceHelper.calculateDistanceToEdge(edgeId, point.get().lat, point.get().lon);

            // Only include edges that are within 20 feet
            if (distanceToEdge <= PROXIMITY_THRESHOLD_METERS) {
                List<String> queryRoadNames = getAllRouteNamesFromEdge(state);

                if (!queryRoadNames.stream().allMatch(String::isEmpty)) {
                    namedEdges.add(edgeId);
                } else {
                    unnamedEdges.add(edgeId);
                }
            }
        });

        // Find the closest edge within proximity
        List<Integer> edgesWithinProximity = new ArrayList<>();
        edgesWithinProximity.addAll(namedEdges);
        edgesWithinProximity.addAll(unnamedEdges);
        Integer closestEdge = findClosestEdgeByDistance(edgesWithinProximity, point.get().lat, point.get().lon);

        if (closestEdge != null) {
            EdgeIteratorState state = graph.getEdgeIteratorState(closestEdge, Integer.MIN_VALUE);
            List<String> roadNames = getAllRouteNamesFromEdge(state);

            // If closest edge is named, use road name matching
            if (!roadNames.stream().allMatch(String::isEmpty)) {
                List<String> allRoadNames = namedEdges.stream()
                        .flatMap(edgeId -> getAllRouteNamesFromEdge(graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE)).stream())
                        .distinct()
                        .toList();
                String bestEdgeRoadName = findClosestMatchingRoadName(allRoadNames, roadName);

                // Filter namedEdges to only include edges with the bestEdgeRoadName
                List<Integer> filteredNamedEdges = filterEdgesByRoadName(namedEdges, bestEdgeRoadName);

                return new EdgeMatchingDecision(bestEdgeRoadName, false, filteredNamedEdges);
            }
            // If closest edge is unnamed, use unnamed fallback
            else {
                return new EdgeMatchingDecision(null, true, unnamedEdges);
            }
        }

        // Fallback to existing logic when no edges found in proximity
        if (roadName == null) {
            throw new WebApplicationException("Could not find road name from lat/lon and no road name provided to use as an alternative.");
        }

        return new EdgeMatchingDecision(sanitizeRoadNames(roadName).get(0), false, Collections.emptyList());
    }

    /**
     * Locates the nearest road edge matching a road name (if specified) near a given coordinate.
     * Uses a two-tier search strategy:
     * 1. First attempts to use pre-filtered edges from EdgeMatchingDecision when available
     * 2. Falls back to expanding bounding box search (3 pulses based on queryMultiplier)
     * This approach avoids redundant proximity checks when edges have already been filtered
     * by upstream decision logic.
     *
     * @param startLat         latitude at center of query box
     * @param startLon         longitude at center of query box
     * @param edgeMatchResult  contains pre-filtered edges and road name from decision logic
     * @param queryMultiplier  base dimension multiplier for expanding bbox search
     * @return the closest matching edge to the start coordinates
     */
    private BufferFeature calculatePrimaryStartFeature(Double startLat, Double startLon,
                                                       EdgeMatchingDecision edgeMatchResult,
                                                       Double queryMultiplier) {
        // If we have pre-filtered edges from proximity check, use them first
        if (!edgeMatchResult.preferredEdges().isEmpty()) {
            if (edgeMatchResult.useUnnamedFallback()) {
                return selectClosestUnnamedEdgeToPoint(edgeMatchResult.preferredEdges(), startLat, startLon);
            }
            return selectClosestNamedEdgeToPoint(edgeMatchResult.preferredEdges(), startLat, startLon);
        }

        // Fallback to existing expanding bbox logic for when no proximity edges found
        String roadName = edgeMatchResult.roadName();
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(
                    startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i
            );

            final List<Integer> filteredQueryEdges = queryNamedEdgesInBbox(bbox, roadName);

            if (!filteredQueryEdges.isEmpty()) {
                return selectClosestNamedEdgeToPoint(filteredQueryEdges, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find primary road with that name near the selection.");
    }

    /**
     * Given a buffer feature, finds all nearby edges with a corresponding road name which aren't along
     * the same road segment as the given feature. Uses three expanding 'pulses' based off the queryMultiplier
     * until at least a single matching edge is found.
     *
     * @param primaryStartFeature buffer feature to query off of
     * @param roadName            name of road
     * @param queryMultiplier     base dimension of query box
     * @return buffer feature closest to primary start feature
     */
    private BufferFeature calculateSecondaryStartFeature(BufferFeature primaryStartFeature, String roadName, Double queryMultiplier) {
        double startLat = primaryStartFeature.getPoint().lat;
        double startLon = primaryStartFeature.getPoint().lon;

        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);

        // Scale up query Bbox
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(
                    startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i
            );

            final List<Integer> filteredQueryEdges = queryNamedEdgesInBbox(bbox, roadName);
            final List<Integer> filteredEdgesByDirection = new ArrayList<>();

            // Secondary filter
            for (Integer edge : filteredQueryEdges) {
                EdgeIteratorState tempState = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

                // If both roads are going in different direction, target isn't bidirectional,
                // and target isn't the same edge as primary start feature
                if (tempState.getBaseNode() != state.getAdjNode()
                        && tempState.getAdjNode() != state.getBaseNode()
                        && !isBidirectional(tempState)
                        && !edge.equals(primaryStartFeature.getEdge())) {
                    filteredEdgesByDirection.add(edge);
                }
            }

            if (!filteredEdgesByDirection.isEmpty()) {
                return selectClosestNamedEdgeToPoint(filteredEdgesByDirection, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find secondary road with that name near the selection.");
    }

    /**
     * Filters out all edges within a bbox that don't have a matching road name.
     * If no road name is provided, filters out all edges that don't have a non-empty road name.
     *
     * @param bbox     query zone
     * @param roadName name of road
     * @return all edges within the bbox that meet the road name filter requirements
     */
    private List<Integer> queryNamedEdgesInBbox(BBox bbox, String roadName) {
        final List<Integer> filteredEdgesInBbox = new ArrayList<>();

        this.locationIndex.query(bbox, edgeId -> {
            EdgeIteratorState state = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            List<String> queryRoadNames = getAllRouteNamesFromEdge(state);

            if ((roadName != null && queryRoadNames.stream().anyMatch(x -> x.contains(roadName)))
                    || (roadName == null && !queryRoadNames.stream().allMatch(String::isEmpty))) {
                filteredEdgesInBbox.add(edgeId);
            }
        });

        return filteredEdgesInBbox;
    }

    //endregion
    //region Path Computation

    /**
     * Generate complete path by combining starting edge segment,
     * intermediate path, and final segment to the exact threshold point.
     *
     * @param startFeature      buffer feature to start at
     * @param roadName          name of road
     * @param thresholdDistance maximum distance in meters that the path should reach
     * @param upstreamPath      direction to build path - either along or against road's flow
     * @param upstreamStart     initial 'launch' direction - used only for a bidirectional start
     * @return lineString representing start -> end
     */
    private LineString buildCompletePath(BufferFeature startFeature, String roadName, Double thresholdDistance,
                                         Boolean upstreamPath, Boolean upstreamStart) {
        // Get geometry of starting edge up to the startFeature point
        PointList startingEdgeGeometry = computeStartingEdgeGeometryWithinThreshold(startFeature, upstreamStart, thresholdDistance);
        boolean canReturnStartFeature = canReturnStartFeature(startingEdgeGeometry, startFeature, upstreamStart, thresholdDistance);

        // Get path from the startFeature to the edge that reaches the threshold distance
        BufferFeature featureToThreshold = buildPathToThresholdDistance(startFeature, thresholdDistance, roadName,
                upstreamPath, upstreamStart, canReturnStartFeature);

        // Get geometry of final segment from featureToThreshold to threshold point
        PointList finalSegmentToThreshold = computeFinalSegmentToThreshold(startFeature, thresholdDistance,
                featureToThreshold, canReturnStartFeature);

        List<Coordinate> coordinates = new ArrayList<>();

        // Add start feature points
        for (GHPoint point : startingEdgeGeometry) {
            coordinates.add(new Coordinate(point.getLon(), point.getLat()));
        }

        // Add to-threshold points unless canReturnStartFeature is false as they exceed the threshold distance.
        if(canReturnStartFeature)
        {
            for (GHPoint point : featureToThreshold.getPath()) {
                coordinates.add(new Coordinate(point.getLon(), point.getLat()));
            }
        }

        // Add final segment points
        for (GHPoint point : finalSegmentToThreshold) {
            Coordinate coordinate = new Coordinate(point.getLon(), point.getLat());
            if (!coordinates.contains(coordinate)) {
                coordinates.add(coordinate);
            }
        }

        // Reverse final path when building upstream
        if (upstreamPath) {
            Collections.reverse(coordinates);
        }

        // LineString must have at least 2 points
        if (coordinates.size() <= 1) {
            throw new WebApplicationException("Threshold distance is too short to construct a valid path.");
        }

        return geometryFactory.createLineString(coordinates.toArray(Coordinate[]::new));
    }

    /**
     * Cycles through all nearby, matching roads, and selects the edge which is closest to the given lat/lon.
     * This should only be used when the edges are known to be named.
     *
     * @param edgeList all nearby edges with proper road name
     * @param startLat latitude at center of query box
     * @param startLon longitude at center of query box
     * @param isPillarOnly determines if pillar or tower points are attempted. Always check pillar points first
     *                     but if no pillar points are found, then check tower points. My understanding is that
     *                     there will always be tower points. (docs/core/low-level-api.md#what-are-pillar-and-tower-nodes)
     * @return BufferFeature representing the closest edge
     */
    private BufferFeature selectClosestNamedEdgeToPoint(List<Integer> edgeList, Double startLat, Double startLon, Boolean isPillarOnly) {
        double lowestDistance = Double.MAX_VALUE;
        GHPoint3D nearestPoint = null;
        Integer nearestEdge = null;

        for (Integer edge : edgeList) {
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
            PointList pointList = isPillarOnly ?
                    state.fetchWayGeometry(FetchMode.PILLAR_ONLY)
                    : state.fetchWayGeometry(FetchMode.TOWER_ONLY);

            for (GHPoint3D point : pointList) {
                // the purpose of epsilon is to prevent rounding problems in determining equality.
                double epsilon = 0.000001d;
                if (Math.abs(startLat - point.lat) < epsilon && Math.abs(startLon - point.lon) < epsilon) {
                    // Values are considered equal
                    continue;
                }
                double dist = distanceHelper.calculatePointDistance(startLat, startLon, point.lat, point.lon);

                if (dist < lowestDistance) {
                    lowestDistance = dist;
                    nearestPoint = point;
                    nearestEdge = edge;
                }
            }
        }

        // In cases where zero PILLAR points have been detected, we must detect TOWER points because we must return
        // non-null nearestEdge and non-null nearestPoint
        if(nearestEdge == null && isPillarOnly)
        {
            return selectClosestNamedEdgeToPoint(edgeList, startLat, startLon, false);
        }

        return new BufferFeature(nearestEdge, nearestPoint, 0.0);
    }

    private BufferFeature selectClosestNamedEdgeToPoint(List<Integer> edgeList, Double startLat, Double startLon) {
        return selectClosestNamedEdgeToPoint(edgeList, startLat, startLon, true);
    }

    /**
     * Selects the best edge based on distance from a list of nearby unnamed edges.
     * This should only be used when the edges are known to be unnamed.
     *
     * @param edges list of edge IDs to select from
     * @param lat latitude of the target point
     * @param lon longitude of the target point
     * @return BufferFeature representing the closest edge
     */
    private BufferFeature selectClosestUnnamedEdgeToPoint(List<Integer> edges, double lat, double lon) {
        return edges.stream()
                .min((edge1, edge2) -> {
                    double dist1 = distanceHelper.calculateDistanceToEdge(edge1, lat, lon);
                    double dist2 = distanceHelper.calculateDistanceToEdge(edge2, lat, lon);
                    return Double.compare(dist1, dist2);
                })
                .map(edge -> {
                    GHPoint3D closestPoint = distanceHelper.findClosestPointOnEdge(edge, lat, lon);
                    return new BufferFeature(edge, closestPoint, 0.0);
                })
                .orElseThrow(() -> new WebApplicationException("No suitable edge found"));
    }

    /**
     * Traverses along the road network from a starting feature, building a complete path
     * until reaching the specified distance threshold. Returns both the final edge at the
     * threshold and the accumulated path geometry traversed to reach it.
     * <p>
     * The method follows road connectivity rules, preferring edges with matching road names
     * and handling special cases like roundabouts and Michigan lefts. The accumulated path
     * includes all intermediate geometry points between nodes.
     *
     * @param startFeature      buffer feature to start traversal from
     * @param thresholdDistance maximum distance in meters to traverse
     * @param roadName          name of road to follow (null for unnamed road logic)
     * @param upstreamPath      direction to build path - either along or against road's flow
     * @param upstreamStart     initial 'launch' direction - used only for a bidirectional start
     * @param isOkayToReturnStartFeature determines if returning the start feature is acceptable
     *                                   when threshold constraints cannot be met
     *
     * @return BufferFeature containing the edge at threshold distance and the complete
     *         path geometry traversed from start to that edge
     */
    private BufferFeature buildPathToThresholdDistance(final BufferFeature startFeature, Double thresholdDistance,
                                                       String roadName, Boolean upstreamPath, Boolean upstreamStart,
                                                       Boolean isOkayToReturnStartFeature) {
        List<Integer> usedEdges = new ArrayList<>() {
            {
                add(startFeature.getEdge());
            }
        };

        EdgeIteratorState currentState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        String previousRoadName = currentState.getName();
        int currentNode = upstreamStart ? currentState.getBaseNode() : currentState.getAdjNode();
        PointList path = new PointList();

        // Check starting edge
        double currentDistance = distanceHelper.calculatePointDistance(
                startFeature.getPoint().getLat(), startFeature.getPoint().getLon(),
                nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode));
        double previousDistance = 0.0;
        Integer currentEdge = -1;
        // Create previous values to use in case we don't meet the threshold
        int previousEdge = currentEdge;
        int originalNode = currentNode;

        if (currentDistance >= thresholdDistance && isOkayToReturnStartFeature) {
            return startFeature;
        }

        boolean isNodeOriginalNode = true;
        // If the threshold is exceeded, we can stop looping only if it isOkayToReturnStartFeature OR the currentNode is not the originalNode
        while (currentDistance < thresholdDistance || ( !isOkayToReturnStartFeature && isNodeOriginalNode )) {
            EdgeIterator iterator = edgeExplorer.setBaseNode(currentNode);
            List<Integer> potentialEdges = new ArrayList<>();
            List<Integer> potentialRoundaboutEdges = new ArrayList<>();
            List<Integer> potentialRoundaboutEdgesWithoutName = new ArrayList<>();
            List<Integer> potentialUnnamedEdges = new ArrayList<>();
            currentEdge = -1;

            while (iterator.next()) {
                List<String> roadNames = getAllRouteNamesFromEdge(iterator);
                Integer tempEdge = iterator.getEdge();
                EdgeIteratorState tempState = graph.getEdgeIteratorState(tempEdge, Integer.MIN_VALUE);

                // Temp hasn't been used before and has proper flow
                if (!usedEdges.contains(tempEdge) && hasProperFlow(currentState, tempState, upstreamPath)) {

                    // Temp has proper road name, isn't part of a roundabout, and bidirectional.
                    // Higher priority escape.
                    if (roadName != null && roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && !tempState.get(this.roundaboutAccessEnc)
                            && isBidirectional(tempState)) {
                        currentEdge = tempEdge;
                        usedEdges.add(tempEdge);
                        break;
                    }

                    // Temp has proper road name and isn't part of a roundabout. Lower priority escape.
                    else if (roadName != null && roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && !tempState.get(this.roundaboutAccessEnc)) {
                        potentialEdges.add(tempEdge);
                    }

                    // Temp has proper road name and is part of a roundabout. Higher entry priority.
                    else if (roadName != null && roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && tempState.get(this.roundaboutAccessEnc)) {
                        potentialRoundaboutEdges.add(tempEdge);
                    }

                    // Handle unnamed edge continuation when roadName is null
                    else if (roadName == null && !tempState.get(this.roundaboutAccessEnc)) {
                        String currentEdgeRoadName = tempState.getName();
                        boolean currentIsBlank = currentEdgeRoadName == null || currentEdgeRoadName.isEmpty();
                        boolean previousIsBlank = previousRoadName == null || previousRoadName.isEmpty();
                        boolean matchesPreviousEdgeName = (currentIsBlank && previousIsBlank)
                                || (currentEdgeRoadName != null && currentEdgeRoadName.equals(previousRoadName));

                        if (matchesPreviousEdgeName) {
                            if (!currentIsBlank) {
                                currentEdge = tempEdge;
                                usedEdges.add(tempEdge);
                                break;
                            }
                            potentialUnnamedEdges.add(tempEdge);
                        } else if (previousIsBlank) {
                            potentialEdges.add(tempEdge);
                        }
                    }

                    // Temp is part of a roundabout. Lower entry priority.
                    else if (tempState.get(this.roundaboutAccessEnc)) {
                        potentialRoundaboutEdgesWithoutName.add(tempEdge);
                    }
                }
            }

            // No bidirectional edge found. Choose from potential edge lists.
            if (!potentialUnnamedEdges.isEmpty()) {
                currentEdge = angleCalculator.selectStraightestEdge(potentialUnnamedEdges, currentState, currentNode);
                usedEdges.add(currentEdge);
            }
            else if (currentEdge == -1) {
                if (!potentialEdges.isEmpty()) {
                    // The Michigan Left
                    if (potentialEdges.size() > 1) {
                        // This logic is not infallible as it stands, but there's no clear alternative.
                        // In the case of a Michigan left, choose the edge that's further away
                        EdgeIteratorState tempState = graph.getEdgeIteratorState(potentialEdges.get(0),
                                Integer.MIN_VALUE);
                        double dist1 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                - nodeAccess.getLon(tempState.getBaseNode()));
                        tempState = graph.getEdgeIteratorState(potentialEdges.get(potentialEdges.size() - 1),
                                Integer.MIN_VALUE);
                        double dist2 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                - nodeAccess.getLon(tempState.getBaseNode()));

                        if (dist1 > dist2) {
                            currentEdge = potentialEdges.get(0);
                            usedEdges.add(currentEdge);
                        } else {
                            currentEdge = potentialEdges.get(potentialEdges.size() - 1);
                            usedEdges.add(currentEdge);
                        }
                    }
                    // Only one possible route
                    else {
                        currentEdge = potentialEdges.get(0);
                        usedEdges.add(currentEdge);
                    }
                } else if (!potentialRoundaboutEdges.isEmpty()) {
                    currentEdge = potentialRoundaboutEdges.get(0);
                    usedEdges.add(currentEdge);
                } else if (!potentialRoundaboutEdgesWithoutName.isEmpty()) {
                    currentEdge = potentialRoundaboutEdgesWithoutName.get(0);
                    usedEdges.add(currentEdge);
                } else {
                    // Instead of throwing an exception, we break the loop and return the
                    // path up to the current edge even though we haven't met the threshold.
                    // Assign current edge & node to previous and break the loop.
                    currentEdge = previousEdge;
                    break;
                }
            }

            // Calculate new distance
            int otherNode = graph.getOtherNode(currentEdge, currentNode);
            previousDistance = currentDistance;
            currentDistance += distanceHelper.calculatePointDistance(nodeAccess.getLat(currentNode),
                    nodeAccess.getLon(currentNode), nodeAccess.getLat(otherNode), nodeAccess.getLon(otherNode));

            // Break before moving to next node in case hitting the threshold
            isNodeOriginalNode = currentNode == originalNode;
            if (currentDistance >= thresholdDistance && ( isOkayToReturnStartFeature || !isNodeOriginalNode )) {
                break;
            }

            EdgeIteratorState state = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
            previousRoadName = state.getName();
            PointList wayGeometry = state.fetchWayGeometry(FetchMode.PILLAR_ONLY);

            // Reverse path if segment is flipped
            if (state.getAdjNode() == currentNode) {
                if (!wayGeometry.isEmpty()) {
                    wayGeometry.reverse();
                }
            }
            // Add current node first
            path.add(new GHPoint(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode)));
            path.add(wayGeometry);

            // Move to next node
            currentNode = otherNode;
            currentState = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
            // Update previous edge
            previousEdge = currentEdge;
        }

        return new BufferFeature(currentEdge, currentNode,
                new GHPoint3D(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode), 0),
                previousDistance, path);
    }

    /**
     * Extracts the geometry points along the final edge segment from the end feature
     * up to (but not exceeding) the specified distance threshold. This creates the
     * final portion of the complete path that connects to the exact threshold point.
     * <p>
     * Returns an empty PointList when the start and end features are on the same edge,
     * since no intermediate geometry is needed in that case.
     *
     * @param startFeature      original starting buffer feature used for distance calculations
     * @param thresholdDistance maximum distance in meters from the start point
     * @param endFeature        buffer feature representing the edge that reaches/exceeds the threshold
     * @param isOkayToReturnZeroPoints whether an empty result is acceptable when constraints cannot be met
     * @return PointList containing geometry points of the final edge segment within the threshold,
     *         or empty list if start and end are on the same edge
     */
    private PointList computeFinalSegmentToThreshold(BufferFeature startFeature, Double thresholdDistance,
                                                     BufferFeature endFeature, Boolean isOkayToReturnZeroPoints) {
        if(endFeature.getEdge() < 0)
        {
            return new PointList();
        }
        EdgeIteratorState finalState = graph.getEdgeIteratorState(endFeature.getEdge(), Integer.MIN_VALUE);
        PointList pointList = finalState.fetchWayGeometry(FetchMode.PILLAR_ONLY);

        // It is possible that the finalState.fetchWayGeometry(FetchMode.xxxx) would
        // only contain TOWER points and not PILLAR points.
        // When this happens, filtering by FetchMode.PILLAR_ONLY will return an empty
        // PointList.
        if (pointList.isEmpty()) {
            if(isOkayToReturnZeroPoints)
            {
                return pointList;
            }
            pointList = new PointList();
            PointList tempPointList = finalState.fetchWayGeometry(FetchMode.TOWER_ONLY);
            for (GHPoint3D point : tempPointList) {
                if (startFeature.getPoint().equals(point))
                {
                    continue;
                }
                pointList.add(point);
            }
        }

        // When the buffer is only as wide as a single edge, truncate one half of the
        // segment
        if (startFeature.getEdge().equals(endFeature.getEdge())) {
            return new PointList();
        }

        // Reverse geometry when starting at adjacent node
        if (finalState.getAdjNode() == endFeature.getNode()) {
            pointList.reverse();
        }

        Double currentDistance = endFeature.getDistance();
        GHPoint3D previousPoint = pointList.get(0);

        PointList resultPointList = truncatePathAtThreshold(thresholdDistance, pointList, currentDistance, previousPoint);

        // If we must return a point, and we do not have one then we need to create a point.  We can create a point because the
        // line segment connecting any two consecutive points of a path necessarily lies entirely on the road.
        if(!isOkayToReturnZeroPoints && resultPointList.isEmpty())
        {
            GHPoint3D beyondThresholdPoint = pointList.get(0);
            double totalDist = distanceHelper.calculatePointDistance(startFeature.getPoint().lat, startFeature.getPoint().lon, beyondThresholdPoint.lat, beyondThresholdPoint.lon);
            double resultLat  = startFeature.getPoint().lat + (beyondThresholdPoint.lat - startFeature.getPoint().lat) * (thresholdDistance/totalDist);
            double resultLon = startFeature.getPoint().lon + (beyondThresholdPoint.lon - startFeature.getPoint().lon) * (thresholdDistance/totalDist);
            GHPoint3D resultPoint = new GHPoint3D(resultLat, resultLon, startFeature.getPoint().ele);
            resultPointList.add(resultPoint);
        }
        return resultPointList;
    }

    /**
     * Extracts the geometry points from the starting edge in the specified direction,
     * filtered to include only those points that remain within the distance threshold.
     * The geometry is truncated at the start feature point based on the launch direction,
     * then further limited to points that don't exceed the threshold distance.
     *
     * @param startFeature  original starting buffer feature
     * @param upstreamStart initial 'launch' direction - true for upstream, false for downstream
     * @param thresholdDistance maximum distance in meters that the path should reach
     * @return PointList containing geometry points of the starting edge within the threshold
     */
    private PointList computeStartingEdgeGeometryWithinThreshold(BufferFeature startFeature, Boolean upstreamStart, Double thresholdDistance) {
        EdgeIteratorState startState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        PointList pathList = startState.fetchWayGeometry(FetchMode.ALL);
        PointList tempList = new PointList();

        // Truncate before startPoint
        if (upstreamStart) {
            for (GHPoint3D point : pathList) {
                tempList.add(point);
                if (startFeature.getPoint().equals(point)) {
                    break;
                }
            }
        }
        // Truncate after startPoint
        else {
            boolean pastPoint = false;
            for (GHPoint3D point : pathList) {
                if (startFeature.getPoint().equals(point)) {
                    pastPoint = true;
                }
                if (pastPoint) {
                    tempList.add(point);
                }
            }
        }

        // Doesn't matter if bidirectional since upstreamStart will always go toward adjacent node
        if (upstreamStart) {
            tempList.reverse();
        }

        double currentDistance = 0.0;
        GHPoint3D previousPoint = tempList.get(0);

        return truncatePathAtThreshold(thresholdDistance, tempList, currentDistance, previousPoint);
    }

    /**
     * Builds a truncated path from a source point list that stays within the distance threshold.
     * Iterates through the source points, accumulating distance from the previous point, and
     * stops adding points when the cumulative distance would exceed the threshold.
     *
     * @param thresholdDistance maximum cumulative distance allowed
     * @param sourcePoints      original point list to process
     * @param currentDistance   starting cumulative distance (usually 0 or distance already traveled)
     * @param previousPoint     reference point for calculating distance to first point in sourcePoints
     * @return PointList containing only points that fit within the threshold distance
     */
    private PointList truncatePathAtThreshold(Double thresholdDistance, PointList sourcePoints, double currentDistance, GHPoint3D previousPoint) {
        PointList truncatedPath = new PointList();
        for (GHPoint3D currentPoint : sourcePoints) {
            // Skip invalid coordinates (likely default/uninitialized points)
            if (currentPoint.lat == 0 && currentPoint.lon == 0) {
                continue;
            }

            // Calculate distance from previous point to current point
            double segmentDistance = distanceHelper.calculatePointDistance(
                    currentPoint.getLat(), currentPoint.getLon(),
                    previousPoint.getLat(), previousPoint.getLon());

            currentDistance += segmentDistance;            
            if (currentDistance >= thresholdDistance) {
                break;
            }

            truncatedPath.add(currentPoint);
            previousPoint = currentPoint;
        }

        return truncatedPath;
    }

    /**
     * Finds the closest edge by distance from a list of edges.
     *
     * @param edges list of edge IDs to search
     * @param lat latitude of the target point
     * @param lon longitude of the target point
     * @return closest edge ID, or null if no edges found
     */
    private Integer findClosestEdgeByDistance(List<Integer> edges, double lat, double lon) {
        double minDistance = Double.MAX_VALUE;
        Integer closestEdge = null;

        for (Integer edge : edges) {
            double distance = distanceHelper.calculateDistanceToEdge(edge, lat, lon);
            if (distance < minDistance) {
                minDistance = distance;
                closestEdge = edge;
            }
        }

        return closestEdge;
    }

    //endregion
    //region Direction Filtering

    private List<LineString> filterPathsByDirection(List<LineString> lineStrings, Boolean buildUpstream, Direction directionEnum) {
        if (lineStrings == null || lineStrings.isEmpty()) {
            return Collections.emptyList();
        }
        Point furthestPointOfFirstPath = buildUpstream ? lineStrings.get(0).getStartPoint() : lineStrings.get(0).getEndPoint();
        Point furthestPointOfSecondPath = buildUpstream ? lineStrings.get(lineStrings.size() - 1).getStartPoint() : lineStrings.get(lineStrings.size() - 1).getEndPoint();

        switch (directionEnum) {
            case NORTH:
                return furthestPointOfFirstPath.getY() < furthestPointOfSecondPath.getY()
                        ? Collections.singletonList(lineStrings.get(0))
                        : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
            case SOUTH:
                return furthestPointOfFirstPath.getY() > furthestPointOfSecondPath.getY()
                        ? Collections.singletonList(lineStrings.get(0))
                        : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
            case EAST:
                return furthestPointOfFirstPath.getX() < furthestPointOfSecondPath.getX()
                        ? Collections.singletonList(lineStrings.get(0))
                        : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
            case WEST:
                return furthestPointOfFirstPath.getX() > furthestPointOfSecondPath.getX()
                        ? Collections.singletonList(lineStrings.get(0))
                        : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
            case BOTH, UNKNOWN:
                return lineStrings;
            default:
                break;
        }

        // For non-cardinal directions, use bearing calculation
        // Splits the circle into two halves and checks if the angle is within the specified half.
        // For instance, if the direction is NorthEast but the angle in question is NNW (North by Northwest),
        // this would return true because NNW is closer to NE than it is to the opposite, SW.
        int bearing = (int) Math.round(AngleCalc.ANGLE_CALC.calcAzimuth(furthestPointOfFirstPath.getY(), furthestPointOfFirstPath.getX(), furthestPointOfSecondPath.getY(),furthestPointOfSecondPath.getX()));
        // In a case like if start point is in a hairpin turn, it might be the case that NEITHER lineString looks good
        // or that BOTH lineStrings look good.  By taking the bearing from one terminal to the other, we have a better
        // chance to be correct than if we check the bearing of one lineString.
        boolean isFirstLineStringBetter = false;
        int DUE_NORTHEAST = 45;
        int DUE_SOUTHEAST = 135;
        int DUE_SOUTHWEST = 225;
        int DUE_NORTHWEST = 315;

        switch (directionEnum) {
            case NORTHEAST:
                isFirstLineStringBetter = bearing >= DUE_NORTHWEST || bearing <= DUE_SOUTHEAST;
                break;
            case SOUTHWEST:
                isFirstLineStringBetter = bearing > DUE_SOUTHEAST && bearing < DUE_NORTHWEST;
                break;
            case NORTHWEST:
                isFirstLineStringBetter = bearing >= DUE_SOUTHWEST || bearing <= DUE_NORTHEAST;
                break;
            case SOUTHEAST:
                isFirstLineStringBetter = bearing > DUE_NORTHEAST && bearing < DUE_SOUTHWEST;
                break;
            default:
                break;
        }

        return isFirstLineStringBetter
                ? Collections.singletonList(lineStrings.get(0))
                : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
    }

    //endregion
    //region Road Name Helpers

    /**
     * Combines the StreetName and StreetRef from an EdgeIteratorState. Each list can potentially
     * include different route names so we need to combine both lists.
     * I.e. streetNames contains "Purple Heart Trl" while streetRef contains "I 80"
     *
     * @param state the edge iterator state to fetch from
     * @return list of road names from street name and ref
     */
    private List<String> getAllRouteNamesFromEdge(EdgeIteratorState state) {
        List<String> streetNames = sanitizeRoadNames(state.getName());
        List<String> streetRef = sanitizeRoadNames((String) state.getValue("street_ref"));

        return Stream.concat(streetNames.stream(), streetRef.stream()).toList();
    }

    /**
     * Splits a comma-separated list of road names, then removes any spaces, casing, and dashes.
     *
     * @param roadNames comma-separated list of road names
     * @return list of split road names
     */
    private List<String> sanitizeRoadNames(String roadNames) {
        // Return empty list if roadNames is null
        if (roadNames == null) {
            return new ArrayList<>();
        }

        List<String> separatedNames = Arrays.asList(roadNames.split(","));
        // Replace any empty spaces or dashes from the road
        separatedNames.replaceAll(s -> s.replace(" ", "").replace("-", "").toLowerCase());

        return separatedNames;
    }

    /**
     * Calculates the length of the longest common subsequence (LCS) between two strings.
     * Used when finding the best edge by comparing its road name to the provided road name.
     *
     * @param str1 first string
     * @param str2 second string
     * @return length of the longest common subsequence
     */
    private int calculateLCSLength(String str1, String str2) {
        // Create a 2D array to store the lengths of longest common subsequences for substrings of str1 and str2
        int[][] lcsTable = new int[str1.length() + 1][str2.length() + 1];

        // Iterate through each character of both strings
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                // If characters match, increment the length of the LCS by 1
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    lcsTable[i][j] = lcsTable[i - 1][j - 1] + 1;
                }
                // Otherwise, take the maximum LCS length from the top or left cell
                else {
                    lcsTable[i][j] = Math.max(lcsTable[i - 1][j], lcsTable[i][j - 1]);
                }
            }
        }

        // Return the length of the longest common subsequence
        return lcsTable[str1.length()][str2.length()];
    }

    /**
     * Finds the closest matching road name using LCS algorithm.
     *
     * @param roadNames list of available road names from the edge
     * @param targetRoadName target road name to match against
     * @return best matching road name
     */
    private String findClosestMatchingRoadName(List<String> roadNames, String targetRoadName) {
        if (targetRoadName != null) {
            // Use LCS to find best matching road name
            return roadNames.stream()
                    .max((name1, name2) -> Integer.compare(
                            calculateLCSLength(targetRoadName, name1),
                            calculateLCSLength(targetRoadName, name2)))
                    .orElse(roadNames.get(0));
        }
        return roadNames.stream().filter(name -> !name.isEmpty()).findFirst().orElse(null);
    }

    /**
     * Filters the given list of edges, returning only those that match the specified road name.
     * Uses the same sanitization and matching logic as the existing road name comparison.
     *
     * @param edges list of edge IDs to filter
     * @param roadName road name to match (should already be the best matching name)
     * @return filtered list of edge IDs containing only edges with the specified road name
     */
    private List<Integer> filterEdgesByRoadName(List<Integer> edges, String roadName) {
        if (roadName == null || roadName.isEmpty()) {
            return edges; // Return all edges if no road name specified
        }

        List<Integer> filteredEdges = new ArrayList<>();
        String sanitizedTargetName = sanitizeRoadNames(roadName).get(0);

        for (Integer edge : edges) {
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
            List<String> edgeRoadNames = getAllRouteNamesFromEdge(state);

            // Check if any of the edge's road names match the target road name
            boolean hasMatchingName = edgeRoadNames.stream()
                    .anyMatch(edgeName -> edgeName.equals(sanitizedTargetName));

            if (hasMatchingName) {
                filteredEdges.add(edge);
            }
        }

        return filteredEdges;
    }

    //endregion
    //region Utility Methods

    /**
     * Determines whether the start feature can be returned based on edge geometry and distance constraints.
     * Returns true if the starting edge has multiple geometry points, or if it has only one point and
     * the distance to the target node is within the threshold.
     *
     * @param startingEdgeGeometry  the geometry of the starting edge
     * @param startFeature         the buffer feature to start at
     * @param isUpstream        initial 'launch' direction
     * @param thresholdDistance    maximum distance in meters
     * @return true if it's okay to return the start feature, false otherwise
     */
    private boolean canReturnStartFeature(PointList startingEdgeGeometry, BufferFeature startFeature,
                                           Boolean isUpstream, Double thresholdDistance) {
        // Multiple geometry points means we can safely return the start feature
        if (startingEdgeGeometry.size() > 1) {
            return true;
        }

        // Single point requires distance validation against the threshold distance
        // 1. If the start feature is within the threshold distance of the nearest node, we can return it
        // 2. Otherwise, we cannot return it as it would exceed the threshold
        EdgeIteratorState startEdgeState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        int startNode = isUpstream ? startEdgeState.getBaseNode() : startEdgeState.getAdjNode();
        double startDistance = distanceHelper.calculatePointDistance(
                startFeature.getPoint().getLat(), startFeature.getPoint().getLon(),
                nodeAccess.getLat(startNode), nodeAccess.getLon(startNode));

        return startDistance < thresholdDistance;
    }

    /**
     * Checks if the next edge is going the same direction as the current edge. Prioritizes bidirectional
     * roads to deal with a road where one-ways converge (e.g. -<)
     *
     * @param currentState current edge
     * @param tempState    edge to test
     * @param upstreamPath direction to build path - either along or against road's
     *                     flow
     * @return true if along the flow, false if roads converge
     */
    private Boolean hasProperFlow(EdgeIteratorState currentState, EdgeIteratorState tempState, Boolean upstreamPath) {
        // Going into a bidirectional road always has proper flow
        if (isBidirectional(tempState)) {
            return true;
        } else {
            // Coming from a bidirectional road means either its base or adjacent could
            // match
            if (isBidirectional(currentState)) {
                // For upstream, adjacent node must match
                if (upstreamPath) {
                    return tempState.getAdjNode() == currentState.getBaseNode()
                            || tempState.getAdjNode() == currentState.getAdjNode();
                }
                // For downstream, base node must match
                else {
                    return tempState.getBaseNode() == currentState.getBaseNode()
                            || tempState.getBaseNode() == currentState.getAdjNode();
                }
            }
            // Coming in from a unidirectional road means the opposite type node must match
            // the tempState's node
            else {
                // For upstream, adjacent node must match
                if (upstreamPath) {
                    return tempState.getAdjNode() == currentState.getBaseNode();
                }
                // For downstream, base node must match
                else {
                    return tempState.getBaseNode() == currentState.getAdjNode();
                }
            }
        }
    }

    /**
     * Checks if the road has access flags going in both directions.
     *
     * @param state edge under question
     * @return true if road is bidirectional
     */
    private Boolean isBidirectional(EdgeIteratorState state) {
        return state.get(this.carAccessEnc) && state.getReverse(this.carAccessEnc);
    }

    /**
     * Formats a list of lineStrings as a geoJson
     */
    private Response createGeoJsonResponse(List<LineString> lineStrings, StopWatch sw) {
        sw.stop();

        List<JsonFeature> features = new ArrayList<>();
        for (LineString lineString : lineStrings) {
            JsonFeature feature = new JsonFeature();
            feature.setGeometry(lineString);
            features.add(feature);
        }

        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("type", "FeatureCollection");
        json.putPOJO("copyrights", config.getCopyrights());
        json.putPOJO("features", features);
        return Response.ok(json).header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }

    //endregion
}
