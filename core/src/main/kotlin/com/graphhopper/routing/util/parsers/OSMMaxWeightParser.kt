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
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor
import com.graphhopper.storage.IntsRef

class OSMMaxWeightParser(private val weightEncoder: DecimalEncodedValue) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        OSMValueExtractor.extractTons(edgeId, edgeIntAccess, way, weightEncoder, MAX_WEIGHT_TAGS)

        // vehicle:conditional = no @ (weight > 7.5)
        for (restriction in HGV_RESTRICTIONS) {
            val value = way.getTag(restriction, "")
            if (value.startsWith("no") && value.indexOf("@") < 6) { // no,none[ ]@
                val dec = OSMValueExtractor.conditionalWeightToTons(value)
                if (!dec.isNaN()) weightEncoder.setDecimal(false, edgeId, edgeIntAccess, dec)
            }
        }
    }

    companion object {
        // do not include OSM tag "height" here as it has completely different meaning (height of peak)
        private val MAX_WEIGHT_TAGS = listOf("maxweight", "maxweightrating", "maxweightrating:hgv", "maxgcweight"/*abandoned*/)
        private val HGV_RESTRICTIONS = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HGV)
            .map { it + ":conditional" }
    }
}
