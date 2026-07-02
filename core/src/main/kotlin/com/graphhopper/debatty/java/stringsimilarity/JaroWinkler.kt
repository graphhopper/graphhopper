/*
Copyright 2015 Thibault Debatty.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.graphhopper.debatty.java.stringsimilarity

/**
 * This class is copied (and ported to Kotlin) from: https://github.com/tdebatty/java-string-similarity/blob/master/src/main/java/info/debatty/java/stringsimilarity/JaroWinkler.java
 * and slightly modified. *
 *
 * The Jaro–Winkler distance metric is designed and best suited for short
 * strings such as person names, and to detect typos; it is (roughly) a
 * variation of Damerau-Levenshtein, where the substitution of 2 close
 * characters is considered less important then the substitution of 2 characters
 * that a far from each other.
 * Jaro-Winkler was developed in the area of record linkage (duplicate
 * detection) (Winkler, 1990). It returns a value in the interval [0.0, 1.0].
 * The distance is computed as 1 - Jaro-Winkler similarity.
 * @author Thibault Debatty
 */
class JaroWinkler @JvmOverloads constructor(
    /**
     * The threshold used for adding the Winkler bonus. Set it to a negative value to get the
     * Jaro distance. The default value is 0.7.
     */
    val threshold: Double = DEFAULT_THRESHOLD
) {

    /**
     * Compute JW similarity.
     */
    fun similarity(s1: String, s2: String): Double {
        val mtp = matches(s1, s2)
        // float on purpose to keep the exact result of the original implementation
        val m = mtp[0].toFloat()
        if (m == 0f) return 0.0

        val j = ((m / s1.length + m / s2.length + (m - mtp[1]) / m) / THREE).toDouble()
        return if (j > threshold) j + minOf(JW_COEF, 1.0 / mtp[THREE]) * mtp[2] * (1 - j) else j
    }

    /**
     * Return 1 - similarity.
     */
    fun distance(s1: String, s2: String): Double = 1.0 - similarity(s1, s2)

    private fun matches(s1: String, s2: String): IntArray {
        val max: String
        val min: String
        if (s1.length > s2.length) {
            max = s1
            min = s2
        } else {
            max = s2
            min = s1
        }
        val range = maxOf(max.length / 2 - 1, 0)
        val matchIndexes = IntArray(min.length) { -1 }
        val matchFlags = BooleanArray(max.length)
        var matches = 0
        for (mi in min.indices) {
            val c1 = min[mi]
            for (xi in maxOf(mi - range, 0) until minOf(mi + range + 1, max.length)) {
                if (!matchFlags[xi] && c1 == max[xi]) {
                    matchIndexes[mi] = xi
                    matchFlags[xi] = true
                    matches++
                    break
                }
            }
        }
        val ms1 = CharArray(matches)
        val ms2 = CharArray(matches)
        var si = 0
        for (i in min.indices) {
            if (matchIndexes[i] != -1) {
                ms1[si] = min[i]
                si++
            }
        }
        si = 0
        for (i in max.indices) {
            if (matchFlags[i]) {
                ms2[si] = max[i]
                si++
            }
        }
        var transpositions = 0
        for (mi in ms1.indices) {
            if (ms1[mi] != ms2[mi]) transpositions++
        }
        var prefix = 0
        for (mi in min.indices) {
            if (s1[mi] == s2[mi]) prefix++ else break
        }
        return intArrayOf(matches, transpositions / 2, prefix, max.length)
    }

    companion object {
        private const val DEFAULT_THRESHOLD = 0.7
        private const val THREE = 3
        private const val JW_COEF = 0.1
    }
}
