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
package com.graphhopper.routing.util

import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderWay
import com.graphhopper.reader.osm.RestrictionTagParser
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.util.parsers.RelationTagParser
import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.storage.IntsRef
import java.util.function.Function

class OSMParsers(
    val ignoredHighways: MutableList<String>,
    val wayTagParsers: MutableList<TagParser>,
    val relationTagParsers: MutableList<RelationTagParser>,
    val restrictionTagParsers: MutableList<RestrictionTagParser>
) {
    private val relConfig = EncodedValue.InitializerConfig()

    constructor() : this(ArrayList(), ArrayList(), ArrayList(), ArrayList())

    fun addIgnoredHighway(highway: String): OSMParsers {
        ignoredHighways.add(highway)
        return this
    }

    fun addWayTagParser(tagParser: TagParser): OSMParsers {
        wayTagParsers.add(tagParser)
        return this
    }

    fun addRelationTagParser(createRelationTagParser: Function<EncodedValue.InitializerConfig, RelationTagParser>): OSMParsers {
        relationTagParsers.add(createRelationTagParser.apply(relConfig))
        return this
    }

    fun addRestrictionTagParser(restrictionTagParser: RestrictionTagParser): OSMParsers {
        restrictionTagParsers.add(restrictionTagParser)
        return this
    }

    fun acceptWay(way: ReaderWay): Boolean {
        val highway = way.getTag("highway")
        return if (highway != null)
            !ignoredHighways.contains(highway)
        else if (way.getTag("route") != null)
            // we accept *all* ways with a 'route' tag and no 'highway' tag, because most of them are ferries
            // (route=ferry), which we want, and there aren't so many such ways we do not want
            // https://github.com/graphhopper/graphhopper/pull/2702#discussion_r1038093050
            true
        else if ("pier" == way.getTag("man_made"))
            true
        else "platform" == way.getTag("railway")
    }

    fun handleRelationTags(relation: ReaderRelation, relFlags: IntsRef): IntsRef {
        for (relParser in relationTagParsers) {
            relParser.handleRelationTags(relFlags, relation)
        }
        return relFlags
    }

    fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef) {
        for (relParser in relationTagParsers)
            relParser.handleWayTags(edgeId, edgeIntAccess, way, relationFlags)
        for (parser in wayTagParsers)
            parser.handleWayTags(edgeId, edgeIntAccess, way, relationFlags)
    }

    fun createRelationFlags(): IntsRef {
        val requiredInts = relConfig.requiredInts
        if (requiredInts > 2)
            throw IllegalStateException("More than two ints are needed for relation flags, but OSMReader does not allow this")
        return IntsRef(2)
    }
}
