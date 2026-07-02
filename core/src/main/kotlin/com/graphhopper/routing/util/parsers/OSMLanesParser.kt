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
 * https://wiki.openstreetmap.org/wiki/Key:lanes
 */
class OSMLanesParser(private val lanesEnc: IntEncodedValue) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        var laneCount = 1
        if (way.hasTag("lanes")) {
            val noLanes = way.getTag("lanes")
            // like Java's String.split: trailing empty strings are removed
            val noLanesTok = noLanes.split(";", ".").dropLastWhile { it.isEmpty() }
            if (noLanesTok.isNotEmpty()) {
                try {
                    val noLanesInt = noLanesTok[0].toInt()
                    // there was a proposal with negative lanes but I cannot find it
                    laneCount = if (noLanesInt < 0) 1
                    else if (noLanesInt > 6) 6
                    else noLanesInt
                } catch (ex: NumberFormatException) {
                    // ignore if no number
                }
            }
        }
        lanesEnc.setInt(false, edgeId, edgeIntAccess, laneCount)
    }
}
