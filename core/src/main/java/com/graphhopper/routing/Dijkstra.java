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
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p/>
 * @author Peter Karich
 */
public class Dijkstra extends AbstractRoutingAlgorithm
{
    protected TIntObjectMap<EdgeEntry> map = new TIntObjectHashMap<EdgeEntry>();
    protected PriorityQueue<EdgeEntry> heap = new PriorityQueue<EdgeEntry>();
    protected boolean alreadyRun;
    protected int visitedNodes;

    public Dijkstra( Graph graph, FlagEncoder encoder, WeightCalculation type )
    {
        super(graph, encoder, type);
    }

    @Override
    public Path calcPath( int from, int to )
    {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
        EdgeEntry fromEdge = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0d);
        map.put(from, fromEdge);
        EdgeEntry currEdge = calcEdgeEntry(fromEdge, to);
        if (currEdge == null || currEdge.endNode != to)
            return new Path(graph, flagEncoder);

        return extractPath(currEdge);
    }

    public EdgeEntry calcEdgeEntry( EdgeEntry currEdge, int to )
    {       
        EdgeExplorer explorer = outEdgeExplorer;
        while (true)
        {
            visitedNodes++;
            if (finished(currEdge, to))
                break;

            int neighborNode = currEdge.endNode;
            explorer.setBaseNode(neighborNode);
            while (explorer.next())
            {
                if (!accept(explorer))
                    continue;

                int tmpNode = explorer.getAdjNode();
                double tmpWeight = weightCalc.getWeight(explorer.getDistance(), explorer.getFlags()) + currEdge.weight;
                EdgeEntry nEdge = map.get(tmpNode);
                if (nEdge == null)
                {
                    nEdge = new EdgeEntry(explorer.getEdge(), tmpNode, tmpWeight);
                    nEdge.parent = currEdge;
                    map.put(tmpNode, nEdge);
                    heap.add(nEdge);
                } else if (nEdge.weight > tmpWeight)
                {
                    heap.remove(nEdge);
                    nEdge.edge = explorer.getEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    heap.add(nEdge);
                }

                updateShortest(nEdge, neighborNode);
            }

            if (heap.isEmpty())
                return null;

            currEdge = heap.poll();
            if (currEdge == null)
                throw new AssertionError("null currEdge cannot happen?");
        }
        return currEdge;
    }

    protected boolean finished( EdgeEntry currEdge, int to )
    {
        return currEdge.endNode == to;
    }

    public Path extractPath( EdgeEntry goalEdge )
    {
        return new Path(graph, flagEncoder).setEdgeEntry(goalEdge).extract();
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
}
