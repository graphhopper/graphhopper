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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Calculates extractPath path in bidirectional way.
 *
 * 'Ref' stands for reference implementation and is using the normal
 * Java-'reference'-way.
 *
 * @see DijkstraBidirection for an optimized but more complicated version
 * @author Peter Karich
 */
public class DijkstraBidirectionRef extends AbstractRoutingAlgorithm {

    private int from, to;
    private int visitedFromCount;
    private PriorityQueue<EdgeEntry> openSetFrom;
    private TIntObjectMap<EdgeEntry> shortestWeightMapFrom;
    private int visitedToCount;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> shortestWeightMapTo;
    private boolean alreadyRun;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;
    protected TIntObjectMap<EdgeEntry> shortestWeightMapOther;
    public PathBidirRef shortest;

    public DijkstraBidirectionRef(Graph graph, EdgePropertyEncoder encoder) {
        super(graph, encoder);
        initCollections(Math.max(20, graph.nodes()));
    }

    protected void initCollections(int nodes) {
        openSetFrom = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(nodes / 10);

        openSetTo = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(nodes / 10);
    }

    public DijkstraBidirectionRef initFrom(int from) {
        this.from = from;
        currFrom = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0);
        shortestWeightMapFrom.put(from, currFrom);
        return this;
    }

    public DijkstraBidirectionRef initTo(int to) {
        this.to = to;
        currTo = new EdgeEntry(EdgeIterator.NO_EDGE, to, 0);
        shortestWeightMapTo.put(to, currTo);
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");
        alreadyRun = true;
        initPath();
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int finish = 0;
        while (finish < 2) {
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }

        return extractPath();
    }

    public Path extractPath() {
        return shortest.extract();
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the extractPath path!!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder 
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ            
    public boolean checkFinishCondition() {
        if (currFrom == null)
            return currTo.weight >= shortest.weight();
        else if (currTo == null)
            return currFrom.weight >= shortest.weight();
        return currFrom.weight + currTo.weight >= shortest.weight();
    }

    void fillEdges(EdgeEntry curr, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, EdgeFilter filter) {

        int currNode = curr.endNode;
        EdgeIterator iter = graph.getEdges(currNode, filter);
        while (iter.next()) {
            if (!accept(iter))
                continue;
            int neighborNode = iter.adjNode();
            double tmpWeight = weightCalc.getWeight(iter.distance(), iter.flags()) + curr.weight;
            EdgeEntry de = shortestWeightMap.get(neighborNode);
            if (de == null) {
                de = new EdgeEntry(iter.edge(), neighborNode, tmpWeight);
                de.parent = curr;
                shortestWeightMap.put(neighborNode, de);
                prioQueue.add(de);
            } else if (de.weight > tmpWeight) {
                prioQueue.remove(de);
                de.edge = iter.edge();
                de.weight = tmpWeight;
                de.parent = curr;
                prioQueue.add(de);
            }

            updateShortest(de, neighborNode);
        }
    }

    @Override
    protected void updateShortest(EdgeEntry shortestEE, int currLoc) {
        EdgeEntry entryOther = shortestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestEE.weight + entryOther.weight;
        if (newShortest < shortest.weight()) {
            shortest.switchToFrom(shortestWeightMapFrom == shortestWeightMapOther);
            shortest.edgeEntry(shortestEE);
            shortest.edgeTo = entryOther;
            shortest.weight(newShortest);
        }
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, openSetFrom, shortestWeightMapFrom, outEdgeFilter);
            visitedFromCount++;
            if (openSetFrom.isEmpty()) {
                currFrom = null;
                return false;
            }

            currFrom = openSetFrom.poll();
            if (checkFinishCondition())
                return false;
        } else if (currTo == null)
            return false;
        return true;
    }

    public boolean fillEdgesTo() {
        if (currTo != null) {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, openSetTo, shortestWeightMapTo, inEdgeFilter);
            visitedToCount++;
            if (openSetTo.isEmpty()) {
                currTo = null;
                return false;
            }

            currTo = openSetTo.poll();
            if (checkFinishCondition())
                return false;
        } else if (currFrom == null)
            return false;
        return true;
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to)
            return new Path(graph, flagEncoder);
        return null;
    }

    public EdgeEntry shortestWeightFrom(int nodeId) {
        return shortestWeightMapFrom.get(nodeId);
    }

    public EdgeEntry shortestWeightTo(int nodeId) {
        return shortestWeightMapTo.get(nodeId);
    }

    protected PathBidirRef createPath() {
        return new PathBidirRef(graph, flagEncoder);
    }

    public DijkstraBidirectionRef initPath() {
        shortest = createPath();
        return this;
    }

    /**
     * @return number of visited nodes.
     */
    @Override
    public int visitedNodes() {
        return visitedFromCount + visitedToCount;
    }

    @Override public String name() {
        return "dijkstrabi";
    }
}
