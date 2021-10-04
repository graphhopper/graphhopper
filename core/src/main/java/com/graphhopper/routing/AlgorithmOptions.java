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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

/**
 * @author Peter Karich
 */
public class AlgorithmOptions {
    private PMap hints = new PMap();
    private String algorithm = Parameters.Algorithms.DIJKSTRA_BI;
    private TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private int maxVisitedNodes = Integer.MAX_VALUE;

    public AlgorithmOptions() {
    }

    public AlgorithmOptions(AlgorithmOptions b) {
        setAlgorithm(b.getAlgorithm());
        setTraversalMode(b.getTraversalMode());
        setMaxVisitedNodes(b.getMaxVisitedNodes());
        setHints(b.getHints());
    }

    public AlgorithmOptions setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public AlgorithmOptions setTraversalMode(TraversalMode traversalMode) {
        this.traversalMode = traversalMode;
        return this;
    }

    public AlgorithmOptions setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
        return this;
    }

    public AlgorithmOptions setHints(PMap pMap) {
        this.hints = new PMap(pMap);
        return this;
    }

    public TraversalMode getTraversalMode() {
        return traversalMode;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    public PMap getHints() {
        return hints;
    }

    @Override
    public String toString() {
        return algorithm + ", " + traversalMode;
    }

    // TODO: Builder has been removed, need to see how to integrate changes
//    /**
//     * This method clones the specified AlgorithmOption object with the possibility for further
//     * changes.
//     */
//    public static Builder start(AlgorithmOptions opts) {
//        Builder b = new Builder();
//        if (opts.algorithm != null)
//            b.algorithm(opts.getAlgorithm());
//        if (opts.traversalMode != null)
//            b.traversalMode(opts.getTraversalMode());
//        if (opts.weighting != null)
//            b.weighting(opts.getWeighting());
//        if (opts.maxVisitedNodes >= 0)
//            b.maxVisitedNodes(opts.maxVisitedNodes);
//        if (!opts.hints.isEmpty())
//            b.hints(opts.hints);
//        if (opts.edgeFilter != null)
//            b.edgeFilter(opts.edgeFilter);
//        return b;
//    }
//
//    public static class Builder {
//        private AlgorithmOptions opts = new AlgorithmOptions();
//        private boolean buildCalled;
//
//        public Builder traversalMode(TraversalMode traversalMode) {
//            if (traversalMode == null)
//                throw new IllegalArgumentException("null as traversal mode is not allowed");
//
//            this.opts.traversalMode = traversalMode;
//            return this;
//        }
//
//        public Builder weighting(Weighting weighting) {
//            this.opts.weighting = weighting;
//            return this;
//        }
//
//        /**
//         * For possible values see {@link Parameters.Algorithms}
//         */
//        public Builder algorithm(String algorithm) {
//            this.opts.algorithm = algorithm;
//            return this;
//        }
//
//        public Builder maxVisitedNodes(int maxVisitedNodes) {
//            this.opts.maxVisitedNodes = maxVisitedNodes;
//            return this;
//        }
//
//        public Builder hints(PMap hints) {
//            this.opts.hints.put(hints);
//            return this;
//        }
//
//        public Builder edgeFilter(EdgeFilter edgeFilter) {
//            this.opts.edgeFilter = edgeFilter;
//            return this;
//        }
//
//        public AlgorithmOptions build() {
//            if (buildCalled)
//                throw new IllegalStateException("Cannot call AlgorithmOptions.Builder.build() twice");
//
//            buildCalled = true;
//            return opts;
//        }
//    }
//
    // ORS-GH MOD START: handle additional edgeFilter to pass to algo
    protected EdgeFilter edgeFilter;

    public EdgeFilter getEdgeFilter() {
        return edgeFilter;
    }

    public void setEdgeFilter(EdgeFilter edgeFilter) {
        this.edgeFilter = edgeFilter;
    }
    // ORS-GH MOD END
}
