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

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

/**
 * The algorithm options. Create an immutable object via:
 * <pre>
 * AlgorithmOptions algoOpts = AlgorithmOptions.start().
 *        algorithm(Parameters.Algorithms.DIJKSTRA).
 *        weighting(weighting).
 *        build();
 * </pre>
 * <p>
 *
 * @author Peter Karich
 */
public class AlgorithmOptions {
    private final PMap hints = new PMap(5);
    private String algorithm = Parameters.Algorithms.DIJKSTRA_BI;
    private Weighting weighting;
    private TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private int maxVisitedNodes = Integer.MAX_VALUE;

    private AlgorithmOptions() {
    }

    /**
     * Default traversal mode NODE_BASED is used.
     */
    public AlgorithmOptions(String algorithm, Weighting weighting) {
        this.algorithm = algorithm;
        this.weighting = weighting;
    }

    public AlgorithmOptions(String algorithm, Weighting weighting, TraversalMode tMode) {
        this.algorithm = algorithm;
        this.weighting = weighting;
        this.traversalMode = tMode;
    }

    /**
     * This method starts the building process for AlgorithmOptions.
     */
    public static Builder start() {
        return new Builder();
    }

    /**
     * This method clones the specified AlgorithmOption object with the possibility for further
     * changes.
     */
    public static Builder start(AlgorithmOptions opts) {
        Builder b = new Builder();
        if (opts.algorithm != null)
            b.algorithm(opts.getAlgorithm());
        if (opts.traversalMode != null)
            b.traversalMode(opts.getTraversalMode());
        if (opts.weighting != null)
            b.weighting(opts.getWeighting());
        if (opts.maxVisitedNodes >= 0)
            b.maxVisitedNodes(opts.maxVisitedNodes);
        if (!opts.hints.isEmpty())
            b.hints(opts.hints);

        return b;
    }

    /**
     * @return the traversal mode, where node-based is the default.
     */
    public TraversalMode getTraversalMode() {
        return traversalMode;
    }

    public boolean hasWeighting() {
        return weighting != null;
    }

    public Weighting getWeighting() {
        assertNotNull(weighting, "weighting");
        return weighting;
    }

    public String getAlgorithm() {
        assertNotNull(algorithm, "algorithm");
        return algorithm;
    }

    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    public PMap getHints() {
        return hints;
    }

    private void assertNotNull(Object optionValue, String optionName) {
        if (optionValue == null)
            throw new NullPointerException("Option '" + optionName + "' must NOT be null");
    }

    @Override
    public String toString() {
        return algorithm + ", " + weighting + ", " + traversalMode;
    }

    public static class Builder {
        private AlgorithmOptions opts = new AlgorithmOptions();
        private boolean buildCalled;

        public Builder traversalMode(TraversalMode traversalMode) {
            if (traversalMode == null)
                throw new IllegalArgumentException("null as traversal mode is not allowed");

            this.opts.traversalMode = traversalMode;
            return this;
        }

        public Builder weighting(Weighting weighting) {
            this.opts.weighting = weighting;
            return this;
        }

        /**
         * For possible values see Parameters.Algorithms
         */
        public Builder algorithm(String algorithm) {
            this.opts.algorithm = algorithm;
            return this;
        }

        public Builder maxVisitedNodes(int maxVisitedNodes) {
            this.opts.maxVisitedNodes = maxVisitedNodes;
            return this;
        }

        public Builder hints(PMap hints) {
            this.opts.hints.put(hints);
            return this;
        }

        public AlgorithmOptions build() {
            if (buildCalled)
                throw new IllegalStateException("Cannot call AlgorithmOptions.Builder.build() twice");

            buildCalled = true;
            return opts;
        }
    }
}
