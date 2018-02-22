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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

public abstract class WitnessPathFinder {
    public static int maxOrigEdgesPerInitialEntry = 7;
    protected final CHGraph graph;
    protected final Weighting weighting;
    protected final TraversalMode traversalMode;
    protected final int maxLevel;
    protected final EdgeExplorer outEdgeExplorer;
    protected int numOnOrigPath;
    protected int avoidNode = Integer.MAX_VALUE;
    protected int maxOrigEdgesSettled;
    protected int numOrigEdgesSettled;

    public WitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, int maxLevel) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.maxLevel = maxLevel;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
    }

    public void setInitialEntries(IntObjectMap<WitnessSearchEntry> initialEntries) {
        reset();
        initEntries(initialEntries);
        if (numOnOrigPath != 1) {
            throw new IllegalStateException("There should be exactly one initial entry with onOrigPath = true, but given: " + numOnOrigPath);
        }
        maxOrigEdgesSettled = initialEntries.size() * maxOrigEdgesPerInitialEntry;
    }

    protected abstract void initEntries(IntObjectMap<WitnessSearchEntry> initialEntries);

    public abstract CHEntry getFoundEntry(int edge, int adjNode);

    public abstract CHEntry getFoundEntryNoParents(int edge, int adjNode);

    public abstract void findTarget(int targetEdge, int targetNode);

    private void reset() {
        numOrigEdgesSettled = 0;
        numOnOrigPath = 0;
        maxOrigEdgesSettled = Integer.MAX_VALUE;
        avoidNode = Integer.MAX_VALUE;
        doReset();
    }

    protected abstract void doReset();

    protected int getEdgeKey(int edge, int adjNode) {
        // todo: we should check if calculating the edge key this way affects performance, this method is probably run
        // millions of times
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        EdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    protected boolean isContracted(int node) {
        return graph.getLevel(node) != maxLevel;
    }
}
