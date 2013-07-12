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

import com.graphhopper.routing.AStar.AStarEdge;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.shapes.CoordTrig;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the finish condition can not be so strict which leads to either
 * suboptimal paths or suboptimal node exploration (too many nodes). Still very good approximations
 * with a rougly twice times faster running time than the normal A* can be reached.
 * <p/>
 * Computing the Shortest Path: A∗ Search Meets Graph Theory ->
 * http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 * <p/>
 * better stop condition
 * <p/>
 * 1. Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 * <p/>
 * 2. Whangbo, T. K. (2007). Efficient modified bidirectional a* algorithm for optimal route-
 * finding. In IEA/AIE, volume 4570, pages 344–353. Springer.
 * <p/>
 * or could we even use this three phase approach?
 * www.lix.polytechnique.fr/~giacomon/papers/bidirtimedep.pdf
 * <p/>
 * @author Peter Karich
 */
public class AStarBidirection extends AbstractRoutingAlgorithm
{
    private DistanceCalc dist;
    private int from, to;
    private int visitedFromCount;
    private PriorityQueue<AStarEdge> prioQueueOpenSetFrom;
    private TIntObjectMap<AStarEdge> shortestWeightMapFrom;
    private int visitedToCount;
    private PriorityQueue<AStarEdge> prioQueueOpenSetTo;
    private TIntObjectMap<AStarEdge> shortestWeightMapTo;
    private boolean alreadyRun;
    protected AStarEdge currFrom;
    protected AStarEdge currTo;
    private TIntObjectMap<AStarEdge> shortestWeightMapOther;
    public PathBidirRef shortest;
    private CoordTrig fromCoord;
    private CoordTrig toCoord;
    protected double approximationFactor;

    public AStarBidirection( Graph graph, FlagEncoder encoder )
    {
        super(graph, encoder);
        int nodes = Math.max(20, graph.getNodes());
        initCollections(nodes);
        setApproximation(false);
    }

    protected void initCollections( int size )
    {
        prioQueueOpenSetFrom = new PriorityQueue<AStarEdge>(size / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<AStarEdge>(size / 10);

        prioQueueOpenSetTo = new PriorityQueue<AStarEdge>(size / 10);
        shortestWeightMapTo = new TIntObjectHashMap<AStarEdge>(size / 10);
    }

    /**
     * @param fast if true it enables approximative distance calculation from lat,lon values
     */
    public AStarBidirection setApproximation( boolean approx )
    {
        if (approx)
        {
            dist = new DistancePlaneProjection();
            approximationFactor = 0.5;
        } else
        {
            dist = new DistanceCalc();
            approximationFactor = 1.2;
        }
        return this;
    }

    /**
     * Specify a low value like 0.5 for worse but faster results. Or over 1.1 for more precise.
     */
    public AStarBidirection setApproximationFactor( double approxFactor )
    {
        this.approximationFactor = approxFactor;
        return this;
    }

    public void initFrom( int from )
    {
        this.from = from;
        currFrom = new AStarEdge(-1, from, 0, 0);
        shortestWeightMapFrom.put(from, currFrom);
        fromCoord = new CoordTrig(graph.getLatitude(from), graph.getLongitude(from));
    }

    public void initTo( int to )
    {
        this.to = to;
        currTo = new AStarEdge(-1, to, 0, 0);
        shortestWeightMapTo.put(to, currTo);
        toCoord = new CoordTrig(graph.getLatitude(to), graph.getLongitude(to));
    }

    private Path checkIndenticalFromAndTo()
    {
        if (from == to)
        {
            return new Path(graph, flagEncoder);
        }
        return null;
    }

    protected PathBidirRef createPath()
    {
        return new PathBidirRef(graph, flagEncoder);
    }

    public void initPath()
    {
        shortest = createPath();
        // pi_r_of_t = dist.calcDist(fromCoord.lat, fromCoord.lon, toCoord.lat, toCoord.lon);
    }

    @Override
    public Path calcPath( int from, int to )
    {
        if (alreadyRun)
        {
            throw new IllegalStateException("Create a new instance per call");
        }
        alreadyRun = true;
        initFrom(from);
        initTo(to);
        initPath();

        Path p = checkIndenticalFromAndTo();
        if (p != null)
        {
            return p;
        }

        int finish = 0;
        while (finish < 2)
        {
            finish = 0;
            if (!fillEdgesFrom())
            {
                finish++;
            }

            if (!fillEdgesTo())
            {
                finish++;
            }
        }

        return shortest.extract();
    }

    // Problem is the correct finish condition! if the bounds are too wide too many nodes are visited :/   
    // d_f (v) + (v, w) + d_r (w) < μ + p_r(t)
    // where pi_r_of_t = p_r(t) = 1/2(pi_r(t) - pi_f(t) + pi_f(s)), and pi_f(t)=0
    public boolean checkFinishCondition()
    {
        double tmp = shortest.getWeight() * approximationFactor;
        if (currFrom == null)
        {
            return currTo.weightToCompare >= tmp;
        } else if (currTo == null)
        {
            return currFrom.weightToCompare >= tmp;
        }
        return currFrom.weightToCompare + currTo.weightToCompare >= tmp;
    }

    public boolean fillEdgesFrom()
    {
        if (currFrom != null)
        {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, toCoord, prioQueueOpenSetFrom, shortestWeightMapFrom, outEdgeFilter);
            visitedFromCount++;
            if (prioQueueOpenSetFrom.isEmpty())
            {
                currFrom = null;
                return false;
            }

            currFrom = prioQueueOpenSetFrom.poll();
            if (checkFinishCondition())
            {
                return false;
            }
        } else if (currTo == null)
        {
            return false;
        }

        return true;
    }

