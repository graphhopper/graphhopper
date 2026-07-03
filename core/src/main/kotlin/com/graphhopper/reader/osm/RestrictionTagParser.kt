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

package com.graphhopper.reader.osm

import com.graphhopper.routing.ev.BooleanEncodedValue
import java.util.Collections
import java.util.regex.Pattern

/**
 * Parses the OSM restriction tags for given vehicle types / transportation modes.
 */
class RestrictionTagParser(
    private val vehicleTypes: List<String>,
    // may be null in tests that only exercise the tag parsing
    val turnRestrictionEnc: BooleanEncodedValue?
) {

    @Throws(OSMRestrictionException::class)
    fun parseRestrictionTags(tags: Map<String, Any>): Result? {
        val restriction = tags["restriction"] as String?
        val limitedRestrictions = tags.entries
            .filter { it.key.startsWith("restriction:") }
            // restriction:bicycle=give_way seems quite common in France, but since it isn't a 'strict' turn
            // restriction we ignore it here.
            .filter { it.value != "give_way" }
            .map { it.key }
        val hasGiveWay = tags.values.any { it == "give_way" }
        val exceptVehicles = if (tags.containsKey("except"))
            // todo: there are also some occurrences of except=resident(s), destination or delivery
            // Pattern.split == Java's String.split (drops trailing empty strings, unlike Kotlin's split)
            Pattern.compile(";").split(tags["except"] as String).map { it.trim() }
        else
            emptyList()
        if (restriction != null) {
            // the 'restriction' tag limits the turns for all vehicleTypes, unless this is modified by the 'except' tag
            if (limitedRestrictions.isNotEmpty())
                // note that there is no warning if there is a restriction tag and restriction:*=give_way
                throw OSMRestrictionException("has a 'restriction' tag, but also 'restriction:' tags")
            if (!Collections.disjoint(vehicleTypes, exceptVehicles))
                return null
            return buildResult(restriction)
        } else {
            // if there is no 'restriction' tag there still might be 'restriction:xyz' tags that only affect certain vehicleTypes
            if (limitedRestrictions.isEmpty())
                if (!hasGiveWay)
                    throw OSMRestrictionException("neither has a 'restriction' nor 'restriction:' tags")
                else
                    // ignore, but no warning if there is only restriction:*=give_way
                    throw OSMRestrictionException.withoutWarning()
            if (exceptVehicles.isNotEmpty() && limitedRestrictions.none { it.startsWith("restriction:conditional") })
                throw OSMRestrictionException("has an 'except', but no 'restriction' or 'restriction:conditional' tag")
            // use a HashSet like Java's Collectors.toSet() did, so the exception message below is unchanged
            val restrictions: Set<String> = limitedRestrictions
                // We do not consider the restriction[:<transportation_mode>]:conditional tag so far
                .filter { !it.contains("conditional") }
                .filter { vehicleTypes.contains(it.replace("restriction:", "").trim()) }
                .mapTo(HashSet()) { tags[it] as String }
            if (restrictions.size > 1)
                throw OSMRestrictionException("contains multiple different restriction values: '$restrictions'")
            else if (restrictions.isEmpty())
                return null
            else
                return buildResult(restrictions.iterator().next())
        }
    }

    class Result(val restrictionType: RestrictionType, val restriction: String)

    companion object {
        @Throws(OSMRestrictionException::class)
        private fun buildResult(restriction: String): Result =
            Result(parseRestrictionValue(restriction), restriction)

        @Throws(OSMRestrictionException::class)
        private fun parseRestrictionValue(restriction: String): RestrictionType =
            when (restriction) {
                "no_left_turn",
                "no_right_turn",
                "no_straight_on",
                "no_u_turn",
                "no_entry",
                "no_exit" -> RestrictionType.NO

                "only_left_turn",
                "only_right_turn",
                "only_straight_on",
                "only_u_turn" -> RestrictionType.ONLY

                "no_right_turn_on_red",
                "no_left_turn_on_red" -> throw OSMRestrictionException.withoutWarning()

                else -> throw OSMRestrictionException("uses unknown restriction value: '$restriction'")
            }
    }
}
