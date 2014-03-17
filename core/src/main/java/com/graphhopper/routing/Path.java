/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the nodes for the found path of an algorithm. It additionally needs the edgeIds to make
 * edge determination faster and less complex as there could be several edges (u,v) especially for
 * graphs with shortcuts.
 * <p/>
 * @author Peter Karich
 * @author Ottavio Campana
 */
public class Path
{
    protected Graph graph;
    private FlagEncoder encoder;
    protected double distance;
    // we go upwards (via EdgeEntry.parent) from the goal node to the origin node
    protected boolean reverseOrder = true;
    protected long millis;
    private boolean found;
    protected EdgeEntry edgeEntry;
    final StopWatch extractSW = new StopWatch("extract");
    private int fromNode = -1;
    protected int endNode = -1;
    private TIntList edgeIds;
    private PointList cachedPoints;
    private InstructionList cachedWays;
    private double weight;

    public Path( Graph graph, FlagEncoder encoder )
    {
        this.weight = Double.MAX_VALUE;
        this.graph = graph;
        this.encoder = encoder;
        this.edgeIds = new TIntArrayList();
    }

    /**
     * Populates an unextracted path instances from the specified path p.
     */
    Path( Path p )
    {
        this(p.graph, p.encoder);
        weight = p.weight;
        edgeIds = new TIntArrayList(p.edgeIds);
        edgeEntry = p.edgeEntry;
    }

    public Path setEdgeEntry( EdgeEntry edgeEntry )
    {
        this.edgeEntry = edgeEntry;
        return this;
    }

    protected void addEdge( int edge )
    {
        edgeIds.add(edge);
    }

    protected Path setEndNode( int end )
    {
        endNode = end;
        return this;
    }

    /**
     * We need to remember fromNode explicitely as its not saved in one edgeId of edgeIds.
     */
    protected Path setFromNode( int from )
    {
        fromNode = from;
        return this;
    }

    /**
     * @return the first node of this Path.
     */
    private int getFromNode()
    {
        if (fromNode < 0)
            throw new IllegalStateException("Call extract() before retrieving fromNode");

        return fromNode;
    }

    /**
     * @return the last node of this Path.
     */
    private int getEndNode()
    {
        if (endNode < 0)
            throw new IllegalStateException("Call extract() before retrieving endNode");

        return endNode;
    }

    public boolean isFound()
    {
        return found;
    }

    public Path setFound( boolean found )
    {
        this.found = found;
        return this;
    }

    void reverseOrder()
    {
        if (!reverseOrder)
            throw new IllegalStateException("Switching order multiple times is not supported");

        reverseOrder = false;
        edgeIds.reverse();
    }

    /**
     * @return distance in meter
     */
    public double getDistance()
    {
        return distance;
    }

    /**
     * @return time in millis
     */
    public long getMillis()
    {
        return millis;
    }

    /**
     * This weight will be updated during the algorithm. The initial value is maximum double.
     */
    public double getWeight()
    {
        return weight;
    }

    public Path setWeight( double w )
    {
        this.weight = w;
        return this;
    }

    /**
     * Extracts the Path from the shortest-path-tree determined by edgeEntry.
     */
    public Path extract()
    {
        extractSW.start();
        EdgeEntry goalEdge = edgeEntry;
        setEndNode(goalEdge.adjNode);
        while (EdgeIterator.Edge.isValid(goalEdge.edge))
        {
            processEdge(goalEdge.edge, goalEdge.adjNode);
            goalEdge = goalEdge.parent;
        }

        setFromNode(goalEdge.adjNode);
        reverseOrder();
        extractSW.stop();
        return setFound(true);
    }

    /**
     * @return the time it took to extract the path in nano (!) seconds
     */
    public long getExtractTime()
    {
        return extractSW.getNanos();
    }

    public String getDebugInfo()
    {
        return extractSW.toString();
    }

    /**
     * Calls getDistance and adds the edgeId.
     */
    protected void processEdge( int edgeId, int endNode )
    {
        EdgeIteratorState iter = graph.getEdgeProps(edgeId, endNode);
        double dist = iter.getDistance();
        distance += dist;
        millis += calcMillis(dist, iter.getFlags(), false);
        addEdge(edgeId);
    }

    /**
     * Calculates the time in millis for the specified distance in meter and speed (in km/h) via
     * flags.
     */
    protected long calcMillis( double distance, long flags, boolean revert )
    {
        double speed = revert ? encoder.getReverseSpeed(flags) : encoder.getSpeed(flags);
        return (long) (distance * 3600 / speed);
    }

