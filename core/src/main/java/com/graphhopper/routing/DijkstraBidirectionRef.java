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
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Calculates best path in bidirectional way.
 * <p/>
 * 'Ref' stands for reference implementation and is using the normal Java-'reference'-way.
 * <p/>
 * @see DijkstraBidirection for an array based but more complicated version
 * @author Peter Karich
 */
public class DijkstraBidirectionRef extends AbstractBidirAlgo
{
    private PriorityQueue<EdgeEntry> openSetFrom;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> bestWeightMapFrom;
    private TIntObjectMap<EdgeEntry> bestWeightMapTo;
    protected TIntObjectMap<EdgeEntry> bestWeightMapOther;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;
    protected PathBidirRef bestPath;

    public DijkstraBidirectionRef( Graph graph, FlagEncoder encoder, Weighting weighting )
    {
        super(graph, encoder, weighting);
        initCollections(1000);
    }

    protected void initCollections( int nodes )
    {
        openSetFrom = new PriorityQueue<EdgeEntry>(nodes / 10);
        bestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(nodes / 10);

        openSetTo = new PriorityQueue<EdgeEntry>(nodes / 10);
        bestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(nodes / 10);
    }

    @Override
    public void initFrom( int from, double dist )
    {
        currFrom = createEdgeEntry(from, dist);
        bestWeightMapFrom.put(from, currFrom);
        openSetFrom.add(currFrom);
        if (currTo != null)
        {
            bestWeightMapOther = bestWeightMapTo;
            updateShortest(currTo, from);
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = createEdgeEntry(to, dist);
        bestWeightMapTo.put(to, currTo);
        openSetTo.add(currTo);
        if (currFrom != null)
        {
            bestWeightMapOther = bestWeightMapFrom;
            updateShortest(currFrom, to);
        }
    }

    @Override
    protected void initPath()
    {
        bestPath = new PathBidirRef(graph, flagEncoder);
    }

    @Override
    protected Path extractPath()
    {
        return bestPath.extract();
    }

    @Override
    void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (bestWeightMapFrom.isEmpty() || bestWeightMapTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + bestWeightMapFrom + ", to:" + bestWeightMapTo);
    }

    @Override
    protected boolean fillEdgesFrom()
    {
        if (openSetFrom.isEmpty())
            return false;

        currFrom = openSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, openSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedFromCount++;
        return true;
    }

    @Override
    protected boolean fillEdgesTo()
    {
        if (openSetTo.isEmpty())
            return false;
        currTo = openSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, openSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedToCount++;
        return true;
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the best path!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ            
    @Override
    protected boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    void fillEdges( EdgeEntry currEdge, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, EdgeExplorer explorer, boolean reverse )
    {
        int currNode = currEdge.adjNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next())
        {
            if (!accept(iter))
                continue;
            // minor speed up
            if (currEdge.edge == iter.getEdge())
                continue;

            int neighborNode = iter.getAdjNode();
            double tmpWeight = weighting.calcWeight(iter, reverse) + currEdge.weight;

            EdgeEntry de = shortestWeightMap.get(neighborNode);
            if (de == null)
            {
                de = new EdgeEntry(iter.getEdge(), neighborNode, tmpWeight);
                de.parent = currEdge;
                shortestWeightMap.put(neighborNode, de);
                prioQueue.add(de);
            } else if (de.weight > tmpWeight)
            {
                prioQueue.remove(de);
                de.edge = iter.getEdge();
                de.weight = tmpWeight;
                de.parent = currEdge;
                prioQueue.add(de);
            }

            updateShortest(de, neighborNode);
        }
    }

    @Override
    protected void updateShortest( EdgeEntry shortestEE, int currLoc )
    {
        EdgeEntry entryOther = bestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestEE.weight + entryOther.weight;
        if (newShortest < bestPath.getWeight())
        {
            bestPath.setSwitchToFrom(bestWeightMapFrom == bestWeightMapOther);
            bestPath.setEdgeEntry(shortestEE);
            bestPath.setWeight(newShortest);
            bestPath.edgeTo = entryOther;
        }
    }

    public EdgeEntry shortestWeightFrom( int nodeId )
    {
        return bestWeightMapFrom.get(nodeId);
    }

    public EdgeEntry shortestWeightTo( int nodeId )
    {
        return bestWeightMapTo.get(nodeId);
    }

    @Override
    public String getName()
    {
        return "dijkstrabi";
    }
}
