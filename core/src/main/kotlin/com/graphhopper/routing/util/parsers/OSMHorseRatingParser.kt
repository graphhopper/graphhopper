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

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.storage.IntsRef

/**
 * Parses the horseback riding difficulty. Where common is mapped to 1, demanding to 2 until 6
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:horse_scale">Key:horse_scale</a> for details on horseback riding difficulties.
 */
class OSMHorseRatingParser(private val horseScale: IntEncodedValue) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val scale = way.getTag("horse_scale")
        val rating = when (scale) {
            "common" -> 1
            "demanding" -> 2
            "difficult" -> 3
            "critical" -> 4
            "dangerous" -> 5
            "impossible" -> 6
            else -> 0
        }
        if (rating != 0)
            horseScale.setInt(false, edgeId, edgeIntAccess, rating)
    }
}