    /**
     * The callback used in forEveryEdge.
     */
    private static interface EdgeVisitor
    {
        void next( EdgeIteratorState edgeBase, int index );
    }

    /**
     * Iterates over all edges in this path sorted from start to end and calls the visitor callback
     * for every edge.
     * <p>
     * @param visitor callback to handle every edge. The edge is decoupled from the iterator and can
     * be stored.
     */
    private void forEveryEdge( EdgeVisitor visitor )
    {
        int tmpNode = getFromNode();
        int len = edgeIds.size();
        for (int i = 0; i < len; i++)
        {
            EdgeIteratorState edgeBase = graph.getEdgeProps(edgeIds.get(i), tmpNode);
            if (edgeBase == null)
                throw new IllegalStateException("Edge " + edgeIds.get(i) + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());

            tmpNode = edgeBase.getBaseNode();
            // later: more efficient swap
            edgeBase = graph.getEdgeProps(edgeBase.getEdge(), tmpNode);
            visitor.next(edgeBase, i);
        }
    }

    /**
     * Returns the list of all edges.
     */
    public List<EdgeIteratorState> calcEdges()
    {
        final List<EdgeIteratorState> edges = new ArrayList<EdgeIteratorState>(edgeIds.size());
        if (edgeIds.isEmpty())
            return edges;

        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeIteratorState eb, int i )
            {
                edges.add(eb);
            }
        });
        return edges;
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    public TIntList calcNodes()
    {
        final TIntArrayList nodes = new TIntArrayList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
            return nodes;

        int tmpNode = getFromNode();
        nodes.add(tmpNode);
        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeIteratorState eb, int i )
            {
                nodes.add(eb.getAdjNode());
            }
        });
        return nodes;
    }

    /**
     * @return the cached list of lat,lon for this path
     */
    public PointList calcPoints()
    {
        if (cachedPoints != null)
            return cachedPoints;

        cachedPoints = new PointList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
            return cachedPoints;

        int tmpNode = getFromNode();
        cachedPoints.add(graph.getLatitude(tmpNode), graph.getLongitude(tmpNode));
        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeIteratorState eb, int index )
            {
                PointList pl = eb.fetchWayGeometry(2);
                for (int j = 0; j < pl.getSize(); j++)
                {
                    cachedPoints.add(pl.getLatitude(j), pl.getLongitude(j));
                }
            }
        });
        return cachedPoints;
    }

    /**
     * @return the cached list of ways for this path
     */
    public InstructionList calcInstructions()
    {
        if (cachedWays != null)
            return cachedWays;

        cachedWays = new InstructionList(edgeIds.size() / 4);
        if (edgeIds.isEmpty())
            return cachedWays;

        final int tmpNode = getFromNode();
        forEveryEdge(new EdgeVisitor()
        {
            /*
             * We need three points to make directions
             *
             *        (1)----(2)
             *        /
             *       /
             *    (0)
             *
             * 0 is the node visited at t-2, 1 is the node visited
             * at t-1 and 2 is the node being visited at instant t.
             * orientation is the angle of the vector(1->2) expressed
             * as atan2, while previousOrientation is the angle of the
             * vector(0->1)
             * Intuitively, if orientation is smaller than
             * previousOrientation, then we have to turn right, while
             * if it is greater we have to turn left. To make this
             * algorithm work, we need to make the comparison by
             * considering orientation belonging to the interval
             * [ - pi + previousOrientation , + pi + previousOrientation ]
             */
            private double prevLat = graph.getLatitude(tmpNode);
            private double prevLon = graph.getLongitude(tmpNode);
            private double prevOrientation;
            private Instruction prevInstruction;
            // we do not expose the pointlist in Instruction => no need for elevation
            private PointList points = new PointList();
            private String name = null;
            private int pavementCode;
            private int wayTypeCode;

            @Override
            public void next( EdgeIteratorState edge, int index )
            {
                // baseNode is the current node and adjNode is the next
                int adjNode = edge.getAdjNode();
                double adjLat = graph.getLatitude(adjNode);
                double adjLon = graph.getLongitude(adjNode);
                double latitude, longitude;
                PointList wayGeo = edge.fetchWayGeometry(3);
                if (wayGeo.getSize() <= 2)
                {
                    latitude = adjLat;
                    longitude = adjLon;
                } else
                {
                    latitude = wayGeo.getLatitude(1);
                    longitude = wayGeo.getLongitude(1);

                    // overwrite previous lat,lon
                    int baseNode = edge.getBaseNode();
                    prevLat = graph.getLatitude(baseNode);
                    prevLon = graph.getLongitude(baseNode);
                }

                double orientation = Math.atan2(latitude - prevLat, longitude - prevLon);
                if (name == null)
                {
                    // very first instruction
                    name = edge.getName();
                    pavementCode = encoder.getPavementCode(edge.getFlags());
                    wayTypeCode = encoder.getWayTypeCode(edge.getFlags());
                    prevInstruction = new Instruction(Instruction.CONTINUE_ON_STREET, name, wayTypeCode, pavementCode, points);
                    updatePointsAndInstruction(edge, wayGeo);
                    cachedWays.add(prevInstruction);
                } else
                {
                    double tmpOrientation;
                    if (prevOrientation >= 0)
                    {
                        if (orientation < -Math.PI + prevOrientation)
                            tmpOrientation = orientation + 2 * Math.PI;
                        else
                            tmpOrientation = orientation;

                    } else
                    {
                        if (orientation > +Math.PI + prevOrientation)
                            tmpOrientation = orientation - 2 * Math.PI;
                        else
                            tmpOrientation = orientation;
                    }

                    String tmpName = edge.getName();
                    int tmpPavement = encoder.getPavementCode(edge.getFlags());
                    int tmpWayType = encoder.getWayTypeCode(edge.getFlags());
                    if ((!name.equals(tmpName))
                            || (pavementCode != tmpPavement)
                            || (wayTypeCode != tmpWayType))
                    {
                        // we do not expose the pointlist in Instruction => no need for elevation
                        points = new PointList();
                        name = tmpName;
                        pavementCode = tmpPavement;
                        wayTypeCode = tmpWayType;
                        double delta = Math.abs(tmpOrientation - prevOrientation);
                        int indication;
                        if (delta < 0.2)
                        {
                            // 0.2 ~= 11°
                            indication = Instruction.CONTINUE_ON_STREET;

                        } else if (delta < 0.8)
                        {
                            // 0.8 ~= 40°
                            if (tmpOrientation > prevOrientation)
                                indication = Instruction.TURN_SLIGHT_LEFT;
                            else
                                indication = Instruction.TURN_SLIGHT_RIGHT;

                        } else if (delta < 1.8)
                        {
                            // 1.8 ~= 103°
                            if (tmpOrientation > prevOrientation)
                                indication = Instruction.TURN_LEFT;
                            else
                                indication = Instruction.TURN_RIGHT;

                        } else
                        {
                            if (tmpOrientation > prevOrientation)
                                indication = Instruction.TURN_SHARP_LEFT;
                            else
                                indication = Instruction.TURN_SHARP_RIGHT;

                        }

                        prevInstruction = new Instruction(indication, name, wayTypeCode, pavementCode, points);
                        cachedWays.add(prevInstruction);
                    }

                    updatePointsAndInstruction(edge, wayGeo);
                }

                prevLat = adjLat;
                prevLon = adjLon;
                if (wayGeo.getSize() <= 2)
                    prevOrientation = orientation;
                else
                {
                    int beforeLast = wayGeo.getSize() - 2;
                    prevOrientation = Math.atan2(adjLat - wayGeo.getLatitude(beforeLast), adjLon - wayGeo.getLongitude(beforeLast));
                }

                boolean lastEdge = index == edgeIds.size() - 1;
                if (lastEdge)
                    cachedWays.add(new FinishInstruction(prevLat, prevLon));
            }

            private void updatePointsAndInstruction( EdgeIteratorState edge, PointList pl )
            {
                // skip adjNode
                int len = pl.size() - 1;
                for (int i = 0; i < len; i++)
                {
                    double lat = pl.getLatitude(i);
                    double lon = pl.getLongitude(i);
                    points.add(lat, lon);
                }
                double newDist = edge.getDistance();
                prevInstruction.setDistance(newDist + prevInstruction.getDistance());
                long flags = edge.getFlags();
                prevInstruction.setMillis(calcMillis(newDist, flags, false) + prevInstruction.getMillis());
            }
        });

        return cachedWays;
    }

    public UpcomingInstruction nextInstruction(double lat, double lng) {
        DistanceCalcEarth earthDistance = new DistanceCalcEarth();

        // Closest euclidean distance from (lng,lat) to a path segment
        double minDistance = Double.MAX_VALUE;

        // Instruction for which the (lng,lat) is closest to a path segment
        Instruction bestCurrentInstruction = null;

        // Next instruction
        Instruction bestNextInstruction = null;

        // Projected coordinates on a path segment
        double bestProjx=0, bestProjy=0;

        // Index in the points list of the closest (lng,lat) match
        int bestPointIndex=-1;

        // Distance yet to travel to complete the instruction
        double distanceToGo = -1;

        // Iterate over all instructions in the path
        for(int i=0; i<cachedWays.getSize()-1; i++) {
            Instruction instruction = cachedWays.get(i);
            Instruction nextInstruction = cachedWays.get(i+1);
            PointList points = instruction.getPoints();

            double distanceTillInstruction = 0;			

            // Iterate over all points in the point list
            for(int p=0; p<points.size(); p++) {
                // p1 is the current point
                double p1x = points.getLongitude(p);
                double p1y = points.getLatitude(p);

                // p2 is the next point
                double p2x, p2y;
                if(p<points.size()-1) {
                    p2x = points.getLongitude(p+1);
                    p2y = points.getLatitude(p+1);
                } else {
                    p2x = cachedWays.get(i+1).getPoints().getLongitude(0);
                    p2y = cachedWays.get(i+1).getPoints().getLatitude(0);
                }

                // Calculate the euclidean distance of point (lng,lat) to the path segment p1p2, and hence obtain the 
                // projected coordinates on the path segment
                double distance=0, projx=0, projy=0;

                double l2 = distanceEuclidean(p1x, p1y, p2x, p2y) * distanceEuclidean(p1x, p1y, p2x, p2y); 
                if (l2 == 0.0) 
                    distance = distanceEuclidean(p1x, p1y, lng, lat);
                else {
                    double t = ((lng - p1x) * (p2x - p1x) + (lat - p1y) * (p2y - p1y)) / l2;
                    if (t < 0.0) {
                        distance = distanceEuclidean(lng, lat, p1x, p1y);       // Beyond the 'p1' end of the segment
                        projx = p1x;
                        projy = p1y;
                    }
                    else if (t > 1.0) { 
                        distance = distanceEuclidean(lng, lat, p2x, p2y);  // Beyond the 'p2' end of the segment
                        projx = p2x;
                        projy = p2y;
                    }
                    else {
                        projx = p1x + t * (p2x - p1x);  // Projection falls on the segment
                        projy = p1y + t * (p2y - p1y);
                        distance = distanceEuclidean(lng, lat, projx, projy);
                    }
                }

                if (distance<=minDistance) {
                    minDistance = distance;
                    bestCurrentInstruction = instruction;
                    bestNextInstruction = nextInstruction;
                    distanceToGo = instruction.getDistance() - (distanceTillInstruction + earthDistance.calcDist(p1y, p1x, projy, projx));
                    bestProjx = projx;
                    bestProjy = projy;
                    bestPointIndex = p;
                }

                distanceTillInstruction += earthDistance.calcDist(p1y, p1x, p2y, p2x);
            }
        }

        PointList points = new PointList();
        points.add(bestProjy, bestProjx);
        for(int i=bestPointIndex+1; i<bestCurrentInstruction.getPoints().getSize(); i++)
            points.add(bestCurrentInstruction.getPoints().getLatitude(i), bestCurrentInstruction.getPoints().getLongitude(i));

        UpcomingInstruction upcomingInstruction = new UpcomingInstruction(bestNextInstruction, distanceToGo, 
                (long)(bestCurrentInstruction.getMillis()*distanceToGo/bestCurrentInstruction.getDistance()), points);
        return upcomingInstruction;
    }

    double distanceEuclidean(double ax, double ay, double bx, double by) {
        return Math.sqrt((ax-bx)*(ax-bx) + (ay-by)*(ay-by));
    }

    @Override
    public String toString()
    {
        return "distance:" + getDistance() + ", edges:" + edgeIds.size();
    }

    public String toDetailsString()
    {
        String str = "";
        for (int i = 0; i < edgeIds.size(); i++)
        {
            if (i > 0)
                str += "->";

            str += edgeIds.get(i);
        }
        return toString() + ", " + str;
    }
}