    public boolean fillEdgesTo()
    {
        if (currTo != null)
        {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, fromCoord, prioQueueOpenSetTo, shortestWeightMapTo, inEdgeFilter);
            visitedToCount++;
            if (prioQueueOpenSetTo.isEmpty())
            {
                currTo = null;
                return false;
            }

            currTo = prioQueueOpenSetTo.poll();
            if (checkFinishCondition())
            {
                return false;
            }
        } else if (currFrom == null)
        {
            return false;
        }

        return true;
    }

    private void fillEdges( AStarEdge curr, CoordTrig goal,
            PriorityQueue<AStarEdge> prioQueueOpenSet,
            TIntObjectMap<AStarEdge> shortestWeightMap, EdgeFilter filter )
    {

        int currNode = curr.endNode;
        EdgeIterator iter = graph.getEdges(currNode, filter);
        while (iter.next())
        {
            if (!accept(iter))
            {
                continue;
            }
            int neighborNode = iter.getAdjNode();
            // TODO performance: check if the node is already existent in the opposite direction
            // then we could avoid the approximation as we already know the exact complete path!
            double alreadyVisitedWeight = weightCalc.getWeight(iter) + curr.weightToCompare;
            AStarEdge de = shortestWeightMap.get(neighborNode);
            if (de == null || de.weightToCompare > alreadyVisitedWeight)
            {
                double tmpLat = graph.getLatitude(neighborNode);
                double tmpLon = graph.getLongitude(neighborNode);
                double currWeightToGoal = dist.calcDist(goal.lat, goal.lon, tmpLat, tmpLon);
                currWeightToGoal = weightCalc.getMinWeight(currWeightToGoal);
                double estimationFullDist = alreadyVisitedWeight + currWeightToGoal;
                if (de == null)
                {
                    de = new AStarEdge(iter.getEdge(), neighborNode, estimationFullDist, alreadyVisitedWeight);
                    shortestWeightMap.put(neighborNode, de);
                } else
                {
                    prioQueueOpenSet.remove(de);
                    de.edge = iter.getEdge();
                    de.weight = estimationFullDist;
                    de.weightToCompare = alreadyVisitedWeight;
                }

                de.parent = curr;
                prioQueueOpenSet.add(de);
                updateShortest(de, neighborNode);
            }
        }
    }

//    @Override -> TODO use only weight => then a simple EdgeEntry is possible
    public void updateShortest( AStarEdge shortestDE, int currLoc )
    {
        AStarEdge entryOther = shortestWeightMapOther.get(currLoc);
        if (entryOther == null)
        {
            return;
        }

        // update μ
        double newShortest = shortestDE.weightToCompare + entryOther.weightToCompare;
        if (newShortest < shortest.getWeight())
        {
            shortest.setSwitchToFrom(shortestWeightMapFrom == shortestWeightMapOther);
            shortest.edgeEntry = shortestDE;
            shortest.edgeTo = entryOther;
            shortest.setWeight(newShortest);
        }
    }

    @Override
    public String getName()
    {
        return "astarbi";
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedFromCount + visitedToCount;
    }
}
