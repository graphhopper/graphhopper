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

package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.parsers.OSMTurnRelationParser;
import com.graphhopper.routing.util.parsers.RelationTagParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.util.parsers.TurnCostParser;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OSMParsers {
    private final List<TagParser> wayTagParsers;
    private final List<VehicleTagParser> vehicleTagParsers;
    private final List<RelationTagParser> relationTagParsers;
    private final List<TurnCostParser> turnCostParsers;
    private final EncodedValue.InitializerConfig relConfig = new EncodedValue.InitializerConfig();

    public OSMParsers() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public OSMParsers(List<TagParser> wayTagParsers, List<VehicleTagParser> vehicleTagParsers, List<RelationTagParser> relationTagParsers, List<TurnCostParser> turnCostParsers) {
        this.wayTagParsers = wayTagParsers;
        this.vehicleTagParsers = vehicleTagParsers;
        this.relationTagParsers = relationTagParsers;
        this.turnCostParsers = turnCostParsers;
    }

    public OSMParsers addWayTagParser(TagParser tagParser) {
        wayTagParsers.add(tagParser);
        return this;
    }

    public OSMParsers addVehicleTagParser(VehicleTagParser vehicleTagParser) {
        vehicleTagParsers.add(vehicleTagParser);
        if (vehicleTagParser.supportsTurnCosts())
            turnCostParsers.add(new OSMTurnRelationParser(vehicleTagParser.getAccessEnc(), vehicleTagParser.getTurnCostEnc(), vehicleTagParser.getRestrictions()));
        return this;
    }

    public OSMParsers addRelationTagParser(Function<EncodedValue.InitializerConfig, RelationTagParser> createRelationTagParser) {
        relationTagParsers.add(createRelationTagParser.apply(relConfig));
        return this;
    }

    public OSMParsers addTurnCostTagParser(TurnCostParser turnCostParser) {
        turnCostParsers.add(turnCostParser);
        return this;
    }

    public boolean acceptWay(ReaderWay way) {
        return vehicleTagParsers.stream().anyMatch(v -> !v.getAccess(way).equals(WayAccess.CAN_SKIP));
    }

    public IntsRef handleRelationTags(ReaderRelation relation, IntsRef relFlags) {
        for (RelationTagParser relParser : relationTagParsers) {
            relParser.handleRelationTags(relFlags, relation);
        }
        return relFlags;
    }

    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        for (RelationTagParser relParser : relationTagParsers)
            relParser.handleWayTags(edgeFlags, way, relationFlags);
        for (TagParser parser : wayTagParsers)
            parser.handleWayTags(edgeFlags, way, relationFlags);
        for (VehicleTagParser vehicleTagParser : vehicleTagParsers)
            vehicleTagParser.handleWayTags(edgeFlags, way, relationFlags);
        return edgeFlags;
    }

    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        vehicleTagParsers.forEach(t -> t.applyWayTags(way, edge));
    }

    public void handleTurnRelationTags(OSMTurnRelation turnRelation, TurnCostParser.ExternalInternalMap map, Graph graph) {
        turnCostParsers.forEach(t -> t.handleTurnRelationTags(turnRelation, map, graph));
    }

    public IntsRef createRelationFlags() {
        int requiredInts = relConfig.getRequiredInts();
        if (requiredInts > 2)
            throw new IllegalStateException("More than two ints are needed for relation flags, but OSMReader does not allow this");
        return new IntsRef(2);
    }

    public List<VehicleTagParser> getVehicleTagParsers() {
        return vehicleTagParsers;
    }
}
