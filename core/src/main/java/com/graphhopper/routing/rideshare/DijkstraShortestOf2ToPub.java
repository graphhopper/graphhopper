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
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Public transport represents a collection of Locations. Then there are two points P1 and P1 and it
 * is the aim to find the shortest path from P1 to one of the public transport points (M) and
 * further to P2. So the point M needs to be determined.
 * <p/>
 * <br/> Usage: A driver can carry the passenger from P1 to a public transport point (M) and going
 * back to his own destination P2 and comparing this with the detour of taking the passenger
 * directly to his destination (and then going back to P2).
 * <p/>
 * @author Peter Karich
 */
public class DijkstraShortestOf2ToPub extends AbstractRoutingAlgorithm
{
    private TIntList pubTransport = new TIntArrayList();
    private int fromP1;
    private int toP2;
    private EdgeEntry currTo;
    private EdgeEntry currFrom;
    private PathBidirRef shortest;
    private TIntObjectMap<EdgeEntry> shortestDistMapOther;
    private TIntObjectMap<EdgeEntry> shortestDistMapFrom;
    private TIntObjectMap<EdgeEntry> shortestDistMapTo;
    private int visitedFromCount;
    private int visitedToCount;

    public DijkstraShortestOf2ToPub( Graph graph, FlagEncoder encoder )
    {
        super(graph, encoder);
    }

    public void addPubTransportPoints( int... indices )
    {
        if (indices.length == 0)
        {
            throw new IllegalStateException("You need to add something");
        }

        for (int i = 0; i < indices.length; i++)
        {
            addPubTransportPoint(indices[i]);
        }
    }

    public void addPubTransportPoint( int index )
    {
        if (!pubTransport.contains(index))
        {
            pubTransport.add(index);
        }
    }

    public DijkstraShortestOf2ToPub from( int from )
    {
        fromP1 = from;
        return this;
    }

    public DijkstraShortestOf2ToPub to( int to )
    {
        toP2 = to;
        return this;
    }

    public Path calcPath()
    {
        // identical
        if (pubTransport.contains(fromP1) || pubTransport.contains(toP2))
        {
            return new DijkstraBidirection(graph, flagEncoder).calcPath(fromP1, toP2);
        }

        PriorityQueue<EdgeEntry> prioQueueFrom = new PriorityQueue<EdgeEntry>();
        shortestDistMapFrom = new TIntObjectHashMap<EdgeEntry>();

        EdgeEntry entryTo = new EdgeEntry(EdgeIterator.NO_EDGE, toP2, 0);
        currTo = entryTo;
        PriorityQueue<EdgeEntry> prioQueueTo = new PriorityQueue<EdgeEntry>();
        shortestDistMapTo = new TIntObjectHashMap<EdgeEntry>();

        shortest = new PathBidirRef(graph, flagEncoder);

        // create several starting points
        if (pubTransport.isEmpty())
        {
            throw new IllegalStateException("You'll need at least one starting point. Set it via addPubTransportPoint");
        }

        currFrom = new EdgeEntry(EdgeIterator.NO_EDGE, fromP1, 0);
        // in the birectional case we maintain the shortest path via:
        // currFrom.distance + currTo.distance >= shortest.distance
        // Now we simply need to check before updating if the newly discovered point is from pub tranport
        while (true)
        {
            if (currFrom != null)
            {
                shortestDistMapOther = shortestDistMapTo;
                fillEdges(currFrom, prioQueueFrom, shortestDistMapFrom);
                currFrom = prioQueueFrom.poll();
                if (currFrom != null)
                {
                    if (checkFinishCondition())
                    {
                        break;
                    }
                }
            } else if (currTo == null)
            {
                throw new IllegalStateException("Shortest Path not found? " + fromP1 + " " + toP2);
            }

            if (currTo != null)
            {
                shortestDistMapOther = shortestDistMapFrom;
                fillEdges(currTo, prioQueueTo, shortestDistMapTo);
                currTo = prioQueueTo.poll();
                if (currTo != null)
                {
                    if (checkFinishCondition())
                    {
                        break;
                    }
                }
            } else if (currFrom == null)
            {
                throw new IllegalStateException("Shortest Path not found? " + fromP1 + " " + toP2);
            }
        }

        Path p = shortest.extract();
        // TODO if path directly from P1 to P2 is shorter
        return p;
    }

    // The normal checkFinishCondition won't work anymore as the dijkstra heaps are independent and can take a completely 
    // different returning point into account (not necessarily the shortest).
    // example: P1 to M is long, also P2 to M - in sum they can be longer than the shortest.
    // But even now it could be that there is an undiscovered M' from P1 which results in a very short 
    // (and already discovered) back path M'-P2. See test testCalculateShortestPathWithSpecialFinishCondition
    boolean checkFinishCondition()
    {
        if (currFrom == null)
        {
            if (currTo == null)
            {
                throw new IllegalStateException("no shortest path!?");
            }

            return currTo.weight >= shortest.weight();
        } else if (currTo == null)
        {
            return currFrom.weight >= shortest.weight();
        } else
        {
            return Math.min(currFrom.weight, currTo.weight) >= shortest.weight();
        }
    }

    void fillEdges( EdgeEntry curr, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestDistMap )
    {

        int currVertexFrom = curr.endNode;
        EdgeIterator iter = graph.getEdges(currVertexFrom, outEdgeFilter);
        while (iter.next())
        {
            int tmpV = iter.adjNode();
            double tmp = iter.distance() + curr.weight;
            EdgeEntry de = shortestDistMap.get(tmpV);
            if (de == null)
            {
                de = new EdgeEntry(iter.edge(), tmpV, tmp);
                de.parent = curr;
                shortestDistMap.put(tmpV, de);
                prioQueue.add(de);
            } else if (de.weight > tmp)
            {
                prioQueue.remove(de);
                de.edge = iter.edge();
                de.weight = tmp;
                de.parent = curr;
                prioQueue.add(de);
            }

            updateShortest(de, tmpV);
        }
    }

    @Override
    protected void updateShortest( EdgeEntry shortestDE, int currLoc )
    {
        if (!pubTransport.contains(currLoc))
        {
            return;
        }

        EdgeEntry entryOther = shortestDistMapOther.get(currLoc);
        if (entryOther != null)
        {
            // update μ
            double newShortest = shortestDE.weight + entryOther.weight;
            if (newShortest < shortest.weight())
            {
                shortest.switchToFrom(shortestDistMapFrom == shortestDistMapOther);
                shortest.edgeEntry(shortestDE);
                shortest.edgeEntryTo(entryOther);
                shortest.weight(newShortest);
            }
        }
    }

    @Override
    public Path calcPath( int from, int to )
    {
        addPubTransportPoint(from);
        from(from);
        to(to);
        return calcPath();
    }

    @Override
    public int visitedNodes()
    {
        return visitedFromCount + visitedToCount;
    }
}
