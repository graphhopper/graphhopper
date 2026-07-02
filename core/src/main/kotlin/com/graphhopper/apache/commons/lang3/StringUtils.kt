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
package com.graphhopper.apache.commons.lang3

/**
 * This class is a partial copy (ported to Kotlin) of the org.apache.commons.lang3.StringUtils
 * that can be found here: https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/StringUtils.java
 *
 * The library can be found here: https://commons.apache.org/proper/commons-lang/
 */
object StringUtils {

    /**
     * Find the Levenshtein distance between two Strings.
     *
     * This is the number of changes needed to change one String into another, where each change
     * is a single character modification (deletion, insertion or substitution).
     *
     * The implementation uses a single-dimensional array of length s.length() + 1. See
     * [http://blog.softwx.net/2014/12/optimizing-levenshtein-algorithm-in-c.html](http://blog.softwx.net/2014/12/optimizing-levenshtein-algorithm-in-c.html)
     * for details.
     *
     * ```
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
     * ```
     *
     * @param s the first String, must not be null
     * @param t the second String, must not be null
     * @return result distance
     * @throws IllegalArgumentException if either String input `null`
     */
    @JvmStatic
    fun getLevenshteinDistance(s: CharSequence?, t: CharSequence?): Int {
        var s = s ?: throw IllegalArgumentException("Strings must not be null")
        var t = t ?: throw IllegalArgumentException("Strings must not be null")

        var n = s.length
        var m = t.length

        if (n == 0) return m
        if (m == 0) return n

        if (n > m) {
            // swap the input strings to consume less memory
            val tmp = s
            s = t
            t = tmp
            n = m
            m = t.length
        }

        val p = IntArray(n + 1) { it }

        for (j in 1..m) {
            // upperLeft: the cell diagonally left and up, upper: the cell above
            var upperLeft = p[0]
            val tj = t[j - 1]
            p[0] = j

            for (i in 1..n) {
                val upper = p[i]
                val cost = if (s[i - 1] == tj) 0 else 1
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                p[i] = minOf(p[i - 1] + 1, p[i] + 1, upperLeft + cost)
                upperLeft = upper
            }
        }

        return p[n]
    }
}
