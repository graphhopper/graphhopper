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
 * Parses the hiking difficulty. Where hiking corresponds to 1, mountain_hiking to 2 until 6.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:sac_scale">Key:sac_scale</a> for details on OSM hiking difficulties.
 */
class OSMHikeRatingParser(private val sacScaleEnc: IntEncodedValue) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val scale = way.getTag("sac_scale")
        val rating = when (scale) {
            "hiking" -> 1
            "mountain_hiking" -> 2
            "demanding_mountain_hiking" -> 3
            "alpine_hiking" -> 4
            "demanding_alpine_hiking" -> 5
            "difficult_alpine_hiking" -> 6
            else -> 0
        }
        if (rating != 0)
            sacScaleEnc.setInt(false, edgeId, edgeIntAccess, rating)
    }
}
