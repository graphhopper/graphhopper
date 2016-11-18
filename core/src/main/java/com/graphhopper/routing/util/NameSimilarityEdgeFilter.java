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

    private static final Pattern NON_WORD_CHAR = Pattern.compile("[^\\p{L}]+");
    private final EdgeFilter edgeFilter;
    private final String pointHint;

    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String pointHint) {
        this.edgeFilter = edgeFilter;
        this.pointHint = prepareName(pointHint == null ? "" : pointHint);
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     */
    private String prepareName(String name) {
        return NON_WORD_CHAR.matcher(name.toLowerCase()).replaceAll("");
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

        // Don't check if point hint is empty anyway
        if (pointHint.isEmpty()) {
            return true;
        }

        String name = iter.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }

        name = removeRelation(name);
        name = prepareName(name);
        return isLevenshteinSimilar(name);
    }

    private boolean isLevenshteinSimilar(String name) {
        // too big length difference
        if (Math.min(name.length(), pointHint.length()) * 4 < Math.max(name.length(), pointHint.length()))
            return false;

        // The part 'abs(pointHint.length - name.length)' tries to make differences regarding length less important
        // Ie. 'hauptstraßedresden' vs. 'hauptstr.' should be considered a match, but 'hauptstraßedresden' vs. 'klingestraßedresden' should not match
        int factor = 1 + Math.abs(pointHint.length() - name.length());
        int levDistance = StringUtils.getLevenshteinDistance(pointHint, name);
        // System.out.println(pointHint + " vs. edge:" + name + ", " + levDistance + " <= " + factor);
        return levDistance <= factor;
    }

    public static String longestSubstring(String str1, String str2) {
        StringBuilder sb = new StringBuilder();

        // java initializes them already with 0
        int[][] num = new int[str1.length()][str2.length()];
        int maxlen = 0;
        int lastSubsBegin = 0;

        for (int i = 0; i < str1.length(); i++) {
            for (int j = 0; j < str2.length(); j++) {
                if (str1.charAt(i) == str2.charAt(j)) {
                    if ((i == 0) || (j == 0))
                        num[i][j] = 1;
                    else
                        num[i][j] = 1 + num[i - 1][j - 1];

                    if (num[i][j] > maxlen) {
                        maxlen = num[i][j];
                        // generate substring from str1 => i
                        int thisSubsBegin = i - num[i][j] + 1;
                        if (lastSubsBegin == thisSubsBegin) {
                            //if the current LCS is the same as the last time this block ran
                            sb.append(str1.charAt(i));
                        } else {
                            //this block resets the string builder if a different LCS is found
                            lastSubsBegin = thisSubsBegin;
                            sb = new StringBuilder();
                            sb.append(str1.substring(lastSubsBegin, i + 1));
                        }
                    }
                }
            }
        }

        return sb.toString();
    }
}
