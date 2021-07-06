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

import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.StopWatch;

import java.util.Collections;
import java.util.List;

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
    public List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions) {
        RoutingAlgorithm algo = createAlgo();
        return calcPaths(from, to, edgeRestrictions, algo);
    }

    private RoutingAlgorithm createAlgo() {
        StopWatch sw = new StopWatch().start();
        RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, weighting, algoOpts);
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs";
        return algo;
    }

    private List<Path> calcPaths(int from, int to, EdgeRestrictions edgeRestrictions, RoutingAlgorithm algo) {
        StopWatch sw = new StopWatch().start();
        List<Path> paths;
        if (edgeRestrictions.getGetEdgePenaltyFrom() != null || edgeRestrictions.getGetGetEdgePenaltyTo() != null) {
            if (!(algo instanceof BidirRoutingAlgorithm))
                throw new IllegalArgumentException("For directed routing you need a bidirectional algorithm, got: " + algo.getName());
            paths = Collections.singletonList(((BidirRoutingAlgorithm) algo).calcPath(from, to, edgeRestrictions.getGetEdgePenaltyFrom(), edgeRestrictions.getGetGetEdgePenaltyTo()));
        } else {
            paths = algo.calcPaths(from, to);
        }

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