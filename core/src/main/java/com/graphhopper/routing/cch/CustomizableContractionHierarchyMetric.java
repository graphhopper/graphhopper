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
package com.graphhopper.routing.cch;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;

import static com.graphhopper.routing.cch.CustomizableContractionHierarchy.INF;
import static com.graphhopper.routing.cch.CustomizableContractionHierarchy.satAdd;

/**
 * Basic (not perfect) customization of a {@link CustomizableContractionHierarchy}.
 * Calling {@link #customize(Weighting)} again replaces the metric. The topology stays unchanged,
 * so a traversal that was infinite for one weighting can be finite for the next, as long as the
 * access filter used at build time accepted it.
 */
public final class CustomizableContractionHierarchyMetric {
    private static final int NO_WITNESS = -1;
    private static final int NO_EDGE_KEY = -1;

    final CustomizableContractionHierarchy cch;
    long[] forward = new long[0];
    long[] backward = new long[0];
    int[] witnessForward = new int[0];
    int[] witnessBackward = new int[0];
    int[] forwardEdgeKey = new int[0];
    int[] backwardEdgeKey = new int[0];

    public CustomizableContractionHierarchyMetric(CustomizableContractionHierarchy cch) {
        if (cch == null)
            throw new IllegalArgumentException("CCH topology must not be null");
        this.cch = cch;
    }

    public CustomizableContractionHierarchyMetric customize(Weighting weighting) {
        if (weighting == null)
            throw new IllegalArgumentException("weighting must not be null");
        if (weighting.hasTurnCosts())
            throw new IllegalArgumentException("CCH is node-based and does not support turn costs");

        int arcs = cch.upHead.length;
        forward = new long[arcs];
        backward = new long[arcs];
        Arrays.fill(forward, INF);
        Arrays.fill(backward, INF);
        witnessForward = new int[arcs];
        witnessBackward = new int[arcs];
        Arrays.fill(witnessForward, NO_WITNESS);
        Arrays.fill(witnessBackward, NO_WITNESS);
        forwardEdgeKey = new int[arcs];
        backwardEdgeKey = new int[arcs];
        Arrays.fill(forwardEdgeKey, NO_EDGE_KEY);
        Arrays.fill(backwardEdgeKey, NO_EDGE_KEY);

        for (int arc = 0; arc < arcs; arc++) {
            seed(cch.forwardInputKeys[arc], arc, true, weighting);
            seed(cch.backwardInputKeys[arc], arc, false, weighting);
        }
        relaxLowerTriangles();
        return this;
    }

    private void seed(IntArrayList keys, int arc, boolean forwardDirection, Weighting weighting) {
        if (keys == null)
            return;
        for (int i = 0; i < keys.size(); i++) {
            int edgeKey = keys.get(i);
            EdgeIteratorState edge = cch.graph.getEdgeIteratorStateForKey(edgeKey);
            double raw = weighting.calcEdgeWeight(edge, false);
            if (!Double.isFinite(raw))
                continue;
            long weight = (long) Weighting.roundWeight(raw);
            if (forwardDirection) {
                if (weight < forward[arc]) {
                    forward[arc] = weight;
                    forwardEdgeKey[arc] = edgeKey;
                }
            } else if (weight < backward[arc]) {
                backward[arc] = weight;
                backwardEdgeKey[arc] = edgeKey;
            }
        }
    }

    /**
     * For every triangle whose lowest node is {@code x} and whose other corners are upward neighbors
     * {@code y}, {@code z} (rank y &lt; rank z), relax the arc {@code y -> z}:
     * forward is the path y → x → z, backward is z → x → y.
     */
    private void relaxLowerTriangles() {
        int nodes = cch.nodes;
        for (int level = 0; level < nodes; level++) {
            int x = cch.order[level];
            int begin = cch.upFirstOut[x];
            int end = cch.upFirstOut[x + 1];
            for (int xy = begin; xy < end; xy++) {
                int y = cch.upHead[xy];
                for (int xz = xy + 1; xz < end; xz++) {
                    int z = cch.upHead[xz];
                    int yz = cch.upArc(y, z);
                    long viaForward = satAdd(backward[xy], forward[xz]);
                    if (viaForward < forward[yz]) {
                        forward[yz] = viaForward;
                        witnessForward[yz] = x;
                    }
                    long viaBackward = satAdd(backward[xz], forward[xy]);
                    if (viaBackward < backward[yz]) {
                        backward[yz] = viaBackward;
                        witnessBackward[yz] = x;
                    }
                }
            }
        }
    }
}
