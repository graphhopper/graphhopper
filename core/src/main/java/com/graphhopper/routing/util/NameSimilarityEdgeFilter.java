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
import info.debatty.java.stringsimilarity.JaroWinkler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract Class that defines the basis for NameSimilarity matching using an EdgeFilter.
 *
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilter implements EdgeFilter {

    private static final Pattern NON_WORD_CHAR = Pattern.compile("[^\\p{L}]+");
    private final EdgeFilter edgeFilter;
    private final List<String> pointHint;

    private JaroWinkler jw = new JaroWinkler();

    public NameSimilarityEdgeFilter(EdgeFilter edgeFilter, String pointHint) {
        this.edgeFilter = edgeFilter;
        this.pointHint = prepareName(pointHint == null ? "" : pointHint);
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     */
    private List<String> prepareName(String name) {
        // TODO make this better, also split at ',' and others?
        String[] arr = name.split(" ");
        String tmp;
        List<String> list = new ArrayList<>(arr.length);
        for (int i = 0; i < arr.length; i++) {
            tmp = NON_WORD_CHAR.matcher(arr[i].toLowerCase()).replaceAll("");
            if(!tmp.isEmpty()){
                list.add(tmp);
            }
        }
        return list;
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
        List<String> edgeName = prepareName(name);

        List<String> shorterList;
        List<String> longerList;

        if(pointHint.size() > edgeName.size()){
            // Important to create a clone, since we remove entries
            // TODO maybe the remove is not that good?
            longerList = new ArrayList<>(pointHint);
            shorterList = edgeName;
        }else{
            longerList = edgeName;
            shorterList = new ArrayList<>(pointHint);
        }
        boolean similar = false;
        for (String str1: shorterList) {
            for (int i = 0; i < longerList.size(); i++) {
                if(isJaroWinklerSimilar(str1, longerList.get(i))){
                    // Avoid matchin same string twice, also make it more efficient
                    longerList.remove(i);
                    break;
                }
                // If in last iteration and no match was found
                if(i == longerList.size()-1){
                    return false;
                }
            }
        }
        // We found a match for every string in the shorter list, therefore strings are similar
        return true;
    }

    private boolean isJaroWinklerSimilar(String str1, String str2) {
        // too big length difference
        if (Math.min(str2.length(), str1.length()) * 4 < Math.max(str2.length(), str1.length()))
            return false;

        double jwSimilarity = jw.similarity(str1, str2);
        //System.out.println(str1 + " vs. edge:" + str2 + ", " + jwSimilarity);
        return jwSimilarity > .9;
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
