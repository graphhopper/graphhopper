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
import com.graphhopper.buffer.BufferFeature;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

@Path("buffer")
public class BufferResource {
    private final LocationIndex locationIndex;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;
    private final FlagEncoder carFlagEncoder;
    private final Graph graph;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));

    @Inject
    public BufferResource(GraphHopper graphHopper) {
        this.graph = graphHopper.getGraphHopperStorage().getBaseGraph();
        this.locationIndex = graphHopper.getLocationIndex();
        this.nodeAccess = graph.getNodeAccess();
        this.edgeExplorer = graph.createEdgeExplorer();

        EncodingManager encodingManager = graphHopper.getEncodingManager();
        this.carFlagEncoder = encodingManager.getEncoder("car");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("roadName") @NotNull String roadName,
            @QueryParam("thresholdDistance") @NotNull Double thresholdDistance,
            @QueryParam("queryMultiplier") @DefaultValue(".01") Double queryMultiplier,
            @QueryParam("upstream") @DefaultValue("false") Boolean upstream) {
        if (queryMultiplier > 1) {
            throw new IllegalArgumentException("Query multiplier is too high.");
        } else if (queryMultiplier <= 0) {
            throw new IllegalArgumentException("Query multiplier cannot be zero or negative.");
        }

        StopWatch sw = new StopWatch().start();

        roadName = sanitizeRoadNames(roadName)[0];
        BufferFeature primaryStartFeature = calculatePrimaryStartFeature(point.get().lat, point.get().lon, roadName,
                queryMultiplier);
        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);
        List<LineString> lineStrings = new ArrayList<LineString>();

        // Start feature edge is bidirectional. Simple
        if (isBidirectional(state)) {
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, upstream, true));
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, upstream, false));
        }
        // Start feature edge is unidirectional. Requires finding sister road
        else {
            BufferFeature secondaryStartFeature = calculateSecondaryStartFeature(primaryStartFeature, roadName, .005);
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, upstream, upstream));
            lineStrings.add(computeBufferSegment(secondaryStartFeature, roadName, thresholdDistance, upstream, upstream));
        }

        return createGeoJsonResponse(lineStrings, sw);
    }


    /**
     * Given a lat/long, finds all nearby edges with a corresponding road name. Uses three expanding
     * 'pulses' based off the queryMultiplier until at least a single matching edge is found.
     *
     * @param startLat latitude at center of query box
     * @param startLon longitude at center of query box
     * @param roadName name of road
     * @param queryMultiplier base dimension of query box
     * @return buffer feature closest to start lat/long
    */
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

    /**
     * Given a buffer feature, finds all nearby edges with a corresponding road name which aren't along
     * the same road segment as the given feature. Uses three expanding 'pulses' based off the queryMultiplier 
     * until at least a single matching edge is found.
     *
     * @param primaryStartFeature buffer feature to query off of
     * @param roadName name of road
     * @param queryMultiplier base dimension of query box
     * @return buffer feature closest to primary start feature
    */
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

    /**
     * Filters out all edges within a bbox that don't have a matching road name
     *
     * @param bbox query zone
     * @param roadName name of road
     * @return all edges which have matching road name
    */
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

    /**
     * Given a lat/long, finds all nearby edges with a corresponding road name. Uses three expanding
     * 'pulses' based off the queryMultiplier until at least a single matching edge is found.
     *
     * @param startLat latitude at center of query box
     * @param startLon longitude at center of query box
     * @param roadName name of road
     * @param queryMultiplier base dimension of query box
     * @return lineString representing start -> end
    */
    private LineString computeBufferSegment(BufferFeature startFeature, String roadName, Double thresholdDistance,
            Boolean upstreamPath, Boolean upstreamStart) {
        BufferFeature edgeAtThreshold = computeEdgeAtDistanceThreshold(startFeature, thresholdDistance, roadName,
                upstreamPath, upstreamStart);
        GHPoint3D pointAtThreshold = computePointAtDistanceThreshold(startFeature, thresholdDistance,
                edgeAtThreshold, upstreamPath, upstreamStart);

        Coordinate[] coordinates = new Coordinate[]{
            new Coordinate(pointAtThreshold.getLon(), pointAtThreshold.getLat()),
            new Coordinate(startFeature.getPoint().getLon(), startFeature.getPoint().getLat())
        };

        return geometryFactory.createLineString(coordinates);
    }

    /**
     * Splits a comma-separated list of road names, then removes any spaces, casing, and dashes
     *
     * @param roadNames comma-separated list of road names
     * @return array of split road names
    */
    private String[] sanitizeRoadNames(String roadNames) {
        String[] separatedNames = roadNames.split(",");

        // TODO: Add in removal of road directionality (i.e. N US-80)
        for (int i = 0; i < separatedNames.length; i++) {
            separatedNames[i] = separatedNames[i].trim().replace("-", "").toLowerCase();
        }

        return separatedNames;
    }

    /**
     * Cycles through all nearby, matching roads, and selects the point which is closest to the given lat/lon
     * 
     * @param edgeList all nearby edges with proper road name
     * @param startLat latitude at center of query box
     * @param startLon longitude at center of query box
     * @return closest point along road
    */
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
            }
        }

        return new BufferFeature(nearestEdge, nearestPoint, 0.0);
    }

    /**
     * Iterates along the flow of a road given a starting feature until hitting the denoted threshold.
     * Usually only selects adjacent roads which have the matching road name, but certain road types like
     * roundabouts have separate logic.
     * 
     * @param startFeature buffer feature to start at
     * @param thresholdDistance maximum distance in meters
     * @param roadName name of road
     * @param upstreamPath direction to build path - either along or against road's flow
     * @param upstreamStart initial 'launch' direction - used only for a bidirectional start
     * @return buffer feature at specified distance away from start
    */
    private BufferFeature computeEdgeAtDistanceThreshold(final BufferFeature startFeature, Double thresholdDistance,
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
        Integer currentEdge = -1;

        if (currentDistance >= thresholdDistance) {
            return startFeature;
        }

        while (currentDistance < thresholdDistance) {
            EdgeIterator iterator = edgeExplorer.setBaseNode(currentNode);
            List<Integer> potentialEdges = new ArrayList<Integer>();
            List<Integer> potentialRoundaboutEdges = new ArrayList<Integer>();
            List<Integer> potentialRoundaboutEdgesWithoutName = new ArrayList<Integer>();
            currentEdge = -1;

            while (iterator.next()) {
                String[] roadNames = sanitizeRoadNames(iterator.getName());
                Integer tempEdge = iterator.getEdge();
                EdgeIteratorState tempState = graph.getEdgeIteratorState(tempEdge, Integer.MIN_VALUE);

                // Temp hasn't been used before and has proper flow
                if (!usedEdges.contains(tempEdge) && hasProperFlow(currentState, tempState, upstreamPath)) {

                    // Temp has proper road name, isn't part of a roundabout, and bidirectional.
                    // Higher priority escape.
                    if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && !tempState.get(carFlagEncoder.getBooleanEncodedValue("roundabout"))
                            && isBidirectional(tempState)) {
                        currentEdge = tempEdge;
                        usedEdges.add(tempEdge);
                        break;
                    }

                    // Temp has proper road name and isn't part of a roundabout. Lower priority
                    // escape.
                    else if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && !tempState.get(carFlagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialEdges.add(tempEdge);
                    }

                    // Temp has proper road name and is part of a roundabout. Higher entry priority.
                    else if (Arrays.stream(roadNames).anyMatch(roadName::equals)
                            && tempState.get(carFlagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialRoundaboutEdges.add(tempEdge);
                    }

                    // Temp is part of a roundabout. Lower entry priority.
                    else if (tempState.get(carFlagEncoder.getBooleanEncodedValue("roundabout"))) {
                        potentialRoundaboutEdgesWithoutName.add(tempEdge);
                    }
                }
            }

            // No bidirectional edge found. Choose from potential edge lists.
            if (currentEdge == -1) {
                if (potentialEdges.size() > 0) {

                    // The Michigan Left
                    if (potentialEdges.size() > 1) {
                        // This logic is not very robust as it stands, change it
                        // In the case of a Michigan left, choose the edge that's further away

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

            // Break before moving to next node in case hitting the threshold
            if (currentDistance >= thresholdDistance) {
                break;
            }

            // Move to next node
            currentNode = otherNode;
            currentState = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
        }

        return new BufferFeature(currentEdge,
                        new GHPoint3D(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode), 0),
                        previousDistance);
    }

    /**
     * Iterates along the way geometry of a given edge until hitting the denoted threshold. Has separate
     * logic to break up edges where the original start feature is along the same edge (to ensure directionality).
     * 
     * @param startFeature original starting buffer feature
     * @param thresholdDistance maximum distance in meters
     * @param finalEdge buffer feature of edge at distance threshold
     * @param upstreamPath direction to build path - either along or against road's flow
     * @param upstreamStart initial 'launch' direction - used only for a bidirectional start
     * @return GHPoint3D at specified distance
    */
    private GHPoint3D computePointAtDistanceThreshold(BufferFeature startFeature, Double thresholdDistance,
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

        Double currentDistance = finalEdge.getDistance();
        GHPoint3D previousPoint = pointList.get(0);

        for (GHPoint3D currentPoint : pointList) {
            // Filter zero-points made by PointList() scaling
            if (currentPoint.lat != 0 && currentPoint.lon != 0) {
                // Check if exceeds thresholdDistance
                currentDistance += DistancePlaneProjection.DIST_PLANE.calcDist(currentPoint.getLat(),
                        currentPoint.getLon(), previousPoint.getLat(), previousPoint.getLon());
                if (currentDistance >= thresholdDistance) {
                    return currentPoint;
                }

                previousPoint = currentPoint;
            }
        }

        // Default to previous point in case of a miscalculation
        return previousPoint;
    }

    /**
     * Checks if the next edge is going the same direction as the current edge. Prioritizes bidirectional
     * roads to deal with a road where one-ways converge (e.g. -<)
     * 
     * @param currentState current edge
     * @param tempState edge to test
     * @param upstreamPath direction to build path - either along or against road's flow
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
        return state.get(carFlagEncoder.getAccessEnc()) && state.getReverse(carFlagEncoder.getAccessEnc());
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
        json.putPOJO("copyrights", ResponsePathSerializer.COPYRIGHTS);
        json.putPOJO("features", features);
        return Response.ok(json).header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }
}