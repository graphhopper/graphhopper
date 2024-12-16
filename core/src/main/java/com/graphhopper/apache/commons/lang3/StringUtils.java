/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.apache.commons.lang3;


/**
 * This class is a partial copy of the org.apache.commons.lang3.StringUtils
 * that can be found here: https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/StringUtils.java
 * <p>
 * The library can be found here: https://commons.apache.org/proper/commons-lang/
 */
public class StringUtils {

    /**
     * <p>Find the Levenshtein distance between two Strings.</p>
     * <p>
     * <p>This is the number of changes needed to change one String into
     * another, where each change is a single character modification (deletion,
     * insertion or substitution).</p>
     * <p>
     * <p>The implementation uses a single-dimensional array of length s.length() + 1. See
     * <a href="http://blog.softwx.net/2014/12/optimizing-levenshtein-algorithm-in-c.html">
     * http://blog.softwx.net/2014/12/optimizing-levenshtein-algorithm-in-c.html</a> for details.</p>
     * <p>
     * <pre>
     * StringUtils.getLevenshteinDistance(null, *)             = IllegalArgumentException
     * StringUtils.getLevenshteinDistance(*, null)             = IllegalArgumentException
     * StringUtils.getLevenshteinDistance("","")               = 0
     * StringUtils.getLevenshteinDistance("","a")              = 1
     * StringUtils.getLevenshteinDistance("aaapppp", "")       = 7
     * StringUtils.getLevenshteinDistance("frog", "fog")       = 1
     * StringUtils.getLevenshteinDistance("fly", "ant")        = 3
     * StringUtils.getLevenshteinDistance("elephant", "hippo") = 7
     * StringUtils.getLevenshteinDistance("hippo", "elephant") = 7
     * StringUtils.getLevenshteinDistance("hippo", "zzzzzzzz") = 8
     * StringUtils.getLevenshteinDistance("hello", "hallo")    = 1
     * </pre>
     *
     * @param s the first String, must not be null
     * @param t the second String, must not be null
     * @return result distance
     * @throws IllegalArgumentException if either String input {@code null}
     * @since 3.0 Changed signature from getLevenshteinDistance(String, String) to
     * getLevenshteinDistance(CharSequence, CharSequence)
     */
    public static int getLevenshteinDistance(CharSequence s, CharSequence t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }

        int n = s.length();
        int m = t.length();

        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }

        if (n > m) {
            // swap the input strings to consume less memory
            final CharSequence tmp = s;
            s = t;
            t = tmp;
            n = m;
            m = t.length();
        }

        final int p[] = new int[n + 1];
        // indexes into strings s and t
        int i; // iterates through s
        int j; // iterates through t
        int upper_left;
        int upper;

        char t_j; // jth character of t
        int cost;

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            upper_left = p[0];
            t_j = t.charAt(j - 1);
            p[0] = j;

            for (i = 1; i <= n; i++) {
                upper = p[i];
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                p[i] = Math.min(Math.min(p[i - 1] + 1, p[i] + 1), upper_left + cost);
                upper_left = upper;
            }
        }

        return p[n];
    }

}
