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

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.List;

/**
 * This parser takes the turn restrictions from OSM (OSM does not provide turn costs, but only restrictions) and creates the appropriate turn costs (with value infinity)
 */
public class OSMTurnRelationParser implements TurnCostParser {
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue turnCostEnc;
    private final List<String> restrictions;
    private EdgeExplorer cachedOutExplorer, cachedInExplorer;

    public OSMTurnRelationParser(BooleanEncodedValue accessEnc, DecimalEncodedValue turnCostEnc, List<String> restrictions) {
        if (restrictions.isEmpty())
            throw new IllegalArgumentException("restrictions cannot be empty");
        this.accessEnc = accessEnc;
        this.turnCostEnc = turnCostEnc;
        this.restrictions = restrictions;
    }

    @Override
    public void handleTurnRelationTags(OSMTurnRelation turnRelation, ExternalInternalMap map, Graph graph) {
        if (!turnRelation.isVehicleTypeConcernedByTurnRestriction(restrictions))
            return;

        addRelationToTCStorage(turnRelation, map, graph);
    }

    private EdgeExplorer getInExplorer(Graph graph) {
        return cachedInExplorer == null ? cachedInExplorer = graph.createEdgeExplorer(AccessFilter.inEdges(accessEnc)) : cachedInExplorer;
    }

    private EdgeExplorer getOutExplorer(Graph graph) {
        return cachedOutExplorer == null ? cachedOutExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(accessEnc)) : cachedOutExplorer;
    }

    /**
     * Add the specified relation to the TurnCostStorage
     */
    void addRelationToTCStorage(OSMTurnRelation osmTurnRelation, ExternalInternalMap map, Graph graph) {
        TurnCostStorage tcs = graph.getTurnCostStorage();
        int viaNode = map.getInternalNodeIdOfOsmNode(osmTurnRelation.getViaOsmNodeId());
        EdgeExplorer edgeOutExplorer = getOutExplorer(graph), edgeInExplorer = getInExplorer(graph);

        try {
            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by fromOsm
            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNode);

            while (iter.next()) {
                if (map.getOsmIdOfInternalEdge(iter.getEdge()) == osmTurnRelation.getOsmIdFrom()) {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            if (!EdgeIterator.Edge.isValid(edgeIdFrom))
                return;

            // get all outgoing edges of the via node
            iter = edgeOutExplorer.setBaseNode(viaNode);
            // for TYPE_ONLY_* we add ALL restrictions (from, via, * ) EXCEPT the given turn
            // for TYPE_NOT_*  we add ONE restriction  (from, via, to)
            while (iter.next()) {
                int edgeId = iter.getEdge();
                long wayId = map.getOsmIdOfInternalEdge(edgeId);
                if (edgeId != edgeIdFrom && osmTurnRelation.getRestriction() == OSMTurnRelation.Type.ONLY && wayId != osmTurnRelation.getOsmIdTo()
                        || osmTurnRelation.getRestriction() == OSMTurnRelation.Type.NOT && wayId == osmTurnRelation.getOsmIdTo() && wayId >= 0) {
                    tcs.set(turnCostEnc, edgeIdFrom, viaNode, iter.getEdge(), Double.POSITIVE_INFINITY);
                    if (osmTurnRelation.getRestriction() == OSMTurnRelation.Type.NOT)
                        break;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not built turn table entry for relation of node with osmId:" + osmTurnRelation.getViaOsmNodeId(), e);
        }
    }

    @Override
    public String getName() {
        return turnCostEnc.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
