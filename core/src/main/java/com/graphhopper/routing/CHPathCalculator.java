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

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

public class CHPathCalculator implements PathCalculator {
    private final QueryGraph queryGraph;
    private final CHRoutingAlgorithmFactory algoFactory;
    private final PMap algoOpts;
    private String debug;
    private int visitedNodes;

    public CHPathCalculator(QueryGraph queryGraph, CHRoutingAlgorithmFactory algoFactory, PMap algoOpts) {
        this.queryGraph = queryGraph;
        this.algoFactory = algoFactory;
        this.algoOpts = algoOpts;
    }

    @Override
    public List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions) {
        if (!edgeRestrictions.getUnfavoredEdges().isEmpty())
            throw new IllegalArgumentException("Using unfavored edges is currently not supported for CH");
        RoutingAlgorithm algo = createAlgo();
        return calcPaths(from, to, edgeRestrictions, algo);
    }

    private RoutingAlgorithm createAlgo() {
        StopWatch sw = new StopWatch().start();
        RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs";
        return algo;
    }

    private List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions, RoutingAlgorithm algo) {
        StopWatch sw = new StopWatch().start();
        List<Path> paths;
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE) {
            if (!(algo instanceof BidirRoutingAlgorithm))
                throw new IllegalArgumentException("To make use of the " + Parameters.Routing.CURBSIDE + " parameter you need a bidirectional algorithm, got: " + algo.getName());
            paths = Collections.singletonList(((BidirRoutingAlgorithm) algo).calcPath(from, to, queryGraph.shiftEdgeId(edgeRestrictions.getSourceOutEdge()), queryGraph.shiftEdgeId(edgeRestrictions.getTargetInEdge())));
        } else {
            paths = algo.calcPaths(from, to);
        }
        if (paths.isEmpty())
            throw new IllegalStateException("Path list was empty for " + from + " -> " + to);
        int maxVisitedNodes = algoOpts.getInt(MAX_VISITED_NODES, Integer.MAX_VALUE);
        if (algo.getVisitedNodes() >= maxVisitedNodes)
            throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + maxVisitedNodes);
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

}