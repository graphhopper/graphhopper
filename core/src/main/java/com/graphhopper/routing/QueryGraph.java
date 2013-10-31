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

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * A class which is used to query the underlying graph with real GPS points. It does so by
 * introducing virtual nodes and edges. It is lightweight in order to be created every time a new
 * query comes in, which makes the behaviour thread safe.
 * <p/>
 * @author Peter Karich
 */
public class QueryGraph implements Graph
{
    private final static int E_BASE = 0, E_BASE_REV = 1, E_ADJ = 2, E_ADJ_REV = 3;
    private final Graph mainGraph;
    private final int mainNodes;
    private final int mainEdges;
    private final List<LocationIDResult> queryResults;
    /**
     * Virtual edges are created between existing graph and new virtual tower nodes.
     */
    private final List<EdgeIteratorState> virtualEdges;
    /**
     * Store lat,lon of virtual tower nodes.
     */
    private final List<GHPoint> virtualNodes;
    private final DistanceCalc distCalc = new DistancePlaneProjection();
    /**
     * Every request could have a different filter/vehicle.
     */
    private final EdgeFilter edgeFilter;
    private final Location2IDIndex locIndex;

    public QueryGraph( Location2IDIndex locIndex, Graph graph, EdgeFilter edgeFilter )
    {
        this.mainGraph = graph;
        this.locIndex = locIndex;
        this.edgeFilter = edgeFilter;
        mainNodes = graph.getNodes();
        mainEdges = graph.getAllEdges().getMaxId();
        virtualEdges = new ArrayList<EdgeIteratorState>(10);
        virtualNodes = new ArrayList<GHPoint>(5);
        queryResults = new ArrayList<LocationIDResult>();
    }

    /**
     * Searches for the specified location. Calculates snapped points and creates virtual nodes and
     * edges if necessary.
     * <p>
     * @return a query result initialized with a real or virtual node and edge id
     */
    public LocationIDResult lookup( double lat, double lon )
    {
        // To handle multiple results one the same edge properly we have to use *this* QueryGraph,
        // which already contains virtual edges and nodes!
        LocationIDResult res = locIndex.findClosest(this, lat, lon, edgeFilter);
        lookup(res);
        return res;
    }

    /**
     * Made separately available for testing. Searches with an already initialized location result
     * skipping the locationIndex request. Calculates snapped points and creates virtual nodes and
     * edges if necessary.
     */
    public void lookup( LocationIDResult res )
    {
        if (res.getClosestEdge() == null || res.getWayIndex() < 0)
            throw new IllegalStateException("State of LocationIDResult " + res + " is invalid. Missing closestEdge or wayIndex! " + res);

        boolean onEdge = res.calcSnappedPoint(distCalc);

        // Do not create virtual node for a query result if directly on a tower node
        if (res.isOnTowerNode())
            return;

        GHPoint snapped = res.getSnappedPoint();

        EdgeIteratorState closestEdge = res.getClosestEdge();
        PointList fullPL = res.getClosestEdge().fetchWayGeometry(3);
        int max = res.getWayIndex() + 1;
        PointList basePoints = new PointList(max + 1);
        for (int i = 0; i < max; i++)
        {
            basePoints.add(fullPL.getLatitude(i), fullPL.getLongitude(i));
        }
        if (onEdge)
            basePoints.add(snapped.lat, snapped.lon);

        max = fullPL.getSize();
        PointList adjPoints = new PointList(max - res.getWayIndex());
        int i = res.getWayIndex();
        if (onEdge)
        {
            adjPoints.add(snapped.lat, snapped.lon);
            i++;
        }

        for (; i < max; i++)
        {
            adjPoints.add(fullPL.getLatitude(i), fullPL.getLongitude(i));
        }

        PointList baseReversePoints = basePoints.clone(true);
        PointList adjReversePoints = adjPoints.clone(true);

        double baseDistance = basePoints.calcDistance(distCalc);
        double adjDistance = adjPoints.calcDistance(distCalc);

        int newVirtEdgeId = virtualEdges.size() + mainEdges;
        int newVirtNodeId = virtualNodes.size() + mainNodes;
        int base = closestEdge.getBaseNode();
        int adj = closestEdge.getAdjNode();
        EdgeIteratorState reverseEdge = this.getEdgeProps(closestEdge.getEdge(), base);
        if (reverseEdge == null)
            throw new IllegalStateException("edge " + closestEdge.getEdge() + " with base node " + base + " does not exist?");

        int swapped = reverseEdge.getFlags();

        // edges between base and snapped point
        EdgeIteratorState baseEdge = new VirtualEdgeIState(newVirtEdgeId + E_BASE, base, newVirtNodeId,
                baseDistance, closestEdge.getFlags(), closestEdge.getName(), basePoints);
        EdgeIteratorState baseReverseEdge = new VirtualEdgeIState(newVirtEdgeId + E_BASE_REV, newVirtNodeId, base,
                baseDistance, swapped, closestEdge.getName(), baseReversePoints);

        if (base >= mainNodes)
            replaceEdges(base, adj, baseEdge, baseReverseEdge);

        virtualEdges.add(baseEdge);
        virtualEdges.add(baseReverseEdge);

        // edges between snapped point and adjacent node
        EdgeIteratorState adjEdge = new VirtualEdgeIState(newVirtEdgeId + E_ADJ, newVirtNodeId, adj,
                adjDistance, closestEdge.getFlags(), closestEdge.getName(), adjPoints);
        EdgeIteratorState adjReverseEdge = new VirtualEdgeIState(newVirtEdgeId + E_ADJ_REV, adj, newVirtNodeId,
                adjDistance, swapped, closestEdge.getName(), adjReversePoints);

        if (adj >= mainNodes)
            replaceEdges(adj, base, adjEdge, adjReverseEdge);

        virtualEdges.add(adjEdge);
        virtualEdges.add(adjReverseEdge);

        virtualNodes.add(snapped);
        queryResults.add(res);
        res.setClosestNode(newVirtNodeId);
    }

    void replaceEdges( int existingNode, int neighborNode, EdgeIteratorState edge, EdgeIteratorState edgeReverse)
    {
        LocationIDResult res = queryResults.get(existingNode - mainNodes);
        if (res.getClosestEdge().getBaseNode() == neighborNode)
        {
            virtualEdges.set(existingNode + E_BASE_REV - mainNodes, edge);
            virtualEdges.set(existingNode + E_BASE - mainNodes, edgeReverse);
        } else if (res.getClosestEdge().getAdjNode() == neighborNode)
        {
            virtualEdges.set(existingNode + E_ADJ_REV - mainNodes, edge);
            virtualEdges.set(existingNode + E_ADJ - mainNodes, edgeReverse);
        } else
            throw new IllegalStateException(neighborNode + " not found in " + res.getClosestEdge());

    }

    @Override
    public int getNodes()
    {
        return virtualNodes.size() + mainNodes;
    }

    @Override
    public double getLatitude( int nodeId )
    {
        if (nodeId >= mainNodes)
            return virtualNodes.get(nodeId - mainNodes).lat;
        return mainGraph.getLatitude(nodeId);
    }

    @Override
    public double getLongitude( int nodeId )
    {
        if (nodeId >= mainNodes)
            return virtualNodes.get(nodeId - mainNodes).lon;
        return mainGraph.getLongitude(nodeId);
    }

    @Override
    public BBox getBounds()
    {
        return mainGraph.getBounds();
    }

    @Override
    public EdgeIteratorState getEdgeProps( int origEdgeId, int adjNode )
    {
        if (origEdgeId < mainEdges)
            return mainGraph.getEdgeProps(origEdgeId, adjNode);

        int edgeId = origEdgeId - mainEdges;
        EdgeIteratorState eis = virtualEdges.get(edgeId);
        if (eis.getAdjNode() == adjNode)
            return eis;

        if (edgeId % 2 == 0)
            edgeId++;
        else
            edgeId--;
        eis = virtualEdges.get(edgeId);
        if (eis.getAdjNode() == adjNode)
            return eis;
        throw new IllegalStateException("Edge " + edgeId + " not found with adjNode:" + adjNode);
    }

    @Override
    public EdgeExplorer createEdgeExplorer( final EdgeFilter filter )
    {
        // Iteration over virtual nodes needs to be thread safe if done from different explorer
        // so we need to create the mapping on EVERY call!

        // This needs to be a HashMap (and cannot be an array) as we also need to tweak edges for some mainNodes!
        // The more query points we have the more inefficient this map could be. Hmmh.
        final TIntObjectMap<VirtualEdgeI> node2EdgeIter
                = new TIntObjectHashMap<VirtualEdgeI>(queryResults.size() * 3);

        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!        
        // 2. the connected main nodes needs fresh EdgeIterators too
        final EdgeExplorer mainExplorer = mainGraph.createEdgeExplorer(filter);
        for (int i = 0; i < queryResults.size(); i++)
        {
            // create outgoing edges
            VirtualEdgeI virtEdgeIter = new VirtualEdgeI(2);
            EdgeIteratorState tmp = virtualEdges.get(i * 4 + E_BASE_REV);
            if (filter.accept(tmp))
                virtEdgeIter.add(tmp);
            tmp = virtualEdges.get(i * 4 + E_ADJ);
            if (filter.accept(tmp))
                virtEdgeIter.add(tmp);

            int virtNode = mainNodes + i;
            if (virtEdgeIter.count() == 0)
                throw new IllegalStateException("No edges created for virtual node:" + virtNode);

            node2EdgeIter.put(virtNode, virtEdgeIter);

            // replace edge list of neighboring nodes
            LocationIDResult locQueryRes = queryResults.get(i);
            node2EdgeIter.put(locQueryRes.getClosestEdge().getBaseNode(), createVirtualEdge(mainExplorer, filter, true, i));
            node2EdgeIter.put(locQueryRes.getClosestEdge().getAdjNode(), createVirtualEdge(mainExplorer, filter, false, i));
        }

        return new EdgeExplorer()
        {
            @Override
            public EdgeIterator setBaseNode( int baseNode )
            {
                VirtualEdgeI iter = node2EdgeIter.get(baseNode);
                if (iter != null)
                    return iter.reset();

                return mainExplorer.setBaseNode(baseNode);
            }
        };
    }

