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
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.Toll
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.Helper

class OSMTollParser(private val tollEnc: EnumEncodedValue<Toll>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        var toll = parseToll(way)

        if (toll == Toll.MISSING) {
            val country = way.getTag("country", Country.MISSING)
            toll = getCountryDefault(country, way)
        }

        val tollFwd = parseDirectionalToll(way.getTag("toll:forward"), toll)
        val tollBwd = parseDirectionalToll(way.getTag("toll:backward"), toll)

        tollEnc.setEnum(false, edgeId, edgeIntAccess, tollFwd)
        tollEnc.setEnum(true, edgeId, edgeIntAccess, tollBwd)
    }

    private fun getCountryDefault(country: Country, way: ReaderWay): Toll {
        return when (country) {
            Country.ROU, Country.SVK, Country.SVN -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK)
                    Toll.ALL
                else
                    Toll.NO
            }
            Country.CHE -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK)
                    Toll.ALL
                else
                    // 'Schwerlastabgabe' for the entire road network
                    Toll.HGV
            }
            Country.LIE ->
                // 'Schwerlastabgabe' for the entire road network
                Toll.HGV
            Country.HUN -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY)
                    Toll.ALL
                else if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.PRIMARY)
                    Toll.HGV
                else
                    Toll.NO
            }
            Country.DEU, Country.DNK, Country.EST, Country.LTU, Country.LVA -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK || roadClass == RoadClass.PRIMARY)
                    Toll.HGV
                else
                    Toll.NO
            }
            Country.BEL, Country.BLR, Country.LUX, Country.NLD, Country.POL, Country.SWE -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY)
                    Toll.HGV
                else
                    Toll.NO
            }
            Country.BGR, Country.CZE, Country.FRA, Country.GRC, Country.HRV, Country.ITA, Country.PRT, Country.SRB, Country.ESP -> {
                val roadClass = RoadClass.find(way.getTag("highway", ""))
                if (roadClass == RoadClass.MOTORWAY)
                    Toll.ALL
                else
                    Toll.NO
            }
            else -> Toll.NO
        }
    }

    companion object {
        private val HGV_TAGS = listOf("toll:hgv", "toll:N2", "toll:N3")

        private fun parseDirectionalToll(value: String?, defaultToll: Toll): Toll {
            if (value == null) return defaultToll
            if ("yes" == value) return Toll.ALL
            if ("no" == value) return Toll.NO
            // e.g. toll:forward=hgv
            return try {
                Toll.valueOf(Helper.toUpperCase(value))
            } catch (e: IllegalArgumentException) {
                defaultToll
            }
        }

        private fun parseToll(way: ReaderWay): Toll {
            return if (way.hasTag("toll", "yes"))
                Toll.ALL
            else if (way.hasTag(HGV_TAGS, "yes"))
                Toll.HGV
            else if (way.hasTag("toll", "no"))
                Toll.NO
            else
                Toll.MISSING
        }
    }
}
