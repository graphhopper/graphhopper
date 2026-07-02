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
import com.graphhopper.routing.ev.Cycleway
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.storage.IntsRef

/**
 * Parses the cycleway type from cycleway, cycleway:left, cycleway:right and cycleway:both OSM tags.
 * Stores values per direction: cycleway:right maps to forward, cycleway:left maps to reverse,
 * cycleway:both and cycleway (without direction qualifier) map to both directions.
 * The deprecated opposite_lane and opposite_track values are stored as LANE/TRACK in the reverse direction.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:cycleway">Key:cycleway</a>
 */
class OSMCyclewayParser(private val cyclewayEnc: EnumEncodedValue<Cycleway>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        var bestFwd = Cycleway.MISSING
        var bestRev = Cycleway.MISSING

        val both = Cycleway.find(way.getTag("cycleway:both"))
        if (both != Cycleway.MISSING) {
            bestFwd = both
            bestRev = both
        }

        val right = Cycleway.find(way.getTag("cycleway:right"))
        if (right != Cycleway.MISSING)
            bestFwd = better(bestFwd, right)

        val left = Cycleway.find(way.getTag("cycleway:left"))
        if (left != Cycleway.MISSING)
            bestRev = better(bestRev, left)

        // cycleway (generic) used for both directions, unless opposite_* which maps to reverse only
        val generic = way.getTag("cycleway")
        if (generic != null) {
            if (generic.startsWith("opposite_")) {
                val c = Cycleway.find(generic.substring("opposite_".length))
                if (c != Cycleway.MISSING)
                    bestRev = better(bestRev, c)
            } else {
                val c = Cycleway.find(generic)
                if (c != Cycleway.MISSING) {
                    bestFwd = better(bestFwd, c)
                    bestRev = better(bestRev, c)
                }
            }
        }

        cyclewayEnc.setEnum(false, edgeId, edgeIntAccess, bestFwd)
        cyclewayEnc.setEnum(true, edgeId, edgeIntAccess, bestRev)
    }

    companion object {
        private fun better(current: Cycleway, candidate: Cycleway): Cycleway {
            if (current == Cycleway.MISSING || candidate.ordinal < current.ordinal)
                return candidate
            return current
        }
    }
}
