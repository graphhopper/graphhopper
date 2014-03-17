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

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.PriorityQueue;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TurnWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p/>
 * @author Peter Karich
 */
public class Dijkstra extends AbstractRoutingAlgorithm
{
    private TIntObjectMap<EdgeEntry> fromMap;
    private PriorityQueue<EdgeEntry> fromHeap;
    private int visitedNodes;
    private int to = -1;
    private EdgeEntry currEdge;

    public Dijkstra( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        super(g, encoder, weighting);
        initCollections(1000);
    }

    protected void initCollections( int size )
    {
        fromHeap = new PriorityQueue<EdgeEntry>(size);
        fromMap = new TIntObjectHashMap<EdgeEntry>(size);
    }

    @Override
    public Path calcPath( int from, int to )
    {
        checkAlreadyRun();
        this.to = to;
        currEdge = createEdgeEntry(from, 0);
        if (isTraversalNodeBased())
        {
            fromMap.put(from, currEdge);
        }
        return runAlgo();
    }

    private Path runAlgo()
    {
        EdgeExplorer explorer = outEdgeExplorer;
        while (true)
        {
            visitedNodes++;
            if (finished())
                break;

            int startNode = currEdge.adjNode;
            EdgeIterator iter = explorer.setBaseNode(startNode);
            while (iter.next())
            {
                if (!accept(iter, currEdge))
                    continue;
                // minor speed up
                if (currEdge.edge == iter.getEdge())
                    continue;

                int iterationKey = createIdentifier(iter, false);
                double tmpWeight = weighting.calcWeight(iter, false) + currEdge.weight;

                if (weighting instanceof TurnWeighting)
                {
                    tmpWeight += ((TurnWeighting) weighting).calcTurnWeight(currEdge.edge, startNode, iter.getEdge(), false);
                }

                EdgeEntry nEdge = fromMap.get(iterationKey);
                if (nEdge == null)
                {
                    nEdge = new EdgeEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    nEdge.parent = currEdge;
                    fromMap.put(iterationKey, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight)
                {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                }

                updateShortest(nEdge, iterationKey);
            }

            if (fromHeap.isEmpty())
                return createEmptyPath();

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        return extractPath();
    }

    @Override
    protected boolean finished()
    {
        return currEdge.adjNode == to;
    }

    @Override
    protected Path extractPath()
    {
        if (currEdge == null || !finished())
            return createEmptyPath();
        return new Path(graph, flagEncoder).setWeight(currEdge.weight).setEdgeEntry(currEdge).extract();
    }

    @Override
    public String getName()
    {
        return "dijkstra";
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }

    @Override
    boolean isTraversalModeSupported( TRAVERSAL_MODE aTraversalMode )
    {
        return aTraversalMode == TRAVERSAL_MODE.NODE_BASED || // 
                aTraversalMode == TRAVERSAL_MODE.EDGE_BASED || //
                aTraversalMode == TRAVERSAL_MODE.EDGE_BASED_DIRECTION_SENSITIVE;
    }
}
