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
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * This parser takes the turn restrictions from OSM and creates the appropriated infinite turn costs.
 */
public class OSMTurnRelationParser implements TurnCostParser {
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
    public OSMTurnRelationParser(String name, int maxTurnCosts) {
        this(name, maxTurnCosts, Collections.<String>emptyList());
    }

    public OSMTurnRelationParser(String name, int maxTurnCosts, Collection<String> restrictions) {
        this.name = name;
        this.maxTurnCosts = maxTurnCosts;
        if (restrictions.isEmpty()) {
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
                throw new IllegalArgumentException("restrictions collection must be specified for parser " + name
                        + ", e.g. [\"motorcar\", \"motor_vehicle\", \"vehicle\", \"access\"]");
        } else {
            this.restrictions = restrictions;
        }
    }

    DecimalEncodedValue getTurnCostEnc() {
        if (turnCostEnc == null)
            throw new IllegalStateException("Cannot access turn cost encoded value. Not initialized. Call createTurnCostEncodedValues before");
        return turnCostEnc;
    }

    @Override
    public void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        String accessKey = getKey(name, "access");
        if (!lookup.hasEncodedValue(accessKey))
            throw new IllegalArgumentException("Add TurnCostParsers to EncodingManager after everything else");
        accessEnc = lookup.getEncodedValue(accessKey, BooleanEncodedValue.class);
        registerNewEncodedValue.add(turnCostEnc = TurnCost.create(name, maxTurnCosts));
    }

    @Override
    public void handleTurnRelationTags(OSMTurnRelation turnRelation, IntsRef turnCostFlags, ExternalInternalMap map, Graph graph) {
        if (!turnRelation.isVehicleTypeConcernedByTurnRestriction(restrictions))
            return;

        getRestrictionAsEntries(turnRelation, turnCostFlags, map, graph);
    }

    private EdgeExplorer getInExplorer(Graph graph) {
        return cachedInExplorer == null ? cachedInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc)) : cachedInExplorer;
    }

    EdgeExplorer getOutExplorer(Graph graph) {
        return cachedOutExplorer == null ? cachedOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc)) : cachedOutExplorer;
    }

    /**
     * Transforms this relation into a collection of turn cost entries
     *
     * @return a collection of node cost entries which can be added to the graph later
     */
    Collection<TCEntry> getRestrictionAsEntries(OSMTurnRelation osmTurnRelation, IntsRef turnCostFlags,
                                                ExternalInternalMap map, Graph graph) {
        TurnCostExtension tcs = graph.getTurnCostExtension();
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
                return Collections.emptyList();

            final Collection<TCEntry> entries = new ArrayList<>();
            // get all outgoing edges of the via node
            iter = edgeOutExplorer.setBaseNode(viaNode);
            // for TYPE_ONLY_* we add ALL restrictions (from, via, * ) EXCEPT the given turn
            // for TYPE_NOT_*  we add ONE restriction  (from, via, to)
            while (iter.next()) {
                int edgeId = iter.getEdge();
                long wayId = map.getOsmIdOfInternalEdge(edgeId);
                if (edgeId != edgeIdFrom && osmTurnRelation.getRestriction() == OSMTurnRelation.Type.ONLY && wayId != osmTurnRelation.getOsmIdTo()
                        || osmTurnRelation.getRestriction() == OSMTurnRelation.Type.NOT && wayId == osmTurnRelation.getOsmIdTo() && wayId >= 0) {
                    final TCEntry entry = new TCEntry(new IntsRef(turnCostFlags.length), edgeIdFrom, viaNode, iter.getEdge());
                    getTurnCostEnc().setDecimal(false, entry.flags, Double.POSITIVE_INFINITY);
                    entries.add(entry);
                    tcs.addTurnCost(entry.flags, edgeIdFrom, viaNode, iter.getEdge());
                    if (osmTurnRelation.getRestriction() == OSMTurnRelation.Type.NOT)
                        break;
                }
            }
            return entries;
        } catch (Exception e) {
            throw new IllegalStateException("Could not built turn table entry for relation of node with osmId:" + osmTurnRelation.getViaOsmNodeId(), e);
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

    /**
     * Helper class to processing purposes. We could remove it if TurnCostExtension is similarly fast with merging
     * existing turn cost relations.
     */
    static class TCEntry {
        final int edgeFrom;
        final int nodeVia;
        final int edgeTo;
        public final IntsRef flags;

        TCEntry(IntsRef flags, int edgeFrom, int nodeVia, int edgeTo) {
            this.edgeFrom = edgeFrom;
            this.nodeVia = nodeVia;
            this.edgeTo = edgeTo;
            this.flags = flags;
        }

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         * are involved.
         */
        public long getItemId() {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
        }
    }
}
