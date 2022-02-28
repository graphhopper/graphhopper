package com.graphhopper.buffer;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.NodeAccess;

import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.shapes.GHPoint3D;
import com.graphhopper.util.PointList;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.shapes.BBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Buffer {
    private final Graph graph;
    private final LocationIndex locationIndex;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;

    public Buffer (GraphHopper hopper){
        this.graph = hopper.getGraphHopperStorage().getBaseGraph();
        this.locationIndex = hopper.getLocationIndex();   
        this.nodeAccess = graph.getNodeAccess();
        this.edgeExplorer = graph.createEdgeExplorer();
    }

    // TODO: parameters. How are the isochrone/route requests set up?
    public void generateBuffer(String roadName, double threshholdDistance, double startLat, double startLon){
        // roadName : Road to filter on.
        // startLat/startLon : Starting point. TODO: Turn into/find class.
        // threshholdDistance : Radius of buffer

        // TODO: Snap distance? The size of the querying bubble (Currently .001 degrees with 3 'pulses')

        List<List<Integer>> connectedEdgeLists = new ArrayList<List<Integer>>();

        for (int i = 1; i < 4; i++){
            // Scale up query BBox
            BBox bbox = new BBox(startLon - .01 * i, startLon + .01 * i, startLat - .01 * i, startLat + .01 * i);

            final List<Integer> filteredQueryEdges = queryBbox(bbox, roadName);
            connectedEdgeLists = splitEdgesIntoConnectedLists(filteredQueryEdges);

            // One bidirectional road
            if (connectedEdgeLists.size() == 1) {
                computeBufferSegment(connectedEdgeLists.get(0), roadName, threshholdDistance, startLat, startLon, true);
                computeBufferSegment(connectedEdgeLists.get(0), roadName, threshholdDistance, startLat, startLon, false);
                return;
            }

            // Two unidirectional, parallel roads
            if (connectedEdgeLists.size() == 2){
                computeBufferSegment(connectedEdgeLists.get(0), roadName, threshholdDistance, startLat, startLon, true);
                computeBufferSegment(connectedEdgeLists.get(1), roadName, threshholdDistance, startLat, startLon, true);
                return;
            }
        }

        throw new RuntimeException("Error: Could not split up roads properly.");
    }

    private List<Integer> queryBbox(BBox bbox, String roadName){
        final List<Integer> filteredEdgesInBbox = new ArrayList<Integer>();

        this.locationIndex.query(bbox, new LocationIndex.Visitor(){
            @Override
            public void onEdge(int edgeId) {
                EdgeIteratorState state = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);

                // Roads sometimes have multiple names delineated by a comma
                String[] queryRoadNames = sanitizeRoadNames(state.getName());

                if(Arrays.stream(queryRoadNames).anyMatch(roadName::equals)){
                    filteredEdgesInBbox.add(edgeId);
                }
            };
        });

        return filteredEdgesInBbox;
    }

    private void computeBufferSegment(List<Integer> connectedEdgeList, String roadName, double threshholdDistance, double startLat, double startLon, Boolean upstreamPath){
        BufferStartFeature startFeature = computeStartFeature(connectedEdgeList, startLat, startLon);
        Integer edgeAtThreshhold = computeEdgeAtDistanceThreshhold(startFeature, threshholdDistance, roadName, upstreamPath);
        GHPoint3D pointAtThreshhold = computePointAtDistanceThreshhold(startFeature, threshholdDistance, edgeAtThreshhold, upstreamPath);

        System.out.println("Point: " + startFeature.getPoint().getLon() + ", " + startFeature.getPoint().getLat());
        System.out.println("Point: " + pointAtThreshhold.getLon() + ", " + pointAtThreshhold.getLat());
    }

    private String[] sanitizeRoadNames(String roadNames){
        String[] separatedNames = roadNames.split(",");

        for (int i = 0; i < separatedNames.length; i++){
            separatedNames[i] = separatedNames[i].trim();
        }

        return separatedNames;
    }

    private List<List<Integer>> splitEdgesIntoConnectedLists(List<Integer> filteredQueryEdges){
        List<List<Integer>> connectedLists = new ArrayList<List<Integer>>();

        while (!filteredQueryEdges.isEmpty()){
            final List<Integer> connectedList = computeConnectedList(filteredQueryEdges);
            filteredQueryEdges.removeAll(connectedList);
            connectedLists.add(connectedList);
        }

        return connectedLists;
    }

    private List<Integer> computeConnectedList(List<Integer> edgeList){
        final Integer startingEdge = edgeList.get(0);

        List<Integer> connectedList = new ArrayList<Integer>(){{ add(startingEdge); }};

        final EdgeIteratorState state = graph.getEdgeIteratorState(startingEdge, Integer.MIN_VALUE);

        List<Integer> queue = new ArrayList<Integer>() {
            {
                add(state.getBaseNode());
                add(state.getAdjNode());
            }
        };

        while (queue.size() > 0){
            Integer node = queue.remove(0);

            for(Integer edge : edgeList){
                if (!connectedList.contains(edge)){
                    if (graph.isAdjacentToNode(edge, node)){
                        queue.add(graph.getOtherNode(edge, node));
                        connectedList.add(edge);
                        break;
                    }
                }
            }
        }

        return connectedList;
    }

    private BufferStartFeature computeStartFeature(List<Integer> edgeList, double startLat, double startLon){
        double lowestDistance = Double.MAX_VALUE;
        GHPoint3D nearestPoint = null;
        Integer nearestEdge = null;

        for (Integer edge : edgeList){
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

            PointList pointList = state.fetchWayGeometry(FetchMode.ALL);

            for(GHPoint3D point : pointList)
            {
                // TODO: Replace centralPoint with input points
                double dist = DistancePlaneProjection.DIST_PLANE.calcDist(startLat, startLon,
                    point.lat, point.lon);

                if(dist < lowestDistance){
                    lowestDistance = dist;
                    nearestPoint = point;
                    nearestEdge = edge;
                };
            }
        }

        return new BufferStartFeature(nearestEdge, nearestPoint);
    }

    private Integer computeEdgeAtDistanceThreshhold(final BufferStartFeature startFeature, double threshholdDistance, String roadName, Boolean upstreamPath){
        List<Integer> usedEdges = new ArrayList<Integer>() {{ add(startFeature.getEdge()); }};

        EdgeIteratorState state = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        Integer currentNode = upstreamPath ? state.getBaseNode() : state.getAdjNode();

        // Check starting edge
        Double currentDistance = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(), startFeature.getPoint().getLon(),
            nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode));

        if (currentDistance >= threshholdDistance) {
            return startFeature.getEdge();
        }

        while (true){
            EdgeIterator iterator = edgeExplorer.setBaseNode(currentNode);
            int currentEdge = -1;

            while (iterator.next()){
                String[] roadNames = sanitizeRoadNames(iterator.getName());

                Integer tempEdge = iterator.getEdge();

                if (Arrays.stream(roadNames).anyMatch(roadName::equals) && !usedEdges.contains(tempEdge)){
                    currentEdge = tempEdge;
                    usedEdges.add(tempEdge);
                    break;
                }
            }

            if (currentEdge == -1){
                throw new RuntimeException("Error: Could not build out path using current name.");
            }

            // Move to next node
            currentNode = graph.getOtherNode(currentEdge, currentNode);

            currentDistance = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(), startFeature.getPoint().getLon(),
                nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode));

            if (currentDistance >= threshholdDistance){
                return currentEdge;
            }
        }
    }

    private GHPoint3D computePointAtDistanceThreshhold(BufferStartFeature startFeature, double threshholdDistance, Integer finalEdge, Boolean upstreamPath){
        EdgeIteratorState finalState = graph.getEdgeIteratorState(finalEdge, Integer.MIN_VALUE);
        PointList pointList = finalState.fetchWayGeometry(FetchMode.ALL);

        // In the case where the buffer is only as wide as a single edge, truncate one half of the segment
        if (startFeature.getEdge().equals(finalEdge)){

            PointList tempList = new PointList();
            
            // Truncate _until_ startPoint
            if (upstreamPath){
                for (GHPoint3D point: pointList){
                    tempList.add(point);
                    if(startFeature.getPoint().equals(point)){
                        break;
                    }
                }
            }
            // Truncate _after_ startPoint
            else {
                Boolean pastPoint = false;
                for (GHPoint3D point: pointList){
                    if (startFeature.getPoint().equals(point)){
                        pastPoint = true;
                    }
                    if (pastPoint){
                        tempList.add(point);
                    }
                }
            }

            pointList = tempList;
        }

        // Reverse geometry when going upstream
        if(upstreamPath){
            pointList.reverse();
        }

        GHPoint3D prevPoint = pointList.get(0);

        for(GHPoint3D point : pointList){
            // Filter zero-points made by PointList() scaling
            if (point.lat != 0 && point.lon != 0) {
                double finalDist = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(), startFeature.getPoint().getLon(),
                    point.lat, point.lon);

                // Point is past threshhold distance
                if(finalDist >= threshholdDistance){

                    // Check between prevPoint and currentPoint to see which is closer to the threshholdDistance
                    if (Math.abs(DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(), startFeature.getPoint().getLon(), point.lat, point.lon) - threshholdDistance) <=
                        Math.abs(DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(), startFeature.getPoint().getLon(), prevPoint.lat, prevPoint.lon) - threshholdDistance)) {
                        prevPoint = point;

                        // TODO: Use difference in distances to calculate point exactly at threshhold
                    }
                    break;
                };
                prevPoint = point;
            }
        }

        // Return point closest to the distanceThreshhold
        return prevPoint;
    }
}

class BufferStartFeature {
    private Integer edge;
    private GHPoint3D point;

    public BufferStartFeature(Integer edge, GHPoint3D point){
        this.edge = edge;
        this.point = point;
    }

    public Integer getEdge(){
        return this.edge;
    }

    public GHPoint3D getPoint(){
        return this.point;
    }
}