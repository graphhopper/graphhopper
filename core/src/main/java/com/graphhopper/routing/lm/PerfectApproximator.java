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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

/**
 * Just a sanity-check implementation for WeightApproximator, which 'approximates' perfectly.
 */
public class PerfectApproximator implements WeightApproximator {

    private Graph graph;
    private Weighting weighting;
    private TraversalMode traversalMode;
    private int to;
    private boolean reverse;

    public PerfectApproximator(Graph graph, Weighting weighting, TraversalMode traversalMode, boolean reverse) {
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.reverse = reverse;
    }

    @Override
    public double approximate(int currentNode) {
        Dijkstra dijkstra = new Dijkstra(graph, weighting, traversalMode);
        Path path = reverse ? dijkstra.calcPath(to, currentNode) : dijkstra.calcPath(currentNode, to);
        return path.isFound() ? path.getWeight() : Double.POSITIVE_INFINITY;
    }

    @Override
    public void setTo(int to) {
        this.to = to;
    }

    @Override
    public WeightApproximator reverse() {
        return new PerfectApproximator(graph, weighting, traversalMode, !reverse);
    }

    @Override
    public double getSlack() {
        return 0;
    }
}
