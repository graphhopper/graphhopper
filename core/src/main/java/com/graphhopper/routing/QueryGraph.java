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
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final Graph mainGraph;
    private final int mainNodes;
    private final int mainEdges;
    private List<QueryResult> queryResults;
    /**
     * Virtual edges are created between existing graph and new virtual tower nodes. For every
     * virtual node there are 4 edges: base-snap, snap-base, snap-adj, adj-snap
     */
    private List<EdgeIteratorState> virtualEdges;
    private final static int VE_BASE = 0, VE_BASE_REV = 1, VE_ADJ = 2, VE_ADJ_REV = 3;

    /**
     * Store lat,lon of virtual tower nodes.
     */
    private PointList virtualNodes;
    private final DistanceCalc distCalc = new DistancePlaneProjection();

    public QueryGraph( Graph graph )
    {
        mainGraph = graph;
        mainNodes = graph.getNodes();
        mainEdges = graph.getAllEdges().getMaxId();
    }

    /**
     * For all specified query results calculate snapped point and set closest node and edge to a
     * virtual one if necessary. Additionally the wayIndex can change if an edge is swapped.
     */
    public void lookup( List<QueryResult> resList )
    {
        if (isInitialized())
            throw new IllegalStateException("Call lookup only once. Otherwise you'll have problems for queries sharing the same edge.");

        virtualEdges = new ArrayList<EdgeIteratorState>(resList.size() * 2);
        virtualNodes = new PointList(resList.size());
        queryResults = new ArrayList<QueryResult>(resList.size());

        TIntObjectMap<List<QueryResult>> edge2res = new TIntObjectHashMap<List<QueryResult>>(resList.size());

        // Phase 1
        // calculate snapped point and swap direction of closest edge if necessary
        for (QueryResult res : resList)
        {
            // Do not create virtual node for a query result if it is directly on a tower node or not found
            EdgeIteratorState closestEdge = res.getClosestEdge();
            if (res.getSnappedPosition() == QueryResult.Position.TOWER || closestEdge == null)
                continue;

            int base = closestEdge.getBaseNode();

            // Force the identical direction for all closest edges. 
            // It is important to sort multiple results for the same edge by its wayIndex
            boolean doReverse = base > closestEdge.getAdjNode();
            if (base == closestEdge.getAdjNode())
            {
                // check for special case #162 where adj == base and force direction via latitude comparison
                PointList pl = closestEdge.fetchWayGeometry(0);
                if (pl.size() > 1)
                    doReverse = pl.getLatitude(0) > pl.getLatitude(pl.size() - 1);
            }

            if (doReverse)
            {
                closestEdge = closestEdge.detach(true);
                PointList fullPL = closestEdge.fetchWayGeometry(3);
                res.setClosestEdge(closestEdge);
                if (res.getSnappedPosition() == QueryResult.Position.PILLAR)
                    // ON pillar node                
                    res.setWayIndex(fullPL.getSize() - res.getWayIndex() - 1);
                else
                    // for case "OFF pillar node"
                    res.setWayIndex(fullPL.getSize() - res.getWayIndex() - 2);

                if (res.getWayIndex() < 0)
                    throw new IllegalStateException("Problem with wayIndex while reversing closest edge:" + closestEdge + ", " + res);
            }

            // find multiple results on same edge
            int edgeId = closestEdge.getEdge();
            List<QueryResult> list = edge2res.get(edgeId);
            if (list == null)
            {
                list = new ArrayList<QueryResult>(5);
                edge2res.put(edgeId, list);
            }
            list.add(res);
        }

        // Phase 2 - now it is clear which points cut one edge
        // 1. create point lists
        // 2. create virtual edges between virtual nodes and its neighbor (virtual or normal nodes)
        edge2res.forEachValue(new TObjectProcedure<List<QueryResult>>()
        {
            @Override
            public boolean execute( List<QueryResult> results )
            {
                // we can expect at least one entry in the results
                EdgeIteratorState closestEdge = results.get(0).getClosestEdge();
                final PointList fullPL = closestEdge.fetchWayGeometry(3);
                int baseNode = closestEdge.getBaseNode();
                // sort results on the same edge by the wayIndex and if equal by distance to pillar node
                Collections.sort(results, new Comparator<QueryResult>()
                {
                    @Override
                    public int compare( QueryResult o1, QueryResult o2 )
                    {
                        int diff = o1.getWayIndex() - o2.getWayIndex();
                        if (diff == 0)
                        {
                            // sort by distance from snappedPoint to fullPL.get(wayIndex) if wayIndex is identical
                            GHPoint p1 = o1.getSnappedPoint();
                            GHPoint p2 = o2.getSnappedPoint();
                            if (p1.equals(p2))
                                return 0;

                            double fromLat = fullPL.getLatitude(o1.getWayIndex());
                            double fromLon = fullPL.getLongitude(o1.getWayIndex());
                            if (distCalc.calcNormalizedDist(fromLat, fromLon, p1.lat, p1.lon)
                                    > distCalc.calcNormalizedDist(fromLat, fromLon, p2.lat, p2.lon))
                                return 1;
                            return -1;
                        }
                        return diff;
                    }
                });

                GHPoint prevPoint = fullPL.toGHPoint(0);
                int adjNode = closestEdge.getAdjNode();
                long reverseFlags = closestEdge.detach(true).getFlags();
                int prevWayIndex = 1;
                int prevNodeId = baseNode;
                int counter = 0;
                int virtNodeId = virtualNodes.getSize() + mainNodes;
                // Create base and adjacent PointLists for all virtual nodes!
                // We do so via inserting them at the correct position of fullPL and cutting the                
                // fullPL into the right pieces.
                for (QueryResult res : results)
                {
                    if (res.getClosestEdge().getBaseNode() != baseNode)
                        throw new IllegalStateException("Base nodes have to be identical but were not: " + closestEdge + " vs " + res.getClosestEdge());

                    queryResults.add(res);
                    GHPoint currSnapped = res.getSnappedPoint();
                    createEdges(prevPoint, prevWayIndex,
                            res.getSnappedPoint(), res.getWayIndex(),
                            fullPL, closestEdge, prevNodeId, virtNodeId, reverseFlags);
                    virtualNodes.add(currSnapped.lat, currSnapped.lon);

                    // add edges again to set adjacent edges for newVirtNodeId
                    if (counter > 0)
                    {
                        virtualEdges.add(virtualEdges.get(virtualEdges.size() - 2));
                        virtualEdges.add(virtualEdges.get(virtualEdges.size() - 2));
                    }

                    res.setClosestNode(virtNodeId);
                    prevNodeId = virtNodeId;
                    prevWayIndex = res.getWayIndex() + 1;
                    prevPoint = currSnapped;
                    counter++;
                    virtNodeId++;
                }

                // two edges between last result and adjacent node are still missing
                createEdges(prevPoint, prevWayIndex, fullPL.toGHPoint(fullPL.getSize() - 1), fullPL.getSize() - 1,
                        fullPL, closestEdge, virtNodeId - 1, adjNode, reverseFlags);

                return true;
            }
        });
    }

    private void createEdges( GHPoint prevSnapped, int prevWayIndex, GHPoint currSnapped, int wayIndex,
            PointList fullPL, EdgeIteratorState closestEdge,
            int prevNodeId, int nodeId, long reverseFlags )
    {
        int max = wayIndex + 1;
        // basePoints must have at least the size of 2 to make sure fetchWayGeometry(3) returns at least 2
        PointList basePoints = new PointList(max - prevWayIndex + 1);
        basePoints.add(prevSnapped.lat, prevSnapped.lon);
        for (int i = prevWayIndex; i < max; i++)
        {
            basePoints.add(fullPL.getLatitude(i), fullPL.getLongitude(i));
        }       
        basePoints.add(currSnapped.lat, currSnapped.lon);
        
        PointList baseReversePoints = basePoints.clone(true);
        double baseDistance = basePoints.calcDistance(distCalc);
        int virtEdgeId = virtualEdges.size() + mainEdges;        

        // edges between base and snapped point
        VirtualEdgeIState baseEdge = new VirtualEdgeIState(virtEdgeId + VE_BASE, prevNodeId, nodeId,
                baseDistance, closestEdge.getFlags(), closestEdge.getName(), basePoints);
        VirtualEdgeIState baseReverseEdge = new VirtualEdgeIState(virtEdgeId + VE_BASE_REV, nodeId, prevNodeId,
                baseDistance, reverseFlags, closestEdge.getName(), baseReversePoints);

        virtualEdges.add(baseEdge);
        virtualEdges.add(baseReverseEdge);
    }

    @Override
    public int getNodes()
    {
        return virtualNodes.getSize() + mainNodes;
    }

    @Override
    public double getLatitude( int nodeId )
    {
        if (nodeId >= mainNodes)
            return virtualNodes.getLatitude(nodeId - mainNodes);
        return mainGraph.getLatitude(nodeId);
    }

    @Override
    public double getLongitude( int nodeId )
    {
        if (nodeId >= mainNodes)
            return virtualNodes.getLongitude(nodeId - mainNodes);
        return mainGraph.getLongitude(nodeId);
    }

    @Override
    public int getAdditionalNodeField( int nodeId )
    {
        return mainGraph.getAdditionalNodeField(nodeId);
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

        // find reverse edge via convention. see virtualEdges comment above
        if (edgeId % 2 == 0)
            edgeId++;
        else
            edgeId--;
        EdgeIteratorState eis2 = virtualEdges.get(edgeId);
        if (eis2.getAdjNode() == adjNode)
            return eis2;
        throw new IllegalStateException("Edge " + origEdgeId + " not found with adjNode:" + adjNode
                + ". found edges were:" + eis + ", " + eis2);
    }

    @Override
    public EdgeExplorer createEdgeExplorer( final EdgeFilter edgeFilter )
    {
        if (!isInitialized())
            throw new IllegalStateException("Call lookup before using this graph");

        // Iteration over virtual nodes needs to be thread safe if done from different explorer
        // so we need to create the mapping on EVERY call!
        // This needs to be a HashMap (and cannot be an array) as we also need to tweak edges for some mainNodes!
        // The more query points we have the more inefficient this map could be. Hmmh.
        final TIntObjectMap<VirtualEdgeIterator> node2EdgeMap
                = new TIntObjectHashMap<VirtualEdgeIterator>(queryResults.size() * 3);

        final EdgeExplorer mainExplorer = mainGraph.createEdgeExplorer(edgeFilter);
        final TIntHashSet towerNodesToChange = new TIntHashSet(queryResults.size());

        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!        
        for (int i = 0; i < queryResults.size(); i++)
        {
            // create outgoing edges
            VirtualEdgeIterator virtEdgeIter = new VirtualEdgeIterator(2);
            EdgeIteratorState baseRevEdge = virtualEdges.get(i * 4 + VE_BASE_REV);
            if (edgeFilter.accept(baseRevEdge))
                virtEdgeIter.add(baseRevEdge);
            EdgeIteratorState adjEdge = virtualEdges.get(i * 4 + VE_ADJ);
            if (edgeFilter.accept(adjEdge))
                virtEdgeIter.add(adjEdge);

            int virtNode = mainNodes + i;
            node2EdgeMap.put(virtNode, virtEdgeIter);

            // replace edge list of neighboring tower nodes: a) add virtual edges only and collect tower nodes where real edges will be added in step 2.
            // base node
            int towerNode = baseRevEdge.getAdjNode();
            if (towerNode < mainNodes)
            {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(node2EdgeMap, edgeFilter, true, towerNode, i);
            }

            // adj node
            towerNode = adjEdge.getAdjNode();
            if (towerNode < mainNodes)
            {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(node2EdgeMap, edgeFilter, false, towerNode, i);
            }
        }

        // 2. the connected tower nodes from mainGraph need fresh EdgeIterators with possible fakes
        // where 'fresh' means independent of previous call and respecting the edgeFilter
        // -> setup fake iterators of detected tower nodes (virtual edges are already added)
        towerNodesToChange.forEach(new TIntProcedure()
        {
            @Override
            public boolean execute( int value )
            {
                fillVirtualEdges(node2EdgeMap, value, mainExplorer);
                return true;
            }
        });

        return new EdgeExplorer()
        {
            @Override
            public EdgeIterator setBaseNode( int baseNode )
            {
                VirtualEdgeIterator iter = node2EdgeMap.get(baseNode);
                if (iter != null)
                    return iter.reset();

                return mainExplorer.setBaseNode(baseNode);
            }
        };
    }

    /**
     * Creates a fake edge iterator pointing to multiple edge states.
     */
    private void addVirtualEdges( TIntObjectMap<VirtualEdgeIterator> node2EdgeMap, EdgeFilter filter, boolean base,
            int node, int virtNode )
    {
        VirtualEdgeIterator existingIter = node2EdgeMap.get(node);
        if (existingIter == null)
        {
            existingIter = new VirtualEdgeIterator(10);
            node2EdgeMap.put(node, existingIter);
        }
        EdgeIteratorState edge = base
                ? virtualEdges.get(virtNode * 4 + VE_BASE)
                : virtualEdges.get(virtNode * 4 + VE_ADJ_REV);
        if (filter.accept(edge))
            existingIter.add(edge);
    }

    void fillVirtualEdges( TIntObjectMap<VirtualEdgeIterator> node2Edge, int towerNode, EdgeExplorer mainExpl )
    {
        if (towerNode >= mainNodes)
            throw new IllegalStateException("should not happen:" + towerNode + ", " + node2Edge);

        VirtualEdgeIterator vIter = node2Edge.get(towerNode);
        TIntArrayList ignoreEdges = new TIntArrayList(vIter.count() * 2);
        while (vIter.next())
        {
            EdgeIteratorState edge = queryResults.get(vIter.getAdjNode() - mainNodes).getClosestEdge();
            ignoreEdges.add(edge.getEdge());
        }
        vIter.reset();
        EdgeIterator iter = mainExpl.setBaseNode(towerNode);
        while (iter.next())
        {
            if (!ignoreEdges.contains(iter.getEdge()))
                vIter.add(iter.detach(false));
        }
    }

    private boolean isInitialized()
    {
        return queryResults != null;
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
    public void setNode( int node, double lat, double lon )
    {
        throw exc();
    }

    @Override
    public void setAdditionalNodeField( int nodeId, int additionalValue )
    {
        exc();
    }

    @Override
    public EdgeIteratorState edge( int a, int b )
    {
        throw exc();
    }

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
    public Graph copyTo( Graph g )
    {
        throw exc();
    }

    private UnsupportedOperationException exc()
    {
        return new UnsupportedOperationException("QueryGraph cannot be modified.");
    }

    static class VirtualEdgeIterator implements EdgeIterator, EdgeSkipIterState
    {

        private final List<EdgeIteratorState> edges;
        private int current;

        public VirtualEdgeIterator( int edgeCount )
        {
            edges = new ArrayList<EdgeIteratorState>(edgeCount);
            reset();
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
        public EdgeIteratorState detach( boolean reverse )
        {
            if (reverse)
                throw new IllegalStateException("Not yet supported");

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
        public EdgeIteratorState setWayGeometry( PointList list )
        {
            return edges.get(current).setWayGeometry(list);
        }

        @Override
        public double getDistance()
        {
            return edges.get(current).getDistance();
        }

        @Override
        public EdgeIteratorState setDistance( double dist )
        {
            return edges.get(current).setDistance(dist);
        }

        @Override
        public long getFlags()
        {
            return edges.get(current).getFlags();
        }

        @Override
        public EdgeIteratorState setFlags( long flags )
        {
            return edges.get(current).setFlags(flags);
        }

        @Override
        public String getName()
        {
            return edges.get(current).getName();
        }

        @Override
        public EdgeIteratorState setName( String name )
        {
            return edges.get(current).setName(name);
        }

        @Override
        public String toString()
        {
            return edges.toString();
        }

        @Override
        public int getAdditionalField()
        {
            return edges.get(current).getAdditionalField();
        }

        @Override
        public EdgeIteratorState setAdditionalField( int value )
        {
            return edges.get(current).setAdditionalField(value);
        }

        @Override
        public void copyProperties( EdgeIteratorState edge )
        {
            edges.get(current).copyProperties(edge);
        }

        @Override
        public boolean isShortcut()
        {
            EdgeIteratorState edge = edges.get(current);
            return edge instanceof EdgeSkipIterState && ((EdgeSkipIterState) edge).isShortcut();
        }

        @Override
        public double getWeight()
        {
            // will be called only from PreparationWeighting and if isShortcut is true
            return ((EdgeSkipIterState) edges.get(current)).getWeight();
        }

        @Override
        public EdgeSkipIterState setWeight( double weight )
        {
            throw new UnsupportedOperationException("Not supported.");
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
    }

    /**
     * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
     */
    private static class VirtualEdgeIState implements EdgeIteratorState, EdgeSkipIterState
    {

        private final PointList pointList;
        private final int edgeId;
        private double distance;
        private long flags;
        private String name;
        private final int baseNode;
        private final int adjNode;

        public VirtualEdgeIState( int edgeId, int baseNode, int adjNode,
                double distance, long flags, String name, PointList pointList )
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
                return pointList.clone(false);
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
        public EdgeIteratorState setWayGeometry( PointList list )
        {
            throw new UnsupportedOperationException("Not supported for virtual edge. Set when creating it.");
        }

        @Override
        public double getDistance()
        {
            return distance;
        }

        @Override
        public EdgeIteratorState setDistance( double dist )
        {
            this.distance = dist;
            return this;
        }

        @Override
        public long getFlags()
        {
            return flags;
        }

        @Override
        public EdgeIteratorState setFlags( long flags )
        {
            this.flags = flags;
            return this;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public EdgeIteratorState setName( String name )
        {
            this.name = name;
            return this;
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
        public int getAdditionalField()
        {
            throw new UnsupportedOperationException("Not supported.");
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
        public EdgeIteratorState detach( boolean reverse )
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public EdgeIteratorState setAdditionalField( int value )
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public void copyProperties( EdgeIteratorState edge )
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public EdgeSkipIterState setWeight( double weight )
        {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public double getWeight()
        {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
