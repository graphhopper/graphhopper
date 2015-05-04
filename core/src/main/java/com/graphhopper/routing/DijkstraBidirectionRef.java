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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

/**
 * Calculates best path in bidirectional way.
 * <p/>
 * 'Ref' stands for reference implementation and is using the normal Java-'reference'-way.
 * <p>
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
    private boolean updateBestPath = true;

    public DijkstraBidirectionRef( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);
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
        openSetFrom.add(currFrom);
        if (!traversalMode.isEdgeBased())
        {
            bestWeightMapFrom.put(from, currFrom);
            if (currTo != null)
            {
                bestWeightMapOther = bestWeightMapTo;
                updateBestPath(GHUtility.getEdge(graph, from, currTo.adjNode), currTo, from);
            }
        } else
        {
            if (currTo != null && currTo.adjNode == from)
            {
                // special case of identical start and end
                bestPath.edgeEntry = currFrom;
                bestPath.edgeTo = currTo;
                finishedFrom = true;
                finishedTo = true;
            }
        }
    }

    @Override
    public void initTo( int to, double dist )
    {
        currTo = createEdgeEntry(to, dist);
        openSetTo.add(currTo);
        if (!traversalMode.isEdgeBased())
        {
            bestWeightMapTo.put(to, currTo);
            if (currFrom != null)
            {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to);
            }
        } else
        {
            if (currFrom != null && currFrom.adjNode == to)
            {
                // special case of identical start and end
                bestPath.edgeEntry = currFrom;
                bestPath.edgeTo = currTo;
                finishedFrom = true;
                finishedTo = true;
            }
        }
    }

    @Override
    protected Path createAndInitPath()
    {
        bestPath = new PathBidirRef(graph, flagEncoder);
        return bestPath;
    }

    @Override
    protected Path extractPath()
    {
        if (finished())
            return bestPath.extract();

        return bestPath;
    }

    @Override
    void checkState( int fromBase, int fromAdj, int toBase, int toAdj )
    {
        if (bestWeightMapFrom.isEmpty() || bestWeightMapTo.isEmpty())
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + bestWeightMapFrom + ", to:" + bestWeightMapTo);
    }

    @Override
    public boolean fillEdgesFrom()
    {
        if (openSetFrom.isEmpty())
            return false;

        currFrom = openSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, openSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedCountFrom++;
        return true;
    }

    @Override
    public boolean fillEdgesTo()
    {
        if (openSetTo.isEmpty())
            return false;
        currTo = openSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, openSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the best path!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ            
    @Override
    public boolean finished()
    {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return currFrom.weight + currTo.weight > weightLimit;
    }

    void fillEdges( EdgeEntry currEdge, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, EdgeExplorer explorer, boolean reverse )
    {
        int currNode = currEdge.adjNode;
        EdgeIterator iter = explorer.setBaseNode(currNode);
        while (iter.next())
        {
            if (!accept(iter, currEdge.edge))
                continue;

            int traversalId = traversalMode.createTraversalId(iter, reverse);
            double tmpWeight = weighting.calcWeight(iter, reverse, currEdge.edge) + currEdge.weight;
            if (Double.isInfinite(tmpWeight))
                continue;

            EdgeEntry ee = shortestWeightMap.get(traversalId);
            if (ee == null)
            {
                ee = new EdgeEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                ee.parent = currEdge;
                shortestWeightMap.put(traversalId, ee);
                prioQueue.add(ee);
            } else if (ee.weight > tmpWeight)
            {
                prioQueue.remove(ee);
                ee.edge = iter.getEdge();
                ee.weight = tmpWeight;
                ee.parent = currEdge;
                prioQueue.add(ee);
            } else
                continue;

            if (updateBestPath)
                updateBestPath(iter, ee, traversalId);
        }
    }

    @Override
    protected void updateBestPath( EdgeIteratorState edgeState, EdgeEntry entryCurrent, int traversalId )
    {
        EdgeEntry entryOther = bestWeightMapOther.get(traversalId);
        if (entryOther == null)
            return;

        boolean reverse = bestWeightMapFrom == bestWeightMapOther;

        // update μ
        double newWeight = entryCurrent.weight + entryOther.weight;
        if (traversalMode.isEdgeBased())
        {
            if (entryOther.edge != entryCurrent.edge)
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            if (entryOther.adjNode != entryCurrent.adjNode)
            {
                // prevents the path to contain the edge at the meeting point twice and subtract the weight (excluding turn weight => no previous edge)
                entryCurrent = entryCurrent.parent;
                newWeight -= weighting.calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
            } else
            {
                // we detected a u-turn at meeting point, skip if not supported
                if (!traversalMode.hasUTurnSupport())
                    return;
            }
        }

        if (newWeight < bestPath.getWeight())
        {
            bestPath.setSwitchToFrom(reverse);
            bestPath.setEdgeEntry(entryCurrent);
            bestPath.setWeight(newWeight);
            bestPath.setEdgeEntryTo(entryOther);
        }
    }

    TIntObjectMap<EdgeEntry> getBestFromMap()
    {
        return bestWeightMapFrom;
    }

    TIntObjectMap<EdgeEntry> getBestToMap()
    {
        return bestWeightMapTo;
    }

    void setBestOtherMap( TIntObjectMap<EdgeEntry> other )
    {
        bestWeightMapOther = other;
    }

    void setFromDataStructures( DijkstraBidirectionRef dijkstra )
    {
        openSetFrom = dijkstra.openSetFrom;
        bestWeightMapFrom = dijkstra.bestWeightMapFrom;
        finishedFrom = dijkstra.finishedFrom;
        currFrom = dijkstra.currFrom;
        visitedCountFrom = dijkstra.visitedCountFrom;
        // outEdgeExplorer
    }

    void setToDataStructures( DijkstraBidirectionRef dijkstra )
    {
        openSetTo = dijkstra.openSetTo;
        bestWeightMapTo = dijkstra.bestWeightMapTo;
        finishedTo = dijkstra.finishedTo;
        currTo = dijkstra.currTo;
        visitedCountTo = dijkstra.visitedCountTo;
        // inEdgeExplorer
    }

    void setUpdateBestPath( boolean b )
    {
        updateBestPath = b;
    }

    void setBestPath( PathBidirRef bestPath )
    {
        this.bestPath = bestPath;
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.DIJKSTRA_BI;
    }
}
