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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;

import java.util.*;

import static com.graphhopper.util.Helper.nf;
import static java.lang.System.nanoTime;

/**
 * Makes it possible to control the node contraction order in contraction hierarchies, which is useful for debugging
 * and performance analysis.
 */
public class ManualPrepareContractionHierarchies extends PrepareContractionHierarchies {
    private List<Integer> contractionOrder = new ArrayList<>();
    private List<Stats> stats = new ArrayList<>();
    private int lastShortcutCount;

    public ManualPrepareContractionHierarchies(Directory dir, GraphHopperStorage ghStorage, CHGraph chGraph, Weighting weighting,
                                               TraversalMode traversalMode) {
        super(dir, ghStorage, chGraph, weighting, traversalMode);
    }

    public ManualPrepareContractionHierarchies setContractionOrder(List<Integer> contractionOrder) {
        this.contractionOrder = contractionOrder;
        return this;
    }

    public ManualPrepareContractionHierarchies setSeed(long seed) {
        this.contractionOrder = createRandomContractionOrder(seed);
        return this;
    }

    @Override
    public String toString() {
        return super.toString() + "|manual";
    }

    @Override
    protected void runGraphContraction() {
        setMaxLevelOnAllNodes();
        contractNodes();
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            prepareGraph.setLevel(node, nodes);
        }
    }

    private void contractNodes() {
        if (contractionOrder.size() != prepareGraph.getNodes()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "contraction order size (%d) must be equal to number of nodes in graph (%d)",
                    contractionOrder.size(), prepareGraph.getNodes()));
        }
        final EdgeFilter allFilter = new DefaultEdgeFilter(prepareWeighting.getFlagEncoder(), true, true);
        CHEdgeExplorer explorer = prepareGraph.createEdgeExplorer(allFilter);
        final int nodesToContract = (int) (contractionOrder.size() * nodesContractedPercentage / 100);
        final long logSize = Math.round(Math.max(10, contractionOrder.size() * logMessagesPercentage / 100));
        int degree = 0;
        long startTime = nanoTime();
        for (int i = 0; i < nodesToContract; ++i) {
            int node = contractionOrder.get(i);
            degree += nodeContractor.contractNode(node);
            int shortcutCount = nodeContractor.getAddedShortcutsCount();
            if (isEdgeBased()) {
                int numPolled = ((EdgeBasedNodeContractor) nodeContractor).getNumPolledEdges();
                int numSearches = ((EdgeBasedNodeContractor) nodeContractor).getNumSearches();
                stats.add(new Stats(shortcutCount - lastShortcutCount, node, numPolled, numSearches));
                lastShortcutCount = shortcutCount;
            }
            prepareGraph.setLevel(node, i);

            // todo: !!
            // without disconnecting the degree of the graph rises and the contraction
            // becomes slower with each contracted node. however, using it showed an infinite loop
            // and also makes some tests fail, although we do it just like its done in PCH ??
//            int maxLevel = prepareGraph.getNodes();
//            CHEdgeIterator iter = explorer.setBaseNode(node);
//            while (iter.next()) {
//                int nn = iter.getAdjNode();
//                if (prepareGraph.getLevel(nn) != maxLevel)
//                    continue;
//
//                prepareGraph.disconnect(explorer, iter);
//            }
            if (i % logSize == 0) {
                long elapsed = nanoTime() - startTime;
                logger.info(String.format("contracted %s / %s nodes, shortcuts: %s, avg degree: %.2f, last batch took: %.2f s, time per node: %.2f micros",
                        nf(i), nf(nodesToContract), nf(shortcutCount),
                        degree / (double) logSize, elapsed * 1.e-9, elapsed / logSize * 1.e-3));
                degree = 0;
                startTime = nanoTime();
            }
        }
    }

    public List<Stats> getStats() {
        return stats;
    }

    private List<Integer> createRandomContractionOrder(long seed) {
        int nodes = prepareGraph.getNodes();
        List<Integer> result = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; ++i) {
            result.add(i);
        }
        // the shuffle method is the only reason we are using java.util.ArrayList instead of hppc IntArrayList
        Collections.shuffle(result, new Random(seed));
        return result;
    }

    public static class Stats {
        public int shortcutCount;
        public int nodeId;
        public int numPolled;
        public int numSearches;

        public Stats(int shortcutCount, int nodeId, int numPolled, int numSearches) {
            this.shortcutCount = shortcutCount;
            this.nodeId = nodeId;
            this.numPolled = numPolled;
            this.numSearches = numSearches;
        }
    }

}
