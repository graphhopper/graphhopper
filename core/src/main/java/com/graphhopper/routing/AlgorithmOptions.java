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

}