    @Override
    public EdgeExplorer createEdgeExplorer()
    {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public AllEdgesIterator getAllEdges()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Graph copyTo( Graph g
    )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setNode( int node, double lat, double lon )
    {
        throw exc();
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, int flags )
    {
        throw exc();
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, boolean bothDirections )
    {
        throw exc();
    }

    @Override
    public void markNodeRemoved( int index )
    {
        throw exc();
    }

    @Override
    public boolean isNodeRemoved( int index )
    {
        throw exc();
    }

    @Override
    public void optimize()
    {
        throw exc();
    }

    private UnsupportedOperationException exc()
    {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }

    /**
     * Creates a fake edge iterator pointing to multiple edge states.
     */
    private VirtualEdgeI createVirtualEdge( EdgeExplorer mainExpl, EdgeFilter filter, boolean base, int virtNode )
    {
        VirtualEdgeI vIter = new VirtualEdgeI(10);
        int fromNode;
        int ignoreNode;
        EdgeIteratorState edgeBase = virtualEdges.get(virtNode * 4 + E_BASE);

        if (base)
        {
            EdgeIteratorState edgeAdj = virtualEdges.get(virtNode * 4 + E_ADJ);
            ignoreNode = edgeAdj.getAdjNode();

            fromNode = edgeBase.getBaseNode();
            if (filter.accept(edgeBase))
                vIter.add(edgeBase);
        } else
        {
            ignoreNode = edgeBase.getBaseNode();

            EdgeIteratorState edgeAdjRev = virtualEdges.get(virtNode * 4 + E_ADJ_REV);
            fromNode = edgeAdjRev.getBaseNode();
            if (filter.accept(edgeAdjRev))
                vIter.add(edgeAdjRev);
        }
        EdgeIterator iter = mainExpl.setBaseNode(fromNode);
        while (iter.next())
        {
            if (ignoreNode != iter.getAdjNode())
                vIter.add(iter.detach());
        }
        return vIter;
    }

    private static class VirtualEdgeI implements EdgeIterator
    {
        private final List<EdgeIteratorState> edges;
        private int current;

        public VirtualEdgeI( int edgeCount )
        {
            edges = new ArrayList<EdgeIteratorState>(edgeCount);
        }

        void add( EdgeIteratorState edge )
        {
            edges.add(edge);
        }

        EdgeIterator reset()
        {
            current = -1;
            return this;
        }

        int count()
        {
            return edges.size();
        }

        @Override
        public boolean next()
        {
            current++;
            return current < edges.size();
        }

        @Override
        public EdgeIteratorState detach()
        {
            return edges.get(current);
        }

        @Override
        public int getEdge()
        {
            return edges.get(current).getEdge();
        }

        @Override
        public int getBaseNode()
        {
            return edges.get(current).getBaseNode();
        }

        @Override
        public int getAdjNode()
        {
            return edges.get(current).getAdjNode();
        }

        @Override
        public PointList fetchWayGeometry( int mode )
        {
            return edges.get(current).fetchWayGeometry(mode);
        }

        @Override
        public void setWayGeometry( PointList list )
        {
            edges.get(current).setWayGeometry(list);
        }

        @Override
        public double getDistance()
        {
            return edges.get(current).getDistance();
        }

        @Override
        public void setDistance( double dist )
        {
            edges.get(current).setDistance(dist);
        }

        @Override
        public int getFlags()
        {
            return edges.get(current).getFlags();
        }

        @Override
        public void setFlags( int flags )
        {
            edges.get(current).setFlags(flags);
        }

        @Override
        public String getName()
        {
            return edges.get(current).getName();
        }

        @Override
        public void setName( String name )
        {
            edges.get(current).setName(name);
        }

        @Override
        public String toString()
        {
            return edges.toString();
        }                
    }

    /**
     * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
     */
    private static class VirtualEdgeIState implements EdgeIteratorState, /* for isShortcut only: */ EdgeSkipIterator
    {
        private final PointList pointList;
        private final int edgeId;
        private double distance;
        private int flags;
        private String name;
        private final int baseNode;
        private final int adjNode;

        public VirtualEdgeIState( int edgeId, int baseNode, int adjNode,
                double distance, int flags, String name, PointList pointList )
        {
            this.edgeId = edgeId;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.distance = distance;
            this.flags = flags;
            this.name = name;
            this.pointList = pointList;
        }

        @Override
        public int getEdge()
        {
            return edgeId;
        }

        @Override
        public int getBaseNode()
        {
            return baseNode;
        }

        @Override
        public int getAdjNode()
        {
            return adjNode;
        }

        @Override
        public PointList fetchWayGeometry( int mode )
        {
            if (pointList.getSize() == 0)
                return PointList.EMPTY;

            // due to API we need to create a new instance per call!
            if (mode == 3)
                return pointList.clone(true);
            else if (mode == 1)
                return pointList.copy(0, pointList.getSize() - 1);
            else if (mode == 2)
                return pointList.copy(1, pointList.getSize());
            else if (mode == 0)
            {
                if (pointList.getSize() == 1)
                    return PointList.EMPTY;
                return pointList.copy(1, pointList.getSize() - 1);
            }

            throw new UnsupportedOperationException("Illegal mode:" + mode);
        }

        @Override
        public void setWayGeometry( PointList list )
        {
            throw new UnsupportedOperationException("Not supported for in-memory edge. Set when creating it.");
        }

        @Override
        public double getDistance()
        {
            return distance;
        }

        @Override
        public void setDistance( double dist )
        {
            this.distance = dist;
        }

        @Override
        public int getFlags()
        {
            return flags;
        }

        @Override
        public void setFlags( int flags )
        {
            this.flags = flags;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public void setName( String name )
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return baseNode + "->" + adjNode;
        }

        @Override
        public boolean isShortcut()
        {
            return false;
        }

        @Override
        public int getSkippedEdge1()
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public int getSkippedEdge2()
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public void setSkippedEdges( int edge1, int edge2 )
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean next()
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public EdgeIteratorState detach()
        {
            throw new UnsupportedOperationException("Not supported.");
        }                
    }
}
