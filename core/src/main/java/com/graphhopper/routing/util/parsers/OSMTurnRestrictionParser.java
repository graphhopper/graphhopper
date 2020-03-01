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

import com.graphhopper.reader.OSMTurnRestriction;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * This parser takes the turn restrictions from OSM (OSM does not provide turn costs, but only restrictions) and creates the appropriate turn costs (with value infinity)
 */
public class OSMTurnRestrictionParser implements TurnRestrictionParser {
    private String name;
    private DecimalEncodedValue turnCostEnc;
    private final int maxTurnCosts;
    private final Collection<String> restrictions;
    private BooleanEncodedValue accessEnc;
    private EdgeExplorer cachedOutExplorer, cachedInExplorer;

    /**
     * @param maxTurnCosts specify the maximum value used for turn costs, if this value is reached a
     *                     turn is forbidden and results in costs of positive infinity.
     */
    public OSMTurnRestrictionParser(String name, int maxTurnCosts) {
        this.name = name;
        this.maxTurnCosts = maxTurnCosts;
        // https://wiki.openstreetmap.org/wiki/Key:access
        if (name.contains("car"))
            this.restrictions = Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access");
        else if (name.contains("motorbike") || name.contains("motorcycle"))
            this.restrictions = Arrays.asList("motorcycle", "motor_vehicle", "vehicle", "access");
        else if (name.contains("truck"))
            this.restrictions = Arrays.asList("hgv", "motor_vehicle", "vehicle", "access");
        else if (name.contains("bike") || name.contains("bicycle"))
            this.restrictions = Arrays.asList("bicycle", "vehicle", "access");
        else
            // assume default is some motor_vehicle, exception is too strict
            this.restrictions = Arrays.asList("motor_vehicle", "vehicle", "access");
    }

    DecimalEncodedValue getTurnCostEnc() {
        if (turnCostEnc == null)
            throw new IllegalStateException("Cannot access turn cost encoded value. Not initialized. Call createTurnCostEncodedValues before");
        return turnCostEnc;
    }

    @Override
    public void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        String accessKey = getKey(name, "access");
        accessEnc = lookup.getEncodedValue(accessKey, BooleanEncodedValue.class);
        registerNewEncodedValue.add(turnCostEnc = TurnCost.create(name, maxTurnCosts));
    }

    @Override
    public void handleTurnRelationTags(IntsRef turnCostFlags, OSMTurnRestriction turnRelation, ExternalInternalMap map, Graph graph) {
        if (!turnRelation.isVehicleTypeConcernedByTurnRestriction(restrictions))
            return;

        addRelationToTCStorage(turnRelation, turnCostFlags, map, graph);
    }

    private EdgeExplorer getInExplorer(Graph graph) {
        return cachedInExplorer == null ? cachedInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc)) : cachedInExplorer;
    }

    private EdgeExplorer getOutExplorer(Graph graph) {
        return cachedOutExplorer == null ? cachedOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc)) : cachedOutExplorer;
    }

    void addRelationToTCStorage(OSMTurnRestriction osmTurnRestriction, IntsRef turnCostFlags,
                                ExternalInternalMap map, Graph graph) {
        TurnCostStorage tcs = graph.getTurnCostStorage();
        int viaNode = map.getInternalNodeIdOfOsmNode(osmTurnRestriction.getViaOsmNodeId());
        EdgeExplorer edgeOutExplorer = getOutExplorer(graph), edgeInExplorer = getInExplorer(graph);

        try {
            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by fromOsm
            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNode);

            while (iter.next()) {
                if (map.getOsmIdOfEdge(iter.getEdge()) == osmTurnRestriction.getOsmIdFrom()) {
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
                long wayId = map.getOsmIdOfEdge(edgeId);
                OSMTurnRestriction.Type restrictionType = osmTurnRestriction.getRestrictionType();
                if (edgeId != edgeIdFrom && restrictionType == OSMTurnRestriction.Type.ONLY && wayId != osmTurnRestriction.getOsmIdTo()
                        || restrictionType == OSMTurnRestriction.Type.NOT && wayId == osmTurnRestriction.getOsmIdTo() && wayId >= 0) {
                    tcs.set(turnCostEnc, turnCostFlags, edgeIdFrom, viaNode, iter.getEdge(), Double.POSITIVE_INFINITY);
                    if (restrictionType == OSMTurnRestriction.Type.NOT)
                        break;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not build turn table entry for relation of node with osmId:" + osmTurnRestriction.getViaOsmNodeId(), e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
