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
import com.graphhopper.reader.osm.conditional.DateRangeParser
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.Helper
import java.text.ParseException
import java.util.Date
import java.util.regex.Pattern

/**
 * This parser fills the different XYTemporalAccess enums from the OSM conditional
 * restrictions based on the specified dateRangeParserDate. 'Temporal' means that both, temporary
 * and seasonal restrictions will be considered. Node tags will be ignored for now.
 */
class OSMTemporalAccessParser(
    private val conditionals: Collection<String>,
    private val restrictionSetter: Setter,
    dateRangeParserDate: String
) : TagParser {

    private val parser: DateRangeParser

    fun interface Setter {
        fun setBoolean(edgeId: Int, edgeIntAccess: EdgeIntAccess, b: Boolean)
    }

    init {
        val date = dateRangeParserDate.ifEmpty {
            Helper.createFormatter("yyyy-MM-dd").format(Date().time)
        }
        this.parser = DateRangeParser.createInstance(date)
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        // TODO for now the node tag overhead is not worth the effort due to very few data points
        // List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);

        val b = getTemporaryAccess(way.getTags())
        if (b != null)
            restrictionSetter.setBoolean(edgeId, edgeIntAccess, b)
    }

    fun getTemporaryAccess(tags: Map<String, Any>): Boolean? {
        for ((key, tagValue) in tags) {
            if (!conditionals.contains(key)) continue

            val value = tagValue as String
            val strs = value.split("@").dropLastWhile { it.isEmpty() }
            if (strs.size == 2) {
                val inRange = isInRange(parser, strs[1].trim())
                if (inRange != null) {
                    if (strs[0].trim() == "no") return !inRange
                    if (strs[0].trim() == "yes") return inRange
                }
            }
        }
        return null
    }

    companion object {
        private fun isInRange(parser: DateRangeParser, value: String): Boolean? {
            if (value.isEmpty())
                return null

            if (value.contains(";"))
                return null

            val conditionalValue = value.replace('(', ' ').replace(')', ' ').trim()
            try {
                val res = parser.checkCondition(conditionalValue)
                if (res.isValid)
                    return res.isCheckPassed
            } catch (ex: ParseException) {
            }
            return null
        }

        /**
         * This method checks the conditional restrictions starting from firstIndex and returns
         * true if the access value is in the "accepted" collection AND the conditional value describes
         * a time (e.g. date, time or interval).
         */
        @JvmStatic
        fun hasPermissiveTemporalRestriction(
            way: ReaderWay, firstIndex: Int,
            restrictionKeys: List<String>, accepted: Collection<String>
        ): Boolean {
            for (i in firstIndex downTo 0) {
                val value = way.getTag(restrictionKeys[i] + ":conditional")
                if (acceptedAndInRange(value, accepted)) return true
            }
            return false
        }

        private fun acceptedAndInRange(value: String?, accepted: Collection<String>): Boolean {
            if (value == null) return false
            val strs = value.split("@").dropLastWhile { it.isEmpty() }
            if (strs.size == 2)
                return accepted.contains(strs[0].trim()) && hasTemporalSpec(strs[1])
            return false
        }

        // An OSM conditional is temporal if it mentions any of: a time-of-day range, an OSM
        // day-of-week abbreviation, or an OSM month abbreviation. We don't care about structure
        // (how many rules, in what order) — one recognizable token anywhere is enough to say
        // "there is some time at which this applies."
        private val TEMPORAL_TOKEN: Pattern = Pattern.compile(
            "\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}"                        // time range
                    + "|\\b(?:Mo|Tu|We|Th|Fr|Sa|Su)\\b"                      // day-of-week
                    + "|\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b" // month
        )

        /**
         * Returns true if the given string (the part after '@' in a conditional tag) mentions a
         * time-of-day range, an OSM day-of-week abbreviation, or an OSM month abbreviation.
         */
        @JvmStatic
        fun hasTemporalSpec(conditionalPart: String): Boolean {
            return TEMPORAL_TOKEN.matcher(conditionalPart).find()
        }
    }
}
