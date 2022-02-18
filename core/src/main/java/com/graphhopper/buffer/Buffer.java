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
        // Point: Lat,Lon? Lon,Lat? Starting point
        // Name: Name of road to filter on
        // Distance: Radius of buffer
        // TODO: Snap distance? The size of the querying bubble (Currently .001 degrees with 3 'pulses')
        
        threshholdDistance = 1000;

        // TODO: Turn startLat and startLon into a class or something

        List<Integer> filteredQueryEdges = new ArrayList<Integer>();

        for (int i = 1; i < 4; i++){
            // Scale up query BBox
            BBox bbox = new BBox(startLon - .001 * i, startLon + .001 * i, startLat - .001 * i, startLat + .001 * i);

            final List<Integer> allQueryEdges = queryBbox(bbox);
            filteredQueryEdges = filterQueryEdgesByName(allQueryEdges, roadName);

            // Two adjacent highways found
            if (filteredQueryEdges.size() == 2) {
                break;
            }

            if (filteredQueryEdges.size() > 2) {
                // TODO: Determine a better way to scale bounding box
                // This _should_ never get called, but it might?
                System.out.println("Error with logic.");
            }
        }


        List<List<Integer>> connectedEdgeLists = splitEdgesIntoConnectedLists(filteredQueryEdges);

        for (List<Integer> connectedEdgeList : connectedEdgeLists){
            BufferStartFeature startFeature = computeStartFeature(connectedEdgeList, startLat, startLon);
            Integer edgeAtThreshhold = computeEdgeAtDistanceThreshhold(startFeature, threshholdDistance, roadName);
            GHPoint3D pointAtThreshhold = computePointAtDistanceThreshhold(startFeature, threshholdDistance, edgeAtThreshhold);

            // Call out to the Routing service however that works with 
            // pointAtThreshhold.lat and lon, and startFeature.lat and lon
        }

        return;
    }

    private List<Integer> queryBbox(BBox bbox){
        final List<Integer> edgesInBbox = new ArrayList<Integer>();

        this.locationIndex.query(bbox, new LocationIndex.Visitor(){
            @Override
            public void onEdge(int edgeId) {
                edgesInBbox.add(edgeId);
            };
        });

        return edgesInBbox;
    }

    private String[] sanitizeRoadNames(String roadNames){
        String[] separatedNames = roadNames.split(",");

        for (int i = 0; i < separatedNames.length; i++){
            separatedNames[i] = separatedNames[i].trim();
        }

        return separatedNames;
    }

    private List<Integer> filterQueryEdgesByName(List<Integer> queryEdges, String targetName){
        List<Integer> filteredQueryEdges = new ArrayList<Integer>();

        for (int edge : queryEdges){
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

            // Roads sometimes have multiple names delineated by a comma
            String[] queryRoadNames = sanitizeRoadNames(state.getName());

            if(Arrays.stream(queryRoadNames).anyMatch(targetName::equals)){
                filteredQueryEdges.add(edge);
            }
        }

        return filteredQueryEdges;
    }

    private List<List<Integer>> splitEdgesIntoConnectedLists(List<Integer> filteredQueryEdges){
        List<List<Integer>> connectedSets = new ArrayList<List<Integer>>();

        final List<Integer> setA = computeConnectedList(filteredQueryEdges);
        filteredQueryEdges.removeAll(setA);
        final List<Integer> setB = computeConnectedList(filteredQueryEdges);

        return new ArrayList<List<Integer>>() {
            {
                add(setA);
                add(setB);
            }
        };
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

    private Integer computeEdgeAtDistanceThreshhold(final BufferStartFeature startFeature, double threshholdDistance, String targetRoad){
        List<Integer> usedEdges = new ArrayList<Integer>() {{ add(startFeature.edge); }};

        EdgeIteratorState state = graph.getEdgeIteratorState(startFeature.edge, Integer.MIN_VALUE);
        Integer baseNode = state.getBaseNode();
        Integer finalEdge = startFeature.edge;

        Double currentDistance = 0.0;

        while (true){
            EdgeIterator iterator = edgeExplorer.setBaseNode(baseNode);
            int currentEdge = -1;

            while (iterator.next()){

                String[] roadNames = sanitizeRoadNames(iterator.getName());

                if (Arrays.stream(roadNames).anyMatch(targetRoad::equals) && !usedEdges.contains(iterator.getEdge())){
                    currentEdge = iterator.getEdge();
                    break;
                }
            }

            if (currentEdge == -1){
                System.out.println("Error finding adjacent route?");
                // TODO: Throw exception?
            }

            int otherNode = graph.getOtherNode(currentEdge, baseNode);

            currentDistance = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.point.getLat(), startFeature.point.getLon(),
                nodeAccess.getLat(otherNode), nodeAccess.getLon(otherNode));

            if (currentDistance >= threshholdDistance){
                return finalEdge;
            }
            else {
                finalEdge = currentEdge;
            }
        }
    }

    private GHPoint3D computePointAtDistanceThreshhold(BufferStartFeature startFeature, double threshholdDistance, Integer finalEdge){
        EdgeIteratorState finalState = graph.getEdgeIteratorState(finalEdge, Integer.MIN_VALUE);
        PointList pointList = finalState.fetchWayGeometry(FetchMode.ALL);

        // In the case the buffer is only as wide as a single edge, truncate pointList _until_ startFeature.point
        if (startFeature.edge.equals(finalEdge)){
            PointList tempList = new PointList();

            for (GHPoint3D point : pointList){
                tempList.add(point);
                if(startFeature.point.equals(point)){
                    break;
                }
            }

            pointList = tempList;
        }

        // Reverse pointList so direction of travel is 'upstream'
        pointList.reverse();

        GHPoint3D prevPoint = pointList.get(0);

        for(GHPoint3D point : pointList){
            double finalDist = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.point.getLat(), startFeature.point.getLon(),
                point.lat, point.lon);

            if(finalDist > threshholdDistance){
                break;
            };
            prevPoint = point;
        }

        // Return point just _before_ crossing distanceThreshhold
        return prevPoint;
    }
}

class BufferStartFeature {
    public Integer edge;
    public GHPoint3D point;

    public BufferStartFeature(Integer edge, GHPoint3D point){
        this.edge = edge;
        this.point = point;
    }
}
