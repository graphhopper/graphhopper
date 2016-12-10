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

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Abstract Class that defines the basis for NameSimilarity matching using an EdgeFilter.
 *
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilter implements EdgeFilter {

    public static final int EXACT = 0;
    public static final int LEVENSHTEIN = 1;
    public static final int STRING_MATCHING_ALGO = LEVENSHTEIN;

    private static final double LEVENSHTEIN_ACCEPT_FACTOR = .05;

    private final EdgeFilter edgeFilter;
    private final String soughtName;
    private static final Pattern nonWordCharacter = Pattern.compile("[^\\p{L}]+");

    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String soughtName) {
        this.edgeFilter = edgeFilter;
        this.soughtName = prepareName(soughtName);
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     */
    private String prepareName(String name) {
        if (name == null) {
            name = "";
        }

        name = nonWordCharacter.matcher(name).replaceAll("");
        name = name.toLowerCase();

        return name;
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

        // Don't check if PointHint is empty anyway
        if (soughtName.isEmpty()) {
            return true;
        }

        String name = iter.getName();

        if (name == null || name.isEmpty()) {
            return false;
        }

        name = removeRelation(name);
        name = prepareName(name);

        switch (STRING_MATCHING_ALGO) {
            case LEVENSHTEIN:
                return isLevenshteinSimilar(name);
            case EXACT:
            default:
                return name.equals(soughtName);
        }

    }

    private boolean isLevenshteinSimilar(String name) {
        int perfectDistance = (int) (Math.abs(soughtName.length() - name.length()) + Math.ceil(soughtName.length() * LEVENSHTEIN_ACCEPT_FACTOR));
        int levDistance = StringUtils.getLevenshteinDistance(soughtName, name);
        return levDistance <= perfectDistance;
    }
}
