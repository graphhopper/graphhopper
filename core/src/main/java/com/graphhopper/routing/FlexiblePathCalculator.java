/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

public class FlexiblePathCalculator implements PathCalculator {
    private final QueryGraph queryGraph;
    private final RoutingAlgorithmFactory algoFactory;
    private Weighting weighting;
    private final AlgorithmOptions algoOpts;
    private String debug;
    private int visitedNodes;

    public FlexiblePathCalculator(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, Weighting weighting, AlgorithmOptions algoOpts) {
        this.queryGraph = queryGraph;
        this.algoFactory = algoFactory;
        this.weighting = weighting;
        this.algoOpts = algoOpts;
    }

    @Override
// ORS-GH MOD START - add argument for TD routing
    public List<Path> calcPaths(int from, int to, long at, EdgeRestrictions edgeRestrictions) {
        RoutingAlgorithm algo = createAlgo();
        return calcPaths(from, to, at, edgeRestrictions, algo);
    }

    public List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions) {
        return calcPaths(from, to, -1, edgeRestrictions);
// ORS-GH MOD END
    }

    private RoutingAlgorithm createAlgo() {
        StopWatch sw = new StopWatch().start();
        RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, weighting, algoOpts);
        // ORS-GH MOD START: pass edgeFilter to algorithm
        algo.setEdgeFilter(algoOpts.getEdgeFilter());
        // ORS-GH MOD END
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs";
        return algo;
    }

// ORS-GH MOD START - add argument for TD routing
    private List<Path> calcPaths(int from, int to, long at, EdgeRestrictions edgeRestrictions, RoutingAlgorithm algo) {
// ORS-GH MOD END
        StopWatch sw = new StopWatch().start();
        // todo: so far 'heading' is implemented like this: we mark the unfavored edges on the query graph and then
        // our weighting applies a penalty to these edges. however, this only works for virtual edges and to make
        // this compatible with edge-based routing we would have to use edge keys instead of edge ids. either way a
        // better approach seems to be making the weighting (or the algorithm for that matter) aware of the unfavored
        // edges directly without changing the graph
        for (IntCursor c : edgeRestrictions.getUnfavoredEdges())
            queryGraph.unfavorVirtualEdge(c.value);

        List<Path> paths;
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE) {
            if (!(algo instanceof BidirRoutingAlgorithm))
                throw new IllegalArgumentException("To make use of the " + Parameters.Routing.CURBSIDE + " parameter you need a bidirectional algorithm, got: " + algo.getName());
            paths = Collections.singletonList(((BidirRoutingAlgorithm) algo).calcPath(from, to, edgeRestrictions.getSourceOutEdge(), edgeRestrictions.getTargetInEdge()));
        } else {
// ORS-GH MOD START - add argument for TD routing
            paths = (at == -1) ? algo.calcPaths(from, to) : algo.calcPaths(from, to, at);
// ORS-GH MOD END
        }

        // reset all direction enforcements in queryGraph to avoid influencing next path
        // todo: is this correct? aren't we taking a second look at these edges later when we calc times or
        // instructions etc.?
        queryGraph.clearUnfavoredStatus();

        if (paths.isEmpty())
            throw new IllegalStateException("Path list was empty for " + from + " -> " + to);
        if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
            throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());
        visitedNodes = algo.getVisitedNodes();
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getMillis() + " ms";
        return paths;
    }

    @Override
    public String getDebugString() {
        return debug;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public void setWeighting(Weighting weighting) {
        this.weighting = weighting;
    }
}