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

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class OSMBikeNetworkTagParser implements RelationTagParser {
    private final EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    // used only for internal transformation from relations into edge flags
    private final EnumEncodedValue<RouteNetwork> transformerRouteRelEnc = new EnumEncodedValue<>(getKey("bike", "route_relation"), RouteNetwork.class);

    public OSMBikeNetworkTagParser(EnumEncodedValue<RouteNetwork> bikeRouteEnc, EncodedValue.InitializerConfig relConfig) {
        this.bikeRouteEnc = bikeRouteEnc;
        this.transformerRouteRelEnc.init(relConfig);
    }

    @Override
    public void handleRelationTags(IntsRef relFlags, ReaderRelation relation) {
        IntsRefEdgeIntAccess relIntAccess = new IntsRefEdgeIntAccess(relFlags);
        RouteNetwork oldBikeNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess);
        if (relation.hasTag("route", "bicycle")) {
            String tag = Helper.toLowerCase(relation.getTag("network", ""));
            RouteNetwork newBikeNetwork = RouteNetwork.LOCAL;
            if ("lcn".equals(tag)) {
                newBikeNetwork = RouteNetwork.LOCAL;
            } else if ("rcn".equals(tag)) {
                newBikeNetwork = RouteNetwork.REGIONAL;
            } else if ("ncn".equals(tag)) {
                newBikeNetwork = RouteNetwork.NATIONAL;
            } else if ("icn".equals(tag)) {
                newBikeNetwork = RouteNetwork.INTERNATIONAL;
            }
            if (oldBikeNetwork == RouteNetwork.MISSING || oldBikeNetwork.ordinal() > newBikeNetwork.ordinal())
                transformerRouteRelEnc.setEnum(false, -1, relIntAccess, newBikeNetwork);
        }
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // just copy value into different bit range
        IntsRefEdgeIntAccess relIntAccess = new IntsRefEdgeIntAccess(relationFlags);
        RouteNetwork routeNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess);
        bikeRouteEnc.setEnum(false, edgeId, edgeIntAccess, routeNetwork);
    }

    public EnumEncodedValue<RouteNetwork> getTransformerRouteRelEnc() {
        return transformerRouteRelEnc;
    }
}
