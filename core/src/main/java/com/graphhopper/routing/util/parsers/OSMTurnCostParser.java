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
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * The TurnCostParser requires a fully established topology of the graph and existing.
 * This makes it different to the RelationTagParser that can even convert its tags to way tags.
 */
public class OSMTurnCostParser implements TurnCostParser {
    private String name;
    private DecimalEncodedValue turnCostEnc;
    private EdgeExplorer edgeInExplorer;
    private EdgeExplorer edgeOutExplorer;
    private final int maxTurnCosts;
    private final Collection<String> restrictions;
    private BooleanEncodedValue accessEnc;
    // TODO NOW separate the EncodedValue creation, see RouteNetwork for a similar case
    /**
     * You need to call EncodingManager.getKey(prefix, EV_SUFFIX) as this EncodedValue can be used for e.g. car and bike
     */
    public final static String EV_SUFFIX = "turn_cost";

    /**
     * @param maxTurnCosts specify the maximum value used for turn costs, if this value is reached a
     *                     turn is forbidden and results in costs of positive infinity.
     */
    public OSMTurnCostParser(String name, int maxTurnCosts) {
        this(name, maxTurnCosts, Collections.<String>emptyList());
    }

    public OSMTurnCostParser(String name, int maxTurnCosts, Collection<String> restrictions) {
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
                throw new IllegalArgumentException("restrictions collection must be specified for parser " + name + ", e.g. [\"motorcar\", \"motor_vehicle\", \"vehicle\", \"access\"]");
        } else {
            this.restrictions = restrictions;
        }
    }

    @Override
    public boolean acceptsTurnRelation(OSMTurnRelation relation) {
        return relation.isVehicleTypeConcernedByTurnRestriction(restrictions);
    }

    @Override
    public EdgeExplorer createEdgeOutExplorer(Graph graph) {
        if (edgeOutExplorer == null)
            edgeOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc));
        return edgeOutExplorer;
    }

    @Override
    public EdgeExplorer createEdgeInExplorer(Graph graph) {
        if (edgeInExplorer == null)
            edgeInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc));
        return edgeInExplorer;
    }

    @Override
    public DecimalEncodedValue getTurnCostEnc() {
        if (turnCostEnc == null)
            throw new IllegalStateException("Cannot access turn cost encoded value. Not initialized. Call createRelationEncodedValues before");
        return turnCostEnc;
    }

    @Override
    public void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        String accessKey = getKey(name, "access");
        if (!lookup.hasEncodedValue(accessKey))
            throw new IllegalArgumentException("Add TurnCostParsers to EncodingManager after everything else");
        accessEnc = lookup.getEncodedValue(accessKey, BooleanEncodedValue.class);

        int turnBits = Helper.countBitValue(maxTurnCosts);
        registerNewEncodedValue.add(turnCostEnc = new UnsignedDecimalEncodedValue(getKey(name, EV_SUFFIX), turnBits, 1, 0, false, true));
    }

    @Override
    public void create(Graph graph) {
        // TODO NOW here the code from turn cost related parsing from OSMReader should end up
    }

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry {
        public final int edgeFrom;
        public final int nodeVia;
        public final int edgeTo;
        public final IntsRef flags;

        public TurnCostTableEntry(IntsRef flags, int edgeFrom, int nodeVia, int edgeTo) {
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

        public void mergeFlags(TurnCostTableEntry tce) {
            flags.ints[0] |= tce.flags.ints[0];
        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
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
