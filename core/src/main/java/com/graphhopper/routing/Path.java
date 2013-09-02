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
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

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
    protected FlagEncoder encoder;
    protected double distance;
    // we go upwards (via EdgeEntry.parent) from the goal node to the origin node
    protected boolean reverseOrder = true;
    protected long time;
    private boolean found;
    protected EdgeEntry edgeEntry;
    StopWatch sw = new StopWatch("extract");
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
        edgeIds = new TIntArrayList(edgeIds);
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
        if (!EdgeIterator.Edge.isValid(fromNode))
        {
            throw new IllegalStateException("Call extract() before retrieving fromNode");
        }
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
        reverseOrder = !reverseOrder;
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
     * @return time in seconds
     */
    public long getTime()
    {
        return time;
    }

    /**
     * This weight will be updated during the algorithm. The initial value is maximum double.
     */
    public double getWeight()
    {
        return weight;
    }

    public void setWeight( double w )
    {
        this.weight = w;
    }

    /**
     * The graph out of which the edge was build
     * @return
     */
    public Graph graph() {
    	return this.graph;
    }
    
    public FlagEncoder encoder() {
    	return this.encoder;
    }
    
    /**
     * Extracts the Path from the shortest-path-tree determined by edgeEntry.
     */
    public Path extract()
    {
        sw.start();
        EdgeEntry goalEdge = edgeEntry;
        setEndNode(goalEdge.endNode);
        while (EdgeIterator.Edge.isValid(goalEdge.edge))
        {
            processDistance(goalEdge.edge, goalEdge.endNode);
            goalEdge = goalEdge.parent;
        }

        setFromNode(goalEdge.endNode);
        reverseOrder();
        sw.stop();
        return setFound(true);
    }

    public String getDebugInfo()
    {
        return sw.toString();
    }

    /**
     * Calls calcDistance and adds the edgeId.
     */
    protected void processDistance( int edgeId, int endNode )
    {
        EdgeBase iter = graph.getEdgeProps(edgeId, endNode);
        distance += calcDistance(iter);
        time += calcTime(iter.getDistance(), iter.getFlags());
        addEdge(edgeId);
    }

    /**
     * This method returns the distance in kilometer for the specified edge.
     */
    protected double calcDistance( EdgeBase iter )
    {
        return iter.getDistance();
    }

    /**
     * Calculates the time in minutes for the specified distance and speed (via flags)
     */
    protected long calcTime( double distance, int flags )
    {
        return (long) (distance * 3.6 / encoder.getSpeed(flags));
    }

    /**
     * Used in combination with forEveryEdge.
     */
    public static interface EdgeVisitor
    {
        void next( EdgeBase edgeBase, int index );
    }

    /**
     * Iterates over all edges in this path and calls the visitor for it.
     */
    public void forEveryEdge( EdgeVisitor visitor )
    {
        int tmpNode = getFromNode();
        int len = edgeIds.size();
        for (int i = 0; i < len; i++)
        {
            EdgeBase edgeBase = graph.getEdgeProps(edgeIds.get(i), tmpNode);
            if (edgeBase == null)
            {
                throw new IllegalStateException("Edge " + edgeIds.get(i)
                        + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());
            }
            tmpNode = edgeBase.getBaseNode();
            visitor.next(edgeBase, i);
        }
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    public TIntList calcNodes()
    {
        final TIntArrayList nodes = new TIntArrayList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
        {
            return nodes;
        }

        int tmpNode = getFromNode();
        nodes.add(tmpNode);
        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeBase eb, int i )
            {
                nodes.add(eb.getBaseNode());
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
        {
            return cachedPoints;
        }
        cachedPoints = new PointList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
        {
            return cachedPoints;
        }
        int tmpNode = getFromNode();
        cachedPoints.add(graph.getLatitude(tmpNode), graph.getLongitude(tmpNode));
        forEveryEdge(new EdgeVisitor()
        {
            @Override
            public void next( EdgeBase eb, int i )
            {
                PointList pl = eb.getWayGeometry();
                pl.reverse();
                for (int j = 0; j < pl.getSize(); j++)
                {
                    cachedPoints.add(pl.getLatitude(j), pl.getLongitude(j));
                }
                int baseNode = eb.getBaseNode();
                cachedPoints.add(graph.getLatitude(baseNode), graph.getLongitude(baseNode));
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
        {
            return cachedWays;
        }
        cachedWays = new InstructionList(edgeIds.size() / 4);
        if (edgeIds.isEmpty())
        {
            return cachedWays;
        }

        final int tmpNode = getFromNode();
        forEveryEdge(new EdgeVisitor()
        {
            String name = null;
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
            double prevLat = graph.getLatitude(tmpNode);
            double prevLon = graph.getLongitude(tmpNode);
            double prevOrientation;
            double prevDist;

            @Override
            public void next( EdgeBase edgeBase, int index )
            {                
                // Hmmh, a bit ugly: 'iter' links to the previous node of the path!
                // Ie. baseNode is the current node and adjNode is the previous.
                int baseNode = edgeBase.getBaseNode();
                double baseLat = graph.getLatitude(baseNode);
                double baseLon = graph.getLongitude(baseNode);
                double latitude, longitude;
                PointList wayGeo = edgeBase.getWayGeometry();
                if (wayGeo.isEmpty())
                {
                    latitude = baseLat;
                    longitude = baseLon;
                } else
                {
                    int adjNode = edgeBase.getAdjNode();
                    prevLat = graph.getLatitude(adjNode);
                    prevLon = graph.getLongitude(adjNode);
                    latitude = wayGeo.getLatitude(wayGeo.getSize() - 1);
                    longitude = wayGeo.getLongitude(wayGeo.getSize() - 1);
                }

                double orientation = Math.atan2(latitude - prevLat, longitude - prevLon);
                if (name == null)
                {
                    name = edgeBase.getName();
                    prevDist = calcDistance(edgeBase);
                    cachedWays.add(InstructionList.CONTINUE_ON_STREET, name, prevDist);
                } else
                {
                    double tmpOrientation;
                    if (prevOrientation >= 0)
                    {
                        if (orientation < -Math.PI + prevOrientation)
                        {
                            tmpOrientation = orientation + 2 * Math.PI;
                        } else
                        {
                            tmpOrientation = orientation;
                        }
                    } else
                    {
                        if (orientation > +Math.PI + prevOrientation)
                        {
                            tmpOrientation = orientation - 2 * Math.PI;
                        } else
                        {
                            tmpOrientation = orientation;
                        }
                    }
                                                           
                    String tmpName = edgeBase.getName();
                    if (!name.equals(tmpName))
                    {
                        cachedWays.updateLastDistance(prevDist);
                        prevDist = calcDistance(edgeBase);
                        name = tmpName;
                        double delta = Math.abs(tmpOrientation - prevOrientation);
                        if (delta < 0.2)
                        {
                            // 0.2 ~= 11°
                            cachedWays.add(InstructionList.CONTINUE_ON_STREET, name, prevDist);

                        } else if (delta < 0.8)
                        {
                            // 0.8 ~= 40°
                            if (tmpOrientation > prevOrientation)
                            {
                                cachedWays.add(InstructionList.TURN_SLIGHT_LEFT, name, prevDist);
                            } else
                            {
                                cachedWays.add(InstructionList.TURN_SLIGHT_RIGHT, name, prevDist);
                            }
                        } else if (delta < 1.8)
                        {
                            // 1.8 ~= 103°
                            if (tmpOrientation > prevOrientation)
                            {
                                cachedWays.add(InstructionList.TURN_LEFT, name, prevDist);
                            } else
                            {
                                cachedWays.add(InstructionList.TURN_RIGHT, name, prevDist);
                            }
                        } else
                        {
                            if (tmpOrientation > prevOrientation)
                            {
                                cachedWays.add(InstructionList.TURN_SHARP_LEFT, name, prevDist);
                            } else
                            {
                                cachedWays.add(InstructionList.TURN_SHARP_RIGHT, name, prevDist);
                            }
                        }
                    } else
                        prevDist += calcDistance(edgeBase);
                }

                prevLat = baseLat;
                prevLon = baseLon;
                if (wayGeo.isEmpty())
                {
                    prevOrientation = orientation;
                } else
                {
                    prevOrientation = Math.atan2(baseLat - wayGeo.getLatitude(0), baseLon - wayGeo.getLongitude(0));
                }
                
                boolean lastEdgeIter = index == edgeIds.size() - 1;
                if(lastEdgeIter)
                    cachedWays.updateLastDistance(prevDist);
            }
        });

        return cachedWays;
    }

    public TIntSet calculateIdenticalNodes( Path p2 )
    {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        TIntList nodes = calcNodes();
        int max = nodes.size();
        for (int i = 0; i < max; i++)
        {
            thisSet.add(nodes.get(i));
        }

        nodes = p2.calcNodes();
        max = nodes.size();
        for (int i = 0; i < max; i++)
        {
            if (thisSet.contains(nodes.get(i)))
            {
                retSet.add(nodes.get(i));
            }
        }
        return retSet;
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
            {
                str += "->";
            }

            str += edgeIds.get(i);
        }
        return toString() + ", " + str;
    }
}
