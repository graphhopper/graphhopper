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

package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.reader.osm.GraphRestriction;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.reader.osm.RestrictionType;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

import java.util.List;

import static com.graphhopper.reader.osm.RestrictionType.NO;
import static com.graphhopper.reader.osm.RestrictionType.ONLY;

public class RestrictionSetter {
    private final BaseGraph baseGraph;
    private final EdgeExplorer edgeExplorer;
    private final IntIntMap artificialEdgesByEdges = new IntIntHashMap();

    public RestrictionSetter(BaseGraph baseGraph) {
        this.baseGraph = baseGraph;
        this.edgeExplorer = baseGraph.createEdgeExplorer();
    }

    /**
     * Adds all the turn restriction entries to the graph that are needed to enforce the given restrictions, for
     * a single turn cost encoded value.
     * Implementing via-way turn restrictions requires adding artificial edges to the graph, which is also handled here.
     * Since we keep track of the added artificial edges here it is important to only use one RestrictionSetter instance
     * for **all** turn restrictions and vehicle types.
     */
    public void setRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        // we first need to add all the artificial edges, because we might need to restrict turns between artificial
        // edges created for different restrictions (when restrictions are overlapping)
        addArtificialEdges(restrictions);
        // now we can add all the via-way restrictions
        addViaWayRestrictions(restrictions, turnCostEnc);
        // ... and finally all the via-node restrictions
        addViaNodeRestrictions(restrictions, turnCostEnc);
    }

    private void addArtificialEdges(List<Pair<GraphRestriction, RestrictionType>> restrictions) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.first.isViaWayRestriction()) {
                if (ignoreViaWayRestriction(p)) continue;
                for (IntCursor viaEdgeCursor : p.first.getViaEdges()) {
                    int viaEdge = viaEdgeCursor.value;
                    int artificialEdge = artificialEdgesByEdges.getOrDefault(viaEdge, -1);
                    if (artificialEdge < 0) {
                        EdgeIteratorState viaEdgeState = baseGraph.getEdgeIteratorState(viaEdge, Integer.MIN_VALUE);
                        EdgeIteratorState artificialEdgeState = baseGraph.edge(viaEdgeState.getBaseNode(), viaEdgeState.getAdjNode())
                                .setFlags(viaEdgeState.getFlags())
                                .setWayGeometry(viaEdgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY))
                                .setDistance(viaEdgeState.getDistance())
                                .setKeyValues(viaEdgeState.getKeyValues());
                        artificialEdge = artificialEdgeState.getEdge();
                        artificialEdgesByEdges.put(viaEdge, artificialEdge);
                    }
                }
            }
        }
    }

    private void addViaWayRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        IntSet viaEdgesUsedByOnlyRestrictions = new IntHashSet();
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (!p.first.isViaWayRestriction()) continue;
            if (ignoreViaWayRestriction(p)) continue;
            final int fromEdge = p.first.getFromEdges().get(0);
            final int toEdge = p.first.getToEdges().get(0);
            final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
            final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
            final int nViaEdges = p.first.getViaEdges().size(); 
            
            for (int i = 0; i < nViaEdges; i++) {
                int viaNode = p.first.getViaNodes().get(i);
                int viaEdge = p.first.getViaEdges().get(i);
                if (p.second == ONLY && !viaEdgesUsedByOnlyRestrictions.add(viaEdge))
                    throw new IllegalStateException("We cannot deal with 'only' via-way restrictions that use the same via edges");
                int artificialVia = artificialEdgesByEdges.getOrDefault(viaEdge, viaEdge);
                if (artificialVia == viaEdge)
                    throw new IllegalArgumentException("There should be an artificial edge for every via edge of a way restriction");
                preventTurnBetweenOriginalEdgeAndArtificialCounterpart(turnCostEnc, artificialVia, viaNode, viaEdge);
                
                // FROM WAY TO FIRST VIA WAY
                // -> NO: restrict the original turn, divert onto the artificial edge
                // -> ONLY: force turning onto the artificial edge
                if (i == 0) {
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, fromEdge, viaNode, viaEdge);
                        // if an artificial edge of an overlapping restriction exists, we restrict it similarly
                        if (artificialFrom != fromEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, viaEdge);
                    }
                    else if (p.second == ONLY) {
                    	mandatoryTurn(turnCostEnc, fromEdge, viaNode, artificialVia);
                    	if (artificialFrom != fromEdge)
                    		mandatoryTurn(turnCostEnc, artificialFrom, viaNode, artificialVia);
                    } 
                    else {
                        throw new IllegalArgumentException("Unexpected restriction type: " + p.second);
                    }
                }
                
                // VIA WAY TO VIA WAY
                // -> NO: prevent turn on original via edge, but allow all other turns
                // -> ONLY: force stay on artificial edge
                if (i > 0 && i < nViaEdges) {
                    int oldArtificialViaEdge = artificialEdgesByEdges.get(p.first.getViaEdges().get(i-1));                    
                    if (p.second == NO)
                        restrictTurn(turnCostEnc, oldArtificialViaEdge, viaNode, viaEdge);
                    else if (p.second == ONLY)
                    	mandatoryTurn(turnCostEnc, oldArtificialViaEdge, viaNode, artificialVia);
                }

                // LAST VIA WAY TO TO WAY
                // -> NO: Deny turning on to way
                // -> ONLY: Force turning on to way
                if (i == nViaEdges - 1) {
                	int viaToToNode = p.first.getViaNodes().get(i+1);
                	preventTurnBetweenOriginalEdgeAndArtificialCounterpart(turnCostEnc, artificialVia, viaToToNode, viaEdge);
                	if (p.second == NO) {
                        restrictTurn(turnCostEnc, artificialVia, viaToToNode, toEdge);
                        if (artificialTo != toEdge)
                            restrictTurn(turnCostEnc, artificialVia, viaToToNode, artificialTo);
                	}
                    else if (p.second == ONLY)
                    	mandatoryTurn(turnCostEnc, artificialVia, viaToToNode, toEdge, artificialTo);
                }
            }
        }    
    }

    private void addViaNodeRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.first.isViaWayRestriction()) continue;
            final int viaNode = p.first.getViaNodes().get(0);
            for (IntCursor fromEdgeCursor : p.first.getFromEdges()) {
                for (IntCursor toEdgeCursor : p.first.getToEdges()) {
                    final int fromEdge = fromEdgeCursor.value;
                    final int toEdge = toEdgeCursor.value;
                    final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
                    final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
                    if (p.second == NO) {
                        restrictTurn(turnCostEnc, fromEdge, viaNode, toEdge);
                        // we also need to restrict this term in case there are artificial edges for the from- and/or to-edge
                        if (artificialFrom != fromEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, toEdge);
                        if (artificialTo != toEdge)
                            restrictTurn(turnCostEnc, fromEdge, viaNode, artificialTo);
                        if (artificialFrom != fromEdge && artificialTo != toEdge)
                            restrictTurn(turnCostEnc, artificialFrom, viaNode, artificialTo);
                    } else if (p.second == ONLY) {
                        // we need to restrict all turns except the one, but that also means not restricting the
                        // artificial counterparts of these turns, if they exist.
                        // we do not explicitly restrict the U-turn from the from-edge back to the from-edge though.
                    	mandatoryTurn(turnCostEnc, fromEdge, viaNode, toEdge, artificialTo);
                        // and the same for the artificial edge belonging to the from-edge if it exists
                    	if (fromEdge != artificialFrom) {
                    		mandatoryTurn(turnCostEnc, artificialFrom, viaNode, toEdge, artificialTo);
                    	}
                    } else {
                        throw new IllegalArgumentException("Unexpected restriction type: " + p.second);
                    }
                }
            }
        }
    }

    public IntIntMap getArtificialEdgesByEdges() {
        return artificialEdgesByEdges;
    }

    private void preventTurnBetweenOriginalEdgeAndArtificialCounterpart(DecimalEncodedValue turnCostEnc, int artificial, int viaNode, int real) {
        restrictTurn(turnCostEnc, artificial, viaNode, real);
        restrictTurn(turnCostEnc, real, viaNode, artificial);
    }
    
    private void restrictTurn(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        if (fromEdge < 0 || toEdge < 0 || viaNode < 0)
            throw new IllegalArgumentException("from/toEdge and viaNode must be >= 0");
        baseGraph.getTurnCostStorage().set(turnCostEnc, fromEdge, viaNode, toEdge, Double.POSITIVE_INFINITY);
    }
    	
	private void mandatoryTurn(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int... allowedEdge) {
		// we restrict all turns BUT the allowed edge(s)
	    if (allowedEdge.length < 1 || allowedEdge.length > 2)
	    	throw new IllegalArgumentException("there can only be one (the to-edge) or two (the to-edge and artificial counterpart) allowed edges");
		EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
	    while (iter.next())
	        if (iter.getEdge() != fromEdge && !isAllowedEdge(allowedEdge, iter.getEdge()))
	        	restrictTurn(turnCostEnc, fromEdge, viaNode, iter.getEdge());
	}
	
	private static boolean isAllowedEdge(int[] edges, int edge) {
		if (edges.length > 0 && edges[0] == edge)
			return true;
		else if (edges.length > 1 && edges[1] == edge)
			return true;
		else
			return false;
	}
	
    private static boolean ignoreViaWayRestriction(Pair<GraphRestriction, RestrictionType> p) {
        // todo: how frequent are these? -> none in germany
        if (p.first.getFromEdges().size() > 1 || p.first.getToEdges().size() > 1)
            // no multi-from or -to yet
            return true;
        return false;
    }
}