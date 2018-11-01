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
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.StopWatch;

import java.util.*;

import static com.graphhopper.util.Helper.nf;

/**
 * Makes it possible to control the node contraction order in contraction hierarchies, which is useful for debugging
 * and performance analysis.
 * todo: remove this class and move the same functionality into PrepareContractionHierarchies. Also this should be
 * extended to the case where we can save/load the contraction order to/from disk. For example when we use another
 * vehicle profile we still might get reasonable results for a previous contraction order.
 */
public class ManualPrepareContractionHierarchies extends PrepareContractionHierarchies {
    private List<Integer> contractionOrder = new ArrayList<>();

    public ManualPrepareContractionHierarchies(Directory dir, GraphHopperStorage ghStorage, CHGraph chGraph, TraversalMode traversalMode) {
        super(dir, ghStorage, chGraph, traversalMode);
    }

    ManualPrepareContractionHierarchies setContractionOrder(List<Integer> contractionOrder) {
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
        final EdgeFilter allFilter = DefaultEdgeFilter.allEdges(prepareWeighting.getFlagEncoder());
        CHEdgeExplorer explorer = prepareGraph.createEdgeExplorer(allFilter);
        CHEdgeExplorer discExplorer = prepareGraph.createEdgeExplorer(allFilter);
        final int nodesToContract = contractionOrder.size();
        final long logSize = 10;
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < nodesToContract; ++i) {
            stopWatch.start();
            int node = contractionOrder.get(i);
            // contract node
            nodeContractor.contractNode(node);
            long shortcutCount = nodeContractor.getAddedShortcutsCount();
            prepareGraph.setLevel(node, i);

            // disconnect neighbors
            CHEdgeIterator iter = explorer.setBaseNode(node);
            while (iter.next()) {
                if (prepareGraph.getLevel(iter.getAdjNode()) != maxLevel)
                    continue;
                prepareGraph.disconnect(discExplorer, iter);
            }
            stopWatch.stop();
            if (i % logSize == 0) {
                logger.info(String.format("contracted %s / %s nodes, shortcuts: %s, last batch took: %.2f s, time per node: %.2f micros, %s",
                        nf(i), nf(nodesToContract), nf(shortcutCount), stopWatch.getSeconds(),
                        stopWatch.getNanos() / logSize * 1.e-3, nodeContractor.getStatisticsString()));
            }
        }
    }

    private List<Integer> createRandomContractionOrder(long seed) {
        int nodes = prepareGraph.getNodes();
        List<Integer> result = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; ++i) {
            result.add(i);
        }
        // the shuffle method is the only reason we are using java.util.ArrayList instead of hppc IntArrayList here
        Collections.shuffle(result, new Random(seed));
        return result;
    }

}
