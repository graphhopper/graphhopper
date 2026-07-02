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

import com.graphhopper.apache.commons.lang3.StringUtils
import com.graphhopper.debatty.java.stringsimilarity.JaroWinkler
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.Helper.toLowerCase
import com.graphhopper.util.shapes.BBox
import com.graphhopper.util.shapes.Circle
import com.graphhopper.util.shapes.GHPoint
import java.util.regex.Pattern

/**
 * This class defines the basis for NameSimilarity matching using an EdgeFilter. It is not thread-safe.
 * The typical use-case is to match not the nearest edge in
 * [com.graphhopper.storage.index.LocationIndex.findClosest]
 * but the edge with the name that is similar to the specified pointHint and still close.
 *
 * Names that are similar to each other are (n1 name1, n2 name2):
 *  * n1 == n2
 *  * n1 is significant substring of n2, e.g: n1="Main Road", n2="Main Road, New York"
 *  * n1 and n2 contain a reasonable longest common substring, e.g.: n1="Cape Point / Cape of Good Hope",
 *    n2="Cape Point Rd, Cape Peninsula, Cape Town, 8001, Afrique du Sud"
 *
 * The aim is to allow minor typos/differences of the substrings, without having too much false positives.
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
class NameSimilarityEdgeFilter(
    private val edgeFilter: EdgeFilter,
    pointHint: String?,
    point: GHPoint,
    radius: Double,
    private val rewriteMap: Map<String, String>
) : EdgeFilter {

    private val pointHint: String = prepareName(removeRelation(pointHint ?: ""))
    private val pointCircle: Circle = Circle(point.lat, point.lon, radius)

    constructor(edgeFilter: EdgeFilter, pointHint: String?, point: GHPoint, radius: Double) :
            this(edgeFilter, pointHint, point, radius, DEFAULT_REWRITE_MAP)

    @JvmName("getNormalizedPointHint")
    internal fun getNormalizedPointHint(): String = pointHint

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     * TODO Currently limited to certain 'western' languages
     */
    private fun prepareName(name: String): String {
        val sb = StringBuilder(name.length)
        val wordCharMatcher = WORD_CHAR.matcher(name)
        while (wordCharMatcher.find()) {
            var normalizedToken = toLowerCase(wordCharMatcher.group())
            val rewrite = rewriteMap[normalizedToken]
            if (rewrite != null)
                normalizedToken = rewrite
            if (normalizedToken.isEmpty())
                continue
            // Ignore matching short phrases like de, la, ... except it is a number
            if (normalizedToken.length > 2) {
                sb.append(normalizedToken)
            } else {
                if (Character.isDigit(normalizedToken[0]) && (normalizedToken.length == 1 || Character.isDigit(normalizedToken[1]))) {
                    sb.append(normalizedToken)
                }
            }
        }
        return sb.toString()
    }

    private fun removeRelation(edgeName: String): String {
        val index = edgeName.lastIndexOf(", ")
        return if (index >= 0) edgeName.substring(0, index) else edgeName
    }

    override fun accept(edgeState: EdgeIteratorState): Boolean {
        if (!edgeFilter.accept(edgeState)) {
            return false
        }

        if (pointHint.isEmpty()) {
            return true
        }

        var name: String? = edgeState.name
        if (name == null || name.isEmpty()) {
            return false
        }

        val bbox = createBBox(edgeState)
        if (!pointCircle.intersects(bbox)) {
            return false
        }

        name = removeRelation(name)
        val edgeName = prepareName(name)

        return isJaroWinklerSimilar(pointHint, edgeName)
    }

    private fun isJaroWinklerSimilar(str1: String, str2: String): Boolean {
        val jwSimilarity = jaroWinkler.similarity(str1, str2)
        // System.out.println(str1 + " vs. edge:" + str2 + ", " + jwSimilarity);
        return jwSimilarity > JARO_WINKLER_ACCEPT_FACTOR
    }

    @Suppress("unused")
    private fun isLevenshteinSimilar(hint: String, name: String): Boolean {
        // too big length difference
        if (Math.min(name.length, hint.length) * 4 < Math.max(name.length, hint.length))
            return false

        // The part 'abs(pointHint.length - name.length)' tries to make differences regarding length less important
        // Ie. 'hauptstraßedresden' vs. 'hauptstr.' should be considered a match, but 'hauptstraßedresden' vs. 'klingestraßedresden' should not match
        val factor = 1 + Math.abs(hint.length - name.length)
        val levDistance = StringUtils.getLevenshteinDistance(hint, name)
        // System.out.println(hint + " vs. edge:" + name + ", " + levDistance + " <= " + factor);
        return levDistance <= factor
    }

    companion object {
        private val DEFAULT_REWRITE_MAP: Map<String, String> = HashMap<String, String>().apply {
            // Words with 2 characters like "Dr" (Drive) will be ignored, so it is not required to list them here.
            // Words with 3 and more characters should be listed here to remove or rename them.
            for (remove in listOf(
                "ally", "alley",
                "arc", "arcade",
                "bvd", "bvd.", "boulevard",
                "av.", "avenue", "avenida", "ave",
                "calle",
                "cl.", "close",
                "crescend", "cres", "cres.",
                "rd.", "road",
                "ln.", "lane",
                "pde.", "pde", "parade",
                "pl.", "place", "plaza",
                "rte", "route",
                "str.", "str", "straße", "strasse", "st.", "street", "strada",
                "sq.", "square",
                "tr.", "track",
                "via"
            )) {
                put(remove, "")
            }
            // expand instead of remove as significant part of the road name
            put("n", "north")
            put("s", "south")
            put("w", "west")
            put("e", "east")
            put("ne", "northeast")
            put("nw", "northwest")
            put("se", "southeast")
            put("sw", "southwest")
        }
        private val WORD_CHAR: Pattern = Pattern.compile("\\p{LD}+")
        private val jaroWinkler = JaroWinkler()
        private const val JARO_WINKLER_ACCEPT_FACTOR = .9

        private fun createBBox(edgeState: EdgeIteratorState): BBox {
            // we should include the entire geometry, see #2319
            val geometry = edgeState.fetchWayGeometry(FetchMode.ALL)
            val bbox = BBox(180.0, -180.0, 90.0, -90.0)
            for (i in 0 until geometry.size())
                bbox.update(geometry.getLat(i), geometry.getLon(i))
            return bbox
        }
    }
}
