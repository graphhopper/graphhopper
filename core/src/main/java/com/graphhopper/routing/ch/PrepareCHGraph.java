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

import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Helper adapter api over {@link CHGraph} used for CH preparation.
 */
public class PrepareCHGraph {
    private final CHGraph chGraph;
    private final Weighting weighting;
    private final TurnWeighting turnWeighting;
    private final FlagEncoder encoder;

    public static PrepareCHGraph nodeBased(CHGraph chGraph, Weighting weighting) {
        if (chGraph.getCHProfile().isEdgeBased()) {
            throw new IllegalArgumentException("Expected node-based CHGraph, but was edge-based");
        }
        return new PrepareCHGraph(chGraph, weighting, null);
    }

    public static PrepareCHGraph edgeBased(CHGraph chGraph, Weighting weighting, TurnWeighting turnWeighting) {
        if (!chGraph.getCHProfile().isEdgeBased()) {
            throw new IllegalArgumentException("Expected edge-based CHGraph, but was node-based");
        }
        return new PrepareCHGraph(chGraph, weighting, turnWeighting);
    }

    private PrepareCHGraph(CHGraph chGraph, Weighting weighting, TurnWeighting turnWeighting) {
        this.chGraph = chGraph;
        this.encoder = weighting.getFlagEncoder();
        this.weighting = weighting;
        this.turnWeighting = turnWeighting;
    }

    public PrepareCHEdgeExplorer createInEdgeExplorer() {
        return new PrepareCHEdgeIteratorImpl(chGraph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder)), weighting);
    }

    public PrepareCHEdgeExplorer createOutEdgeExplorer() {
        return new PrepareCHEdgeIteratorImpl(chGraph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder)), weighting);
    }

    public PrepareCHEdgeExplorer createAllEdgeExplorer() {
        return new PrepareCHEdgeIteratorImpl(chGraph.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder)), weighting);
    }

    public EdgeExplorer createOriginalInEdgeExplorer() {
        return chGraph.createOriginalEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));
    }

    public EdgeExplorer createOriginalOutEdgeExplorer() {
        return chGraph.createOriginalEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
    }

    public int getNodes() {
        return chGraph.getNodes();
    }

    public int getEdges() {
        return chGraph.getEdges();
    }

    public int getOriginalEdges() {
        return chGraph.getOriginalEdges();
    }

    public int getLevel(int node) {
        return chGraph.getLevel(node);
    }

    public void setLevel(int node, int level) {
        chGraph.setLevel(node, level);
    }

    public int shortcut(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2) {
        return chGraph.shortcut(a, b, accessFlags, weight, skippedEdge1, skippedEdge2);
    }

    public int shortcutEdgeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2, int origFirst, int origLast) {
        return chGraph.shortcutEdgeBased(a, b, accessFlags, weight, skippedEdge1, skippedEdge2, origFirst, origLast);
    }

    public int getOtherNode(int edge, int adjNode) {
        return chGraph.getOtherNode(edge, adjNode);
    }

    public NodeAccess getNodeAccess() {
        return chGraph.getNodeAccess();
    }

    double getTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (turnWeighting == null) {
            throw new IllegalStateException("Calculating turn weights is only allowed when using edge-based traversal mode");
        }
        return turnWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    public AllCHEdgesIterator getAllEdges() {
        return chGraph.getAllEdges();
    }

    boolean isReadyForContraction() {
        return chGraph.isReadyForContraction();
    }

    /**
     * Disconnects the edges (higher to lower node) via the specified edgeState pointing from lower to
     * higher node.
     * <p>
     *
     * @param edgeState the edge from lower to higher
     */
    public void disconnect(PrepareCHEdgeExplorer explorer, PrepareCHEdgeIterator edgeState) {
        // search edge with opposite direction but we need to know the previousEdge so we cannot simply do:
        // EdgeIteratorState tmpIter = getEdgeIteratorState(edgeState.getEdge(), edgeState.getBaseNode());
        PrepareCHEdgeIterator tmpIter = explorer.setBaseNode(edgeState.getAdjNode());
        int prevEdge = EdgeIterator.NO_EDGE;
        while (tmpIter.next()) {
            // note that we do not disconnect original edges, because we are re-using the base graph for different profiles,
            // even though this is not optimal from a speed performance point of view.
            if (tmpIter.isShortcut() && tmpIter.getEdge() == edgeState.getEdge()) {
                chGraph.disconnectEdge(edgeState.getEdge(), edgeState.getAdjNode(), prevEdge);
                break;
            }

            prevEdge = tmpIter.getEdge();
        }
    }
}
