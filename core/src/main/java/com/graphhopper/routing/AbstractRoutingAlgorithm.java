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

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
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
    protected final FlagEncoder flagEncoder;
    protected final TraversalMode traversalMode;
    protected NodeAccess nodeAccess;
    protected EdgeExplorer inEdgeExplorer;
    protected EdgeExplorer outEdgeExplorer;
    protected int maxVisitedNodes = Integer.MAX_VALUE;
    protected EdgeFilter additionalEdgeFilter;
    private boolean alreadyRun;

    /**
     * @param graph         specifies the graph where this algorithm will run on
     * @param weighting     set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
     */
    public AbstractRoutingAlgorithm(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        this.weighting = weighting;
        this.flagEncoder = weighting.getFlagEncoder();
        this.traversalMode = traversalMode;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        outEdgeExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(flagEncoder));
        inEdgeExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(flagEncoder));
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes) {
        this.maxVisitedNodes = numberOfNodes;
    }

    public RoutingAlgorithm setEdgeFilter(EdgeFilter additionalEdgeFilter) {
        this.additionalEdgeFilter = additionalEdgeFilter;
        return this;
    }

    protected boolean accept(EdgeIteratorState iter, int prevOrNextEdgeId) {
        if (!traversalMode.hasUTurnSupport() && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(iter);
    }

    protected void checkAlreadyRun() {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
    }

    /**
     * To be overwritten from extending class. Should we make this available in RoutingAlgorithm
     * interface?
     * <p>
     *
     * @return true if finished.
     */
    protected abstract boolean finished();

    /**
     * To be overwritten from extending class. Should we make this available in RoutingAlgorithm
     * interface?
     * <p>
     *
     * @return true if finished.
     */
    protected abstract Path extractPath();

    @Override
    public List<Path> calcPaths(int from, int to) {
        return Collections.singletonList(calcPath(from, to));
    }

    protected Path createEmptyPath() {
        return new Path(graph, weighting);
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
}
