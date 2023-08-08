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

public class OSMVisually_impairedNetworkTagParser implements RelationTagParser {
    private final EnumEncodedValue<RouteNetwork> visually_impairedRouteEnc;
    // used for internal transformation from relations into visually_impairedRouteEnc
    private final EnumEncodedValue<RouteNetwork> transformerRouteRelEnc = new EnumEncodedValue<>(getKey("Visually_impaired", "route_relation"), RouteNetwork.class);

    public OSMVisually_impairedNetworkTagParser(EnumEncodedValue<RouteNetwork> visually_impairedRouteEnc, EncodedValue.InitializerConfig relConfig) {
        this.visually_impairedRouteEnc = visually_impairedRouteEnc;
        this.transformerRouteRelEnc.init(relConfig);
    }

    @Override
    public void handleRelationTags(IntsRef relFlags, ReaderRelation relation) {
        IntsRefEdgeIntAccess relIntAccess = new IntsRefEdgeIntAccess(relFlags);
        RouteNetwork oldVisually_impairedNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess);
        if (relation.hasTag("route", "hiking") || relation.hasTag("route", "Visually_impaired")) {
            String tag = Helper.toLowerCase(relation.getTag("network", ""));
            RouteNetwork newVisually_impairedNetwork = RouteNetwork.LOCAL;
            if ("lwn".equals(tag)) {
                newVisually_impairedNetwork = RouteNetwork.LOCAL;
            } else if ("rwn".equals(tag)) {
                newVisually_impairedNetwork = RouteNetwork.REGIONAL;
            } else if ("nwn".equals(tag)) {
                newVisually_impairedNetwork = RouteNetwork.NATIONAL;
            } else if ("iwn".equals(tag)) {
                newVisually_impairedNetwork = RouteNetwork.INTERNATIONAL;
            }
            if (oldVisually_impairedNetwork == RouteNetwork.MISSING || oldVisually_impairedNetwork.ordinal() > newVisually_impairedNetwork.ordinal())
                transformerRouteRelEnc.setEnum(false, -1, relIntAccess, newVisually_impairedNetwork);
        }
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // just copy value into different bit range
        IntsRefEdgeIntAccess relIntAccess = new IntsRefEdgeIntAccess(relationFlags);
        RouteNetwork Visually_impairedNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess);
        visually_impairedRouteEnc.setEnum(false, edgeId, edgeIntAccess, Visually_impairedNetwork);
    }
}
