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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class OSMBikeNetworkTagParser implements RelationTagParser {
    private EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    // used only for internal transformation from relations into edge flags
    private EnumEncodedValue<RouteNetwork> transformerRouteRelEnc;

    @Override
    public void createRelationEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(transformerRouteRelEnc = new EnumEncodedValue<>(getKey("bike", "route_relation"), RouteNetwork.class));
    }

    @Override
    public IntsRef handleRelationTags(IntsRef relFlags, ReaderRelation relation) {
        RouteNetwork oldBikeNetwork = transformerRouteRelEnc.getEnum(false, relFlags);
        RouteNetwork newBikeNetwork = RouteNetwork.MISSING;
        if (relation.hasTag("route", "bicycle")) {
            String tag = Helper.toLowerCase(relation.getTag("network", ""));
            if ("lcn".equals(tag)) {
                newBikeNetwork = RouteNetwork.LOCAL;
            } else if ("rcn".equals(tag)) {
                newBikeNetwork = RouteNetwork.REGIONAL;
            } else if ("ncn".equals(tag)) {
                newBikeNetwork = RouteNetwork.NATIONAL;
            } else if ("icn".equals(tag)) {
                newBikeNetwork = RouteNetwork.INTERNATIONAL;
            } else if ("deprecated".equals(tag)) {
                newBikeNetwork = RouteNetwork.DEPRECATED;
            } else {
                newBikeNetwork = RouteNetwork.LOCAL;
            }
        }
        if (relation.hasTag("route", "ferry")) {
            newBikeNetwork = RouteNetwork.FERRY;
        }
        if (relation.hasTag("route", "mtb")) { // for MTB profile
            newBikeNetwork = RouteNetwork.MTB;
        }
        if (oldBikeNetwork == RouteNetwork.MISSING || oldBikeNetwork.ordinal() > newBikeNetwork.ordinal())
            transformerRouteRelEnc.setEnum(false, relFlags, newBikeNetwork);
        return relFlags;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(bikeRouteEnc = new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class));
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        // just copy value into different bit range
        RouteNetwork routeNetwork = transformerRouteRelEnc.getEnum(false, relationFlags);
        bikeRouteEnc.setEnum(false, edgeFlags, routeNetwork);
        return edgeFlags;
    }
}
