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
package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntsRefEdgeIntAccess
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.util.EncodingManager.Companion.getKey
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.Helper

class OSMBikeNetworkTagParser(
    private val bikeRouteEnc: EnumEncodedValue<RouteNetwork>,
    relConfig: EncodedValue.InitializerConfig,
    private val routeValue: String
) : RelationTagParser {

    // used only for class internal transformation from relations into edge flags
    val transformerRouteRelEnc: EnumEncodedValue<RouteNetwork> =
        EnumEncodedValue(getKey(routeValue, "route_relation"), RouteNetwork::class.java)

    init {
        transformerRouteRelEnc.init(relConfig)
    }

    override fun handleRelationTags(relFlags: IntsRef, relation: ReaderRelation) {
        val relIntAccess = IntsRefEdgeIntAccess(relFlags)
        val oldBikeNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess)
        if (relation.hasTag("route", routeValue)) {
            val tag = Helper.toLowerCase(relation.getTag("network", ""))
            val newBikeNetwork = BikeNetworkParserHelper.determine(tag)
            if (oldBikeNetwork == RouteNetwork.MISSING || oldBikeNetwork.ordinal > newBikeNetwork.ordinal)
                transformerRouteRelEnc.setEnum(false, -1, relIntAccess, newBikeNetwork)
        }
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        // just copy value into different bit range
        val relIntAccess = IntsRefEdgeIntAccess(relationFlags!!)
        var routeNetwork = transformerRouteRelEnc.getEnum(false, -1, relIntAccess)
        // if lcn=yes is mapped in OSM way consider this as route=bicycle
        if (routeValue == "bicycle" && routeNetwork == RouteNetwork.MISSING && way.hasTag("lcn", "yes"))
            routeNetwork = RouteNetwork.LOCAL
        bikeRouteEnc.setEnum(false, edgeId, edgeIntAccess, routeNetwork)
    }
}
