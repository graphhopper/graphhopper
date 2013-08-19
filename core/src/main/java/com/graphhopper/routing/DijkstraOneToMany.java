/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.util.Arrays;

/**
 * A simple dijkstra tuned to perform one to many queries more efficient than DijkstraSimple. Old
 * data structures are cache between requests and potentially reused. Useful for CH preparation.
 * <p/>
 * @author Peter Karich
 */
public class DijkstraOneToMany extends AbstractRoutingAlgorithm
{
    protected double[] weights;
    private TIntList changedNodes;
    private int[] parents;
    private int[] edgeIds;
    private IntDoubleBinHeap heap;
    private int visitedNodes;
    private boolean doClear = true;
    private double limit = Double.MAX_VALUE;

    public DijkstraOneToMany( Graph graph, FlagEncoder encoder )
    {
        super(graph, encoder);
        parents = new int[graph.getNodes()];
        Arrays.fill(parents, -1);

        weights = new double[graph.getNodes()];
        Arrays.fill(weights, Double.MAX_VALUE);
        heap = new IntDoubleBinHeap();
        changedNodes = new TIntArrayList();
    }

    public DijkstraOneToMany setLimit( double weight )
    {
        limit = weight;
        return this;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        if (edgeIds == null)
        {
            edgeIds = new int[graph.getNodes()];
            Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);
        }
        int endNode = findEndNode(from, to);
        PathNative p = new PathNative(graph, flagEncoder, parents, edgeIds);
        p.setFromNode(from);
        if (endNode < 0)
        {
            return p;
        }
        return p.setEndNode(endNode).extract();
    }

    public DijkstraOneToMany clear()
    {
        doClear = true;
        return this;
    }

    public double getWeight( int endNode )
    {
        return weights[endNode];
    }

    public int findEndNode( int from, int to )
    {
        if (weights.length < 2)
            return -1;
        
        int currNode = from;
        if (doClear)
        {
            doClear = false;
            int vn = changedNodes.size();
            for (int i = 0; i < vn; i++)
            {
                int n = changedNodes.get(i);
                weights[n] = Double.MAX_VALUE;
                parents[n] = -1;
                if (edgeIds != null)
                    edgeIds[n] = EdgeIterator.NO_EDGE;
            }

            heap.clear();
            changedNodes.clear();

            weights[currNode] = 0;
            changedNodes.add(currNode);
        } else
        {
            // Cached! Re-use existing data structures
            int parentNode = parents[to];
            if (parentNode >= 0 || heap.isEmpty())
                return to;
            
            currNode = heap.poll_element();
        }

        visitedNodes = 0;
        if (finished(currNode, to))
            return currNode;
        
        while (true)
        {
            visitedNodes++;
            EdgeIterator iter = graph.getEdges(currNode, outEdgeFilter);
            while (iter.next())
            {
                if (!accept(iter))
                {
                    continue;
                }
                int adjNode = iter.getAdjNode();
                double tmpWeight = weightCalc.getWeight(iter.getDistance(), iter.getFlags()) + weights[currNode];
                if (weights[adjNode] == Double.MAX_VALUE)
                {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.insert_(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    if (edgeIds != null)
                    {
                        edgeIds[adjNode] = iter.getEdge();
                    }
                } else if (weights[adjNode] > tmpWeight)
                {
                    parents[adjNode] = currNode;
                    weights[adjNode] = tmpWeight;
                    heap.update_(tmpWeight, adjNode);
                    changedNodes.add(adjNode);
                    if (edgeIds != null)
                        edgeIds[adjNode] = iter.getEdge();
                }
            }

            if (heap.isEmpty())
                return -1;
            
            // calling just peek() is important for a cached next query
            currNode = heap.peek_element();
            if (finished(currNode, to))
                return currNode;
            
            heap.poll_element();
        }
    }

    public boolean finished( int currNode, int to )
    {
        return weights[currNode] >= limit || currNode == to;
    }
    
    public void close() {
        weights = null;
        parents = null;
        edgeIds = null;
        heap = null;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }

    @Override
    public String getName()
    {
        return "dijkstraOneToMany";
    }
}
