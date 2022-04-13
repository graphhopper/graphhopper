package com.graphhopper.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint3D;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("buffer")
public class BufferResource {
    private static final Logger logger = LoggerFactory.getLogger(BufferResource.class);

    private final LocationIndex locationIndex;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;
    private final FlagEncoder flagEncoder;
    private final Graph graph;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));

    @Inject
    public BufferResource(GraphHopper graphHopper) {
        this.graph = graphHopper.getGraphHopperStorage().getBaseGraph();
        this.locationIndex = graphHopper.getLocationIndex();
        this.nodeAccess = graph.getNodeAccess();
        this.edgeExplorer = graph.createEdgeExplorer();

        EncodingManager encodingManager = graphHopper.getEncodingManager();
        this.flagEncoder = encodingManager.getEncoder("car");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("roadName") @NotNull String roadName,
            @QueryParam("threshholdDistance") @NotNull Double threshholdDistance,
            @QueryParam("queryMultiplier") @DefaultValue(".01") Double queryMultiplier,
            @QueryParam("upstream") @DefaultValue("false") Boolean upstream) {
        if (queryMultiplier > 1) {
            throw new IllegalArgumentException("Query multiplier is too high.");
        } else if (queryMultiplier <= 0) {
            throw new IllegalArgumentException("Query multiplier cannot be zero or negative.");
        }

        StopWatch sw = new StopWatch().start();

        BufferFeature primaryStartFeature = calculatePrimaryStartFeature(point.get().lat, point.get().lon, roadName,
                queryMultiplier);
        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);
        List<Point> points = new ArrayList<Point>();

        // Start feature edge is bidirectional. Simple
        if (isBidirectional(state)) {
            points.addAll(computeBufferSegment(primaryStartFeature, roadName, threshholdDistance, upstream, true));
            points.addAll(computeBufferSegment(primaryStartFeature, roadName, threshholdDistance, upstream, false));
        }
        // Start feature edge is unidirectional. Requires finding sister road
        else {
            BufferFeature secondaryStartFeature = calculateSecondaryStartFeature(primaryStartFeature, roadName, .005);
            points.addAll(computeBufferSegment(primaryStartFeature, roadName, threshholdDistance, upstream, upstream));
            points.addAll(
                    computeBufferSegment(secondaryStartFeature, roadName, threshholdDistance, upstream, upstream));
        }

        return createGeoJsonResponse(points, sw);
    }

    private BufferFeature calculatePrimaryStartFeature(Double startLat, Double startLon, String roadName,
            Double queryMultiplier) {
        // Scale up query Bbox
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i);

            final List<Integer> filteredQueryEdges = queryBbox(bbox, roadName);

            if (filteredQueryEdges.size() > 0) {
                return computeStartFeature(filteredQueryEdges, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find road with that name near the selection.");
    }

    private BufferFeature calculateSecondaryStartFeature(BufferFeature primaryStartFeature, String roadName,
            Double queryMultiplier) {
        Double startLat = primaryStartFeature.getPoint().lat;
        Double startLon = primaryStartFeature.getPoint().lon;

        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);

        // Scale up query Bbox
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i);

            final List<Integer> filteredQueryEdges = queryBbox(bbox, roadName);
            final List<Integer> filteredEdgesByDirection = new ArrayList<Integer>();

            // Secondary filter
            for (Integer edge : filteredQueryEdges) {
                EdgeIteratorState tempState = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

                // If both roads are going in different direction, target isn't bidirectional,
                // and target isn't the same edge as primary start feature
                if (tempState.getBaseNode() != state.getAdjNode() && tempState.getAdjNode() != state.getBaseNode()
                        && !isBidirectional(tempState) && !edge.equals(primaryStartFeature.getEdge())) {
                    filteredEdgesByDirection.add(edge);
                }
            }

            if (filteredEdgesByDirection.size() > 0) {
                return computeStartFeature(filteredEdgesByDirection, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find road with that name near the selection.");
    }

    private List<Integer> queryBbox(BBox bbox, String roadName) {
        final List<Integer> filteredEdgesInBbox = new ArrayList<Integer>();

        this.locationIndex.query(bbox, new LocationIndex.Visitor() {
            @Override
            public void onEdge(int edgeId) {
                EdgeIteratorState state = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);

                // Roads sometimes have multiple names delineated by a comma
                String[] queryRoadNames = sanitizeRoadNames(state.getName());

                if (Arrays.stream(queryRoadNames).anyMatch(roadName::equals)) {
                    filteredEdgesInBbox.add(edgeId);
                }
            };
        });

        return filteredEdgesInBbox;
    }

    private List<Point> computeBufferSegment(BufferFeature startFeature, String roadName, Double threshholdDistance,
            Boolean upstreamPath, Boolean upstreamStart) {
        BufferFeature edgeAtThreshhold = computeEdgeAtDistanceThreshhold(startFeature, threshholdDistance, roadName,
                upstreamPath, upstreamStart);
        GHPoint3D pointAtThreshhold = computePointAtDistanceThreshhold(startFeature, threshholdDistance,
                edgeAtThreshhold, upstreamPath, upstreamStart);

        List<Point> points = new ArrayList<Point>() {
            {
                add(geometryFactory
                        .createPoint(new Coordinate(pointAtThreshhold.getLon(), pointAtThreshhold.getLat())));
                add(geometryFactory.createPoint(
                        new Coordinate(startFeature.getPoint().getLon(), startFeature.getPoint().getLat())));
            }
        };

        return points;
    }

    private String[] sanitizeRoadNames(String roadNames) {
        String[] separatedNames = roadNames.split(",");

        // TODO: Add in removal of dashes, casing, and road directionality (i.e. E US-80
        // -> us80)
        for (int i = 0; i < separatedNames.length; i++) {
            separatedNames[i] = separatedNames[i].trim();
        }

        return separatedNames;
    }

    private BufferFeature computeStartFeature(List<Integer> edgeList, Double startLat, Double startLon) {
        Double lowestDistance = Double.MAX_VALUE;
        GHPoint3D nearestPoint = null;
        Integer nearestEdge = null;

        for (Integer edge : edgeList) {
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

            PointList pointList = state.fetchWayGeometry(FetchMode.ALL);

            for (GHPoint3D point : pointList) {
                Double dist = DistancePlaneProjection.DIST_PLANE.calcDist(startLat, startLon, point.lat, point.lon);

                if (dist < lowestDistance) {
                    lowestDistance = dist;
                    nearestPoint = point;
                    nearestEdge = edge;
                }
                ;
            }
        }

        return new BufferFeature(nearestEdge, nearestPoint, 0.0);
    }

    private BufferFeature computeEdgeAtDistanceThreshhold(final BufferFeature startFeature, Double threshholdDistance,
            String roadName, Boolean upstreamPath, Boolean upstreamStart) {
        List<Integer> usedEdges = new ArrayList<Integer>() {
            {
                add(startFeature.getEdge());
            }
        };

        EdgeIteratorState currentState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        Integer currentNode = upstreamStart ? currentState.getBaseNode() : currentState.getAdjNode();

        // Check starting edge
        Double currentDistance = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(),
                startFeature.getPoint().getLon(),
                nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode));
        Double previousDistance = 0.0;

        if (currentDistance >= threshholdDistance) {
            return startFeature;
        }

        while (true) {
            EdgeIterator iterator = edgeExplorer.setBaseNode(currentNode);
            List<Integer> potentialEdges = new ArrayList<Integer>();
            List<Integer> potentialRoundaboutEdges = new ArrayList<Integer>();
            List<Integer> potentialRoundaboutEdgesWithoutName = new ArrayList<Integer>();
            Integer currentEdge = -1;

            while (iterator.next()) {
                String[] roadNames = sanitizeRoadNames(iterator.getName());
                Integer tempEdge = iterator.getEdge();
                EdgeIteratorState tempState = graph.getEdgeIteratorState(tempEdge, Integer.MIN_VALUE);

                // Temp hasn't been used before and has proper flow
                if (!usedEdges.contains(tempEdge) && hasProperFlow(currentState, tempState, upstreamPath)) {

                    // Temp has proper road name, isn't part of a roundabout, and bidirectional.
                    // Higher priority escape.
                    if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && !tempState.get(flagEncoder.getBooleanEncodedValue("roundabout"))
                            && isBidirectional(tempState)) {
                        currentEdge = tempEdge;
                        usedEdges.add(tempEdge);
                        break;
                    }

                    // Temp has proper road name and isn't part of a roundabout. Lower priority
                    // escape.
                    else if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && !tempState.get(flagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialEdges.add(tempEdge);
                    }

                    // Temp has proper road name and is part of a roundabout. Higher entry priority.
                    else if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && tempState.get(flagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialRoundaboutEdges.add(tempEdge);
                    }

                    // Temp is part of a roundabout. Lower entry priority.
                    else if (tempState.get(flagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialRoundaboutEdgesWithoutName.add(tempEdge);
                    }
                }
            }

            // No bidirectional edge found. Choose from potential edge lists.
            if (currentEdge == -1) {
                if (potentialEdges.size() > 0) {

                    // The dreaded Michigan left 😱
                    if (potentialEdges.size() > 1) {
                        // This logic is not very robust as it stands, but I couldn't think of anything
                        // better
                        // In the case of a michigan left, choose the edge that's longer as to not take
                        // the left

                        EdgeIteratorState tempState = graph.getEdgeIteratorState(potentialEdges.get(0),
                                Integer.MIN_VALUE);
                        Double dist1 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                        - nodeAccess.getLon(tempState.getBaseNode()));
                        tempState = graph.getEdgeIteratorState(potentialEdges.get(potentialEdges.size() - 1),
                                Integer.MIN_VALUE);
                        Double dist2 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                        - nodeAccess.getLon(tempState.getBaseNode()));

                        if (dist1 > dist2) {
                            currentEdge = potentialEdges.get(0);
                            usedEdges.add(potentialEdges.get(0));
                        } else {
                            currentEdge = potentialEdges.get(potentialEdges.size() - 1);
                            usedEdges.add(potentialEdges.get(potentialEdges.size() - 1));
                        }
                    }
                    // Only one possible route
                    else {
                        currentEdge = potentialEdges.get(0);
                        usedEdges.add(potentialEdges.get(0));
                    }
                } else if (potentialRoundaboutEdges.size() > 0) {
                    currentEdge = potentialRoundaboutEdges.get(0);
                    usedEdges.add(potentialRoundaboutEdges.get(0));
                } else if (potentialRoundaboutEdgesWithoutName.size() > 0) {
                    currentEdge = potentialRoundaboutEdgesWithoutName.get(0);
                    usedEdges.add(potentialRoundaboutEdgesWithoutName.get(0));
                } else {
                    throw new WebApplicationException("Dead end found.");
                }
            }

            // Calculate new distance
            Integer otherNode = graph.getOtherNode(currentEdge, currentNode);
            previousDistance = currentDistance;
            currentDistance += DistancePlaneProjection.DIST_PLANE.calcDist(nodeAccess.getLat(currentNode),
                    nodeAccess.getLon(currentNode), nodeAccess.getLat(otherNode), nodeAccess.getLon(otherNode));

            if (currentDistance >= threshholdDistance) {
                return new BufferFeature(currentEdge,
                        new GHPoint3D(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode), 0),
                        previousDistance);
            }

            // Move to next node
            currentNode = otherNode;
            currentState = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
        }
    }

    private GHPoint3D computePointAtDistanceThreshhold(BufferFeature startFeature, Double threshholdDistance,
            BufferFeature finalEdge, Boolean upstreamPath, Boolean upstreamStart) {
        EdgeIteratorState finalState = graph.getEdgeIteratorState(finalEdge.getEdge(), Integer.MIN_VALUE);
        PointList pointList = finalState.fetchWayGeometry(FetchMode.ALL);

        // When the buffer is only as wide as a single edge, truncate one half of the
        // segment
        if (startFeature.getEdge().equals(finalEdge.getEdge())) {
            PointList tempList = new PointList();

            // Truncate _until_ startPoint
            if (upstreamStart) {
                for (GHPoint3D point : pointList) {
                    tempList.add(point);
                    if (startFeature.getPoint().equals(point)) {
                        break;
                    }
                }
            }
            // Truncate _after_ startPoint
            else {
                Boolean pastPoint = false;
                for (GHPoint3D point : pointList) {
                    if (startFeature.getPoint().equals(point)) {
                        pastPoint = true;
                    }
                    if (pastPoint) {
                        tempList.add(point);
                    }
                }
            }

            pointList = tempList;
        }

        // Reverse geometry when going upstream
        if (isBidirectional(finalState)) {
            if (upstreamStart) {
                pointList.reverse();
            }
        } else if (upstreamPath) {
            pointList.reverse();
        }

        GHPoint3D pointCandidate = pointList.get(0);

        for (GHPoint3D point : pointList) {
            // Filter zero-points made by PointList() scaling
            if (point.lat != 0 && point.lon != 0) {
                // TODO: Make this use the same distance-along-road algorithm because it
                // currently does bends incorrectly (and the reverse isn't doing anything
                // without the along-road-method)
                // Also, the logic could be totally changed to break on an exceeding distance
                // (which is what it was doing before) but that requires that the logic above
                // for reversal is *absolutely* correct

                // Check between prevPoint and currentPoint to see which is closer to the
                // threshholdDistance
                if (Math.abs(DistancePlaneProjection.DIST_PLANE.calcDist(finalEdge.getPoint().getLat(),
                        finalEdge.getPoint().getLon(), point.lat, point.lon)
                        - (threshholdDistance - finalEdge.getDistance())) < Math
                                .abs(DistancePlaneProjection.DIST_PLANE.calcDist(finalEdge.getPoint().getLat(),
                                        finalEdge.getPoint().getLon(), pointCandidate.lat, pointCandidate.lon)
                                        - (threshholdDistance - finalEdge.getDistance()))) {
                    pointCandidate = point;
                }
            }
        }

        return pointCandidate;
    }

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

        // OLD LOGIC: BAD! Doesn't account for coming from a bidirectional road
        // return isBidirectional(state) || (state.getBaseNode() == currentNode) ==
        // upstreamPath;
    }

    private Boolean isBidirectional(EdgeIteratorState state) {
        return state.get(flagEncoder.getAccessEnc()) && state.getReverse(flagEncoder.getAccessEnc());
    }

    private Response createGeoJsonResponse(List<Point> points, StopWatch sw) {
        sw.stop();

        List<JsonFeature> features = new ArrayList<>();
        for (Point point : points) {
            JsonFeature feature = new JsonFeature();
            feature.setGeometry(point);
            features.add(feature);
        }

        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("type", "FeatureCollection");
        json.putPOJO("copyrights", ResponsePathSerializer.COPYRIGHTS);
        json.putPOJO("features", features);
        return Response.ok(json).header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }
}

class BufferFeature {
    private Integer edge;
    private GHPoint3D point;
    private Double distance;

    public BufferFeature(Integer edge, GHPoint3D point, Double distance) {
        this.edge = edge;
        this.point = point;
        this.distance = distance;
    }

    public BufferFeature(Integer edge, GHPoint3D point) {
        this.edge = edge;
        this.point = point;
    }

    public BufferFeature(Integer edge, Double distance) {
        this.edge = edge;
        this.distance = distance;
    }

    public Integer getEdge() {
        return this.edge;
    }

    public GHPoint3D getPoint() {
        return this.point;
    }

    public Double getDistance() {
        return this.distance;
    }
}