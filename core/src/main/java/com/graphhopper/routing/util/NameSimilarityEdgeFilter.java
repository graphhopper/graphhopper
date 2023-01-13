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
package com.graphhopper.routing.util;

import com.graphhopper.apache.commons.lang3.StringUtils;
import com.graphhopper.debatty.java.stringsimilarity.JaroWinkler;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.core.util.shapes.BBox;
import com.graphhopper.core.util.shapes.Circle;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.graphhopper.util.Helper.toLowerCase;

/**
 * This class defines the basis for NameSimilarity matching using an EdgeFilter. It is not thread-safe.
 * The typical use-case is to match not the nearest edge in
 * {@link com.graphhopper.storage.index.LocationIndex#findClosest(double, double, EdgeFilter)}
 * but the edge with the name that is similar to the specified pointHint and still close.
 * <p>
 * Names that are similar to each other are (n1 name1, n2 name2):
 * <ul>
 * <li>n1 == n2</li>
 * <li>n1 is significant substring of n2, e.g: n1="Main Road", n2="Main Road, New York"</li>
 * <li>n1 and n2 contain a reasonable longest common substring, e.g.: n1="Cape Point / Cape of Good Hope",
 *     n2="Cape Point Rd, Cape Peninsula, Cape Town, 8001, Afrique du Sud"</li>
 * </ul>
 * <p>
 * The aim is to allow minor typos/differences of the substrings, without having too much false positives.
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
public class NameSimilarityEdgeFilter implements EdgeFilter {

    private static final Map<String, String> DEFAULT_REWRITE_MAP = new HashMap<String, String>() {{
        // Words with 2 characters like "Dr" (Drive) will be ignored, so it is not required to list them here.
        // Words with 3 and more characters should be listed here to remove or rename them.
        for (String remove : Arrays.asList(
                "ally", "alley",
                "arc", "arcade",
                "bvd", "bvd.", "boulevard",
                "av.", "avenue", "avenida",
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
                "via")) {
            put(remove, "");
        }
        // expand instead of remove as significant part of the road name
        put("n", "north");
        put("s", "south");
        put("w", "west");
        put("e", "east");
        put("ne", "northeast");
        put("nw", "northwest");
        put("se", "southeast");
        put("sw", "southwest");
    }};
    private static final Pattern WORD_CHAR = Pattern.compile("\\p{LD}+");
    private static final JaroWinkler jaroWinkler = new JaroWinkler();
    private static final double JARO_WINKLER_ACCEPT_FACTOR = .9;
    private final EdgeFilter edgeFilter;
    private final String pointHint;
    private final Map<String, String> rewriteMap;
    private final Circle pointCircle;

    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String pointHint, GHPoint point, double radius) {
        this(edgeFilter, pointHint, point, radius, DEFAULT_REWRITE_MAP);
    }

    /**
     * @param radius     the searchable region about the point in meters
     * @param rewriteMap maps abbreviations to its longer form
     */
    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String pointHint, GHPoint point, double radius, Map<String, String> rewriteMap) {
        this.edgeFilter = edgeFilter;
        this.rewriteMap = rewriteMap;
        this.pointHint = prepareName(removeRelation(pointHint == null ? "" : pointHint));
        this.pointCircle = new Circle(point.lat, point.lon, radius);
    }

    String getNormalizedPointHint() {
        return pointHint;
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     * TODO Currently limited to certain 'western' languages
     */
    private String prepareName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        Matcher wordCharMatcher = WORD_CHAR.matcher(name);
        while (wordCharMatcher.find()) {
            String normalizedToken = toLowerCase(wordCharMatcher.group());
            String rewrite = rewriteMap.get(normalizedToken);
            if (rewrite != null)
                normalizedToken = rewrite;
            if (normalizedToken.isEmpty())
                continue;
            // Ignore matching short phrases like de, la, ... except it is a number
            if (normalizedToken.length() > 2) {
                sb.append(normalizedToken);
            } else {
                if (Character.isDigit(normalizedToken.charAt(0)) && (normalizedToken.length() == 1 || Character.isDigit(normalizedToken.charAt(1)))) {
                    sb.append(normalizedToken);
                }
            }
        }
        return sb.toString();
    }

    private String removeRelation(String edgeName) {
        int index = edgeName.lastIndexOf(", ");
        return index >= 0 ? edgeName.substring(0, index) : edgeName;
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        if (!edgeFilter.accept(iter)) {
            return false;
        }

        if (pointHint.isEmpty()) {
            return true;
        }

        String name = iter.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }

        BBox bbox = createBBox(iter);
        if (!pointCircle.intersects(bbox)) {
            return false;
        }

        name = removeRelation(name);
        String edgeName = prepareName(name);

        return isJaroWinklerSimilar(pointHint, edgeName);
    }

    private static BBox createBBox(EdgeIteratorState edgeState) {
        // we should include the entire geometry, see #2319
        PointList geometry = edgeState.fetchWayGeometry(FetchMode.ALL);
        BBox bbox = new BBox(180, -180, 90, -90);
        for (int i = 0; i < geometry.size(); i++)
            bbox.update(geometry.getLat(i), geometry.getLon(i));
        return bbox;
    }

    private boolean isJaroWinklerSimilar(String str1, String str2) {
        double jwSimilarity = jaroWinkler.similarity(str1, str2);
        // System.out.println(str1 + " vs. edge:" + str2 + ", " + jwSimilarity);
        return jwSimilarity > JARO_WINKLER_ACCEPT_FACTOR;
    }

    private boolean isLevenshteinSimilar(String hint, String name) {
        // too big length difference
        if (Math.min(name.length(), hint.length()) * 4 < Math.max(name.length(), hint.length()))
            return false;

        // The part 'abs(pointHint.length - name.length)' tries to make differences regarding length less important
        // Ie. 'hauptstraßedresden' vs. 'hauptstr.' should be considered a match, but 'hauptstraßedresden' vs. 'klingestraßedresden' should not match
        int factor = 1 + Math.abs(hint.length() - name.length());
        int levDistance = StringUtils.getLevenshteinDistance(hint, name);
        // System.out.println(hint + " vs. edge:" + name + ", " + levDistance + " <= " + factor);
        return levDistance <= factor;
    }
}
