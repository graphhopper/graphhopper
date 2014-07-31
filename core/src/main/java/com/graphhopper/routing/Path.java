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
import com.graphhopper.storage.NodeAccess;
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
    private static final AngleCalc ac = new AngleCalc();
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
    private NodeAccess nodeAccess;

    public Path( Graph graph, FlagEncoder encoder )
    {
        this.weight = Double.MAX_VALUE;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
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
        if (isFound())
            throw new IllegalStateException("Extract can only be called once");

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
    protected void processEdge( int edgeId, int adjNode )
    {
        EdgeIteratorState iter = graph.getEdgeProps(edgeId, adjNode);
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
        if (revert && !encoder.isBool(flags, FlagEncoder.K_BACKWARD)
                || !revert && !encoder.isBool(flags, FlagEncoder.K_FORWARD))
            throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. "
                    + "Reverse:" + revert + ", fwd:" + encoder.isBool(flags, FlagEncoder.K_FORWARD) + ", bwd:" + encoder.isBool(flags, FlagEncoder.K_BACKWARD));

        double speed = revert ? encoder.getReverseSpeed(flags) : encoder.getSpeed(flags);
        if (Double.isInfinite(speed) || Double.isNaN(speed))
            throw new IllegalStateException("Invalid speed stored in edge!");

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
            EdgeIteratorState edgeBase = graph.getOriginalGraph().getEdgeProps(edgeIds.get(i), tmpNode);
            if (edgeBase == null)
                throw new IllegalStateException("Edge " + edgeIds.get(i) + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());

            tmpNode = edgeBase.getBaseNode();
            // more efficient swap, currently not implemented for virtual edges: visitor.next(edgeBase.detach(true), i);
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
     * This method calculated a list of points for this path
     * <p>
     * @return this path its geometry (cached)
     */
    public PointList calcPoints()
    {
        if (cachedPoints != null)
            return cachedPoints;

        cachedPoints = new PointList(edgeIds.size() + 1, nodeAccess.is3D());
        if (edgeIds.isEmpty())
            return cachedPoints;

        int tmpNode = getFromNode();
        cachedPoints.add(nodeAccess, tmpNode);
        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeIteratorState eb, int index )
            {
                PointList pl = eb.fetchWayGeometry(2);
                for (int j = 0; j < pl.getSize(); j++)
                {
                    cachedPoints.add(pl, j);
                }
            }
        });
        return cachedPoints;
    }

    /**
     * @return the list of instructions for this path.
     */
    public InstructionList calcInstructions( final Translation tr )
    {
        cachedWays = new InstructionList(edgeIds.size() / 4, tr);
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
            private double prevLat = nodeAccess.getLatitude(tmpNode);
            private double prevLon = nodeAccess.getLongitude(tmpNode);
            private double prevOrientation;
            private Instruction prevInstruction;
            private PointList points = new PointList(10, nodeAccess.is3D());
            private String name = null;
            private InstructionAnnotation annotation;

            @Override
            public void next( EdgeIteratorState edge, int index )
            {
                // baseNode is the current node and adjNode is the next
                int adjNode = edge.getAdjNode();
                long flags = edge.getFlags();
                double adjLat = nodeAccess.getLatitude(adjNode);
                double adjLon = nodeAccess.getLongitude(adjNode);
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
                    prevLat = nodeAccess.getLatitude(baseNode);
                    prevLon = nodeAccess.getLongitude(baseNode);
                }

                double orientation = ac.calcOrientation(prevLat, prevLon, latitude, longitude);
                if (name == null)
                {
                    // very first instruction
                    name = edge.getName();
                    annotation = encoder.getAnnotation(flags, tr);
                    prevInstruction = new Instruction(Instruction.CONTINUE_ON_STREET, name, annotation, points);
                    updatePointsAndInstruction(edge, wayGeo);
                    cachedWays.add(prevInstruction);
                } else
                {
                    double tmpOrientation = ac.alignOrientation(prevOrientation, orientation);
                    String tmpName = edge.getName();
                    InstructionAnnotation tmpAnnotation = encoder.getAnnotation(flags, tr);
                    if ((!name.equals(tmpName))
                            || (!annotation.equals(tmpAnnotation)))
                    {
                        points = new PointList(10, nodeAccess.is3D());
                        name = tmpName;
                        annotation = tmpAnnotation;
                        double delta = Math.abs(tmpOrientation - prevOrientation);
                        int sign;
                        if (delta < 0.2)
                        {
                            // 0.2 ~= 11°
                            sign = Instruction.CONTINUE_ON_STREET;

                        } else if (delta < 0.8)
                        {
                            // 0.8 ~= 40°
                            if (tmpOrientation > prevOrientation)
                                sign = Instruction.TURN_SLIGHT_LEFT;
                            else
                                sign = Instruction.TURN_SLIGHT_RIGHT;

                        } else if (delta < 1.8)
                        {
                            // 1.8 ~= 103°
                            if (tmpOrientation > prevOrientation)
                                sign = Instruction.TURN_LEFT;
                            else
                                sign = Instruction.TURN_RIGHT;

                        } else
                        {
                            if (tmpOrientation > prevOrientation)
                                sign = Instruction.TURN_SHARP_LEFT;
                            else
                                sign = Instruction.TURN_SHARP_RIGHT;

                        }

                        prevInstruction = new Instruction(sign, name, annotation, points);
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
                    prevOrientation = ac.calcOrientation(wayGeo.getLatitude(beforeLast), wayGeo.getLongitude(beforeLast),
                            adjLat, adjLon);
                }

                boolean lastEdge = index == edgeIds.size() - 1;
                if (lastEdge)
                    cachedWays.add(new FinishInstruction(adjLat, adjLon,
                            nodeAccess.is3D() ? nodeAccess.getElevation(adjNode) : 0));
            }

            private void updatePointsAndInstruction( EdgeIteratorState edge, PointList pl )
            {
                // skip adjNode
                int len = pl.size() - 1;
                for (int i = 0; i < len; i++)
                {
                    points.add(pl, i);
                }
                double newDist = edge.getDistance();
                prevInstruction.setDistance(newDist + prevInstruction.getDistance());
                long flags = edge.getFlags();
                prevInstruction.setTime(calcMillis(newDist, flags, false) + prevInstruction.getTime());
            }
        });

        return cachedWays;
    }

    public Instruction findInstruction( double lat, double lon )
    {
        DistanceCalcEarth distanceCalc = new DistanceCalcEarth();

        double distanceToPath = Double.MAX_VALUE;

        int nextInstrNumber = 0;

        // Search the closest edge to the point
        for (int i = 0; i < cachedWays.getSize() - 1; i++)
        {
            double edgeNodeLat1 = cachedWays.get(i).getPoints().getLatitude(0);
            double edgeNodeLon1 = cachedWays.get(i).getPoints().getLongitude(0);
            int node2NOP = cachedWays.get(i + 1).getPoints().getSize();
            double edgeNodeLat2 = cachedWays.get(i + 1).getPoints().getLatitude(node2NOP - 1);
            double edgeNodeLon2 = cachedWays.get(i + 1).getPoints().getLongitude(node2NOP - 1);

            //Calculate the distance from the point to the edge
            double distanceToEdge = distanceCalc.calcNormalizedEdgeDistance(lat, lon, edgeNodeLat1, edgeNodeLon1, edgeNodeLat2, edgeNodeLon2);

            if (distanceToEdge < distanceToPath)
            {
                distanceToPath = distanceToEdge;
                nextInstrNumber = i + 1;
            }
        }

        return cachedWays.get(nextInstrNumber);
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
