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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.RestrictionTagParser;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.parsers.RelationTagParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class OSMParsers {
    private final List<String> ignoredHighways;
    private List<String> acceptedRoutes;
    private final List<TagParser> wayTagParsers;
    private final List<VehicleTagParser> vehicleTagParsers;
    private final List<RelationTagParser> relationTagParsers;
    private final List<RestrictionTagParser> restrictionTagParsers;
    private final EncodedValue.InitializerConfig relConfig = new EncodedValue.InitializerConfig();

    public OSMParsers() {
        this(new ArrayList<>(), Arrays.asList("ferry", "shuttle_train"), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public OSMParsers(List<String> ignoredHighways, List<String> acceptedRoutes, List<TagParser> wayTagParsers, List<VehicleTagParser> vehicleTagParsers,
                      List<RelationTagParser> relationTagParsers, List<RestrictionTagParser> restrictionTagParsers) {
        this.ignoredHighways = ignoredHighways;
        this.acceptedRoutes = acceptedRoutes;
        this.wayTagParsers = wayTagParsers;
        this.vehicleTagParsers = vehicleTagParsers;
        this.relationTagParsers = relationTagParsers;
        this.restrictionTagParsers = restrictionTagParsers;
    }

    public OSMParsers addIgnoredHighway(String highway) {
        ignoredHighways.add(highway);
        return this;
    }

    public OSMParsers setAcceptedRoutes(List<String> acceptedRoutes) {
        this.acceptedRoutes = acceptedRoutes;
        return this;
    }

    public OSMParsers addWayTagParser(TagParser tagParser) {
        wayTagParsers.add(tagParser);
        return this;
    }

    public OSMParsers addVehicleTagParser(VehicleTagParser vehicleTagParser) {
        vehicleTagParsers.add(vehicleTagParser);
        if (vehicleTagParser.supportsTurnCosts()) {
            restrictionTagParsers.add(new RestrictionTagParser(vehicleTagParser.getRestrictions(), vehicleTagParser.getTurnCostEnc()));
        }
        return this;
    }

    public OSMParsers addRelationTagParser(Function<EncodedValue.InitializerConfig, RelationTagParser> createRelationTagParser) {
        relationTagParsers.add(createRelationTagParser.apply(relConfig));
        return this;
    }

    public OSMParsers addRestrictionTagParser(RestrictionTagParser restrictionTagParser) {
        restrictionTagParsers.add(restrictionTagParser);
        return this;
    }

    public boolean acceptWay(ReaderWay way) {
        String highway = way.getTag("highway");
        if (highway != null)
            return !ignoredHighways.contains(highway);
        else {
            String route = way.getTag("route");
            return route != null && acceptedRoutes.contains(route);
        }
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

    public IntsRef createRelationFlags() {
        int requiredInts = relConfig.getRequiredInts();
        if (requiredInts > 2)
            throw new IllegalStateException("More than two ints are needed for relation flags, but OSMReader does not allow this");
        return new IntsRef(2);
    }

    public List<String> getIgnoredHighways() {
        return ignoredHighways;
    }

    public List<VehicleTagParser> getVehicleTagParsers() {
        return vehicleTagParsers;
    }

    public List<RestrictionTagParser> getRestrictionTagParsers() {
        return restrictionTagParsers;
    }
}
