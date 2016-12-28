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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.CHGraphImpl;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Used in CH preparation and therefor assumed that all edges are of type CHEdgeIteratorState
 * <p>
 *
 * @author Peter Karich
 * @see PrepareContractionHierarchies
 */
public class PreparationWeighting implements Weighting {
    private final Weighting userWeighting;
    private final CHGraphImpl prepareGraph;

    private EdgeIteratorState origEdgeUv;
    private EdgeIteratorState origEdgeVw;
    private EdgeExplorer srcInExplorer;
    private EdgeExplorer targetOutExplorer;
    private int u_fromNode, w_toNode;
    private TraversalMode traversalMode;
    private TurnWeighting turnWeighting;
    private boolean isInPreparation;

    public PreparationWeighting(Weighting userWeighting, CHGraphImpl prepareGraph, TraversalMode traversalMode) {
        this.userWeighting = userWeighting;
        this.prepareGraph = prepareGraph;
        this.srcInExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(getFlagEncoder(), true, false));
        this.targetOutExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(getFlagEncoder(), false, true));
        this.traversalMode = traversalMode;
        this.isInPreparation = false;
    }

    public void initEdgebased(int u_fromNode, int w_toNode, EdgeIteratorState edgeUv, EdgeIteratorState edgeVw) {
        this.isInPreparation = true;
        this.u_fromNode = u_fromNode;
        this.w_toNode = w_toNode;
        origEdgeUv = edgeUv;
        origEdgeVw = edgeVw;
        if (userWeighting instanceof TurnWeighting)
            this.turnWeighting = (TurnWeighting) userWeighting;
    }

    public void preparationFinished() {
        this.isInPreparation = false;
    }

    public Weighting getUserWeighting() {
        return userWeighting;
    }

    @Override
    public final double getMinWeight(double distance) {
        return userWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        CHEdgeIteratorState tmp = (CHEdgeIteratorState) edgeState;

        // TODO: does this handle uturns and loops correctly?
        double addedMaxTurnCostChange = 0;
        if (traversalMode.isEdgeBased() && isInPreparation && turnWeighting != null) {
            int from = edgeState.getBaseNode();
            int to = edgeState.getAdjNode();
            if (reverse) {
                from = edgeState.getAdjNode();
                to = edgeState.getBaseNode();
            }
            if (from == u_fromNode) {
                // this is one of the first outbound edges of the search. Determine max. turn cost change for this edge
                EdgeIterator inIter = srcInExplorer.setBaseNode(edgeState.getBaseNode());
                while (inIter.next()) {
                    double skippedTc = turnWeighting.calcTurnWeight(inIter.getEdge(), u_fromNode, origEdgeUv.getEdge());
                    double addedTc = turnWeighting.calcTurnWeight(inIter.getEdge(), u_fromNode, edgeState.getEdge());
                    double tcc = addedTc - skippedTc;
                    if (tcc > addedMaxTurnCostChange)
                        addedMaxTurnCostChange = tcc;
                }
            } else if (to == w_toNode) {
                // this is one of the last inbound edges of the search. Determine turn cost change to all
                // outgoing edges from w and add that aswell
                EdgeIterator outIter = targetOutExplorer.setBaseNode(w_toNode);
                while (outIter.next()) {
                    double skippedTc = turnWeighting.calcTurnWeight(origEdgeVw.getEdge(), w_toNode, outIter.getEdge());
                    double addedTc = turnWeighting.calcTurnWeight(edgeState.getEdge(), w_toNode, outIter.getEdge());
                    double tcc = addedTc - skippedTc;
                    if (tcc > addedMaxTurnCostChange)
                        addedMaxTurnCostChange = tcc;
                }
            }
        }

        if (tmp.isShortcut()) {
            // if a shortcut is in both directions the weight is identical => no need for 'reverse'
            double weight = tmp.getWeight() + addedMaxTurnCostChange;
            if (turnWeighting != null && edgeState.getEdge() != EdgeIterator.NO_EDGE && prevOrNextEdgeId != EdgeIterator.NO_EDGE)
                weight += turnWeighting.calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId, reverse, true);
            return weight;
        }

        return userWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId) + addedMaxTurnCostChange;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return userWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return userWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches(HintsMap map) {
        return getName().equals(map.getWeighting()) && userWeighting.getFlagEncoder().toString().equals(map.getVehicle());
    }

    @Override
    public String getName() {
        return "prepare|" + userWeighting.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
