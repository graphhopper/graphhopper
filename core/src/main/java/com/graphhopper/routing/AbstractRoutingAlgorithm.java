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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.QueryGraphWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Collections;
import java.util.List;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm {
    protected final Graph graph;
    protected final Weighting weighting;
    protected final TraversalMode traversalMode;
    protected final NodeAccess nodeAccess;
    protected final EdgeExplorer edgeExplorer;
    protected int maxVisitedNodes = Integer.MAX_VALUE;
    protected long timeoutMillis = Long.MAX_VALUE;
    private long finishTimeMillis = Long.MAX_VALUE;
    private boolean alreadyRun;

    /**
     * @param graph         specifies the graph where this algorithm will run on
     * @param weighting     set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
     */
    public AbstractRoutingAlgorithm(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        if (weighting.hasTurnCosts() && !traversalMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");
        if (graph instanceof QueryGraph && !(weighting instanceof QueryGraphWeighting))
            throw new IllegalStateException("Weighting must use QueryGraphWeighting");
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        edgeExplorer = graph.createEdgeExplorer();
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes) {
        this.maxVisitedNodes = numberOfNodes;
    }

    @Override
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    protected boolean accept(EdgeIteratorState iter, int prevOrNextEdgeId) {
        // for edge-based traversal we leave it for calcTurnWeight to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        return traversalMode.isEdgeBased() || iter.getEdge() != prevOrNextEdgeId;
    }

    protected void checkAlreadyRun() {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
    }

    protected void setupFinishTime() {
        try {
            this.finishTimeMillis = Math.addExact(System.currentTimeMillis(), timeoutMillis);
        } catch (ArithmeticException e) {
            this.finishTimeMillis = Long.MAX_VALUE;
        }
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        return Collections.singletonList(calcPath(from, to));
    }

    protected Path createEmptyPath() {
        return new Path(graph);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

    protected boolean isMaxVisitedNodesExceeded() {
        return maxVisitedNodes < getVisitedNodes();
    }

    protected boolean isTimeoutExceeded() {
        return finishTimeMillis < Long.MAX_VALUE && System.currentTimeMillis() > finishTimeMillis;
    }

}
