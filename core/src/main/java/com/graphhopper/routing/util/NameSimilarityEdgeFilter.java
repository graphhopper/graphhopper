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

import com.graphhopper.debatty.java.stringsimilarity.JaroWinkler;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This class defines the basis for NameSimilarity matching using an EdgeFilter.
 * The typical use-case is to match not the nearest edge in
 * {@link com.graphhopper.storage.index.LocationIndex#findClosest(double, double, EdgeFilter)}
 * but the match the edge which name is closest to the pointHint
 * <p>
 * Names that are similar to each other are (n1 name1, n2 name2):
 * <ul>
 * <li>n1 == n2</li>
 * <li>n1 is significant substring of n2, e.g: n1="Main Road", n2="Main Road, New York"</li>
 * <li>n1 and n2 contain a reasonable longest common substring, e.g.: n1="Cape Point / Cape of Good Hope", n2="Cape Point Rd, Cape Peninsula, Cape Town, 8001, Afrique du Sud"</li>
 * </ul>
 * <p>
 * We aim for allowing slight typos/differences of the substrings, without having too much false positives.
 *
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilter implements EdgeFilter {

    private static final Pattern NON_WORD_CHAR = Pattern.compile("[^\\p{L}]+");
    private final double JARO_WINKLER_ACCEPT_FACTOR = .79;
    private final JaroWinkler jaroWinkler = new JaroWinkler();

    private final EdgeFilter edgeFilter;
    private final String pointHint;

    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String pointHint) {
        this.edgeFilter = edgeFilter;
        this.pointHint = prepareName(pointHint == null ? "" : pointHint);
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     * TODO: Remove common street names like: street, road, avenue?
     */
    private String prepareName(String name) {
        // TODO make this better, also split at ',' and others?
        // TODO This limits the approach to certain 'western' languages
        // \s = A whitespace character: [ \t\n\x0B\f\r]
        String[] arr = name.split("\\s");
        String tmp;
        List<String> list = new ArrayList<>(arr.length);
        for (int i = 0; i < arr.length; i++) {
            tmp = NON_WORD_CHAR.matcher(arr[i].toLowerCase()).replaceAll("");
            // Ignore matching short frases like, de, rue, st, etc.
            if (!tmp.isEmpty() && tmp.length() > 3) {
                list.add(tmp);
            }
        }
        return listToString(list);
    }

    private String removeRelation(String edgeName) {
        if (edgeName != null && edgeName.contains(", ")) {
            edgeName = edgeName.substring(0, edgeName.lastIndexOf(','));
        }
        return edgeName;
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

        name = removeRelation(name);
        String edgeName = prepareName(name);

        return isJaroWinklerSimilar(pointHint, edgeName);
    }

    private boolean isJaroWinklerSimilar(String str1, String str2) {
        double jwSimilarity = jaroWinkler.similarity(str1, str2);
        // System.out.println(str1 + " vs. edge:" + str2 + ", " + jwSimilarity);
        return jwSimilarity > JARO_WINKLER_ACCEPT_FACTOR;
    }

    private final String listToString(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            b.append(list.get(i));
        }
        return b.toString();
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
