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
package com.graphhopper.routing

import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_AUTO
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_LEFT
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT
import com.graphhopper.util.Parameters.Routing.CURBSIDE
import java.util.Objects

class DirectionResolverResult private constructor(
    private val inEdgeRight: Int,
    private val outEdgeRight: Int,
    private val inEdgeLeft: Int,
    private val outEdgeLeft: Int
) {
    fun getInEdgeRight(): Int = inEdgeRight

    fun getOutEdgeRight(): Int = outEdgeRight

    fun getInEdgeLeft(): Int = inEdgeLeft

    fun getOutEdgeLeft(): Int = outEdgeLeft

    fun isRestricted(): Boolean = !equals(UNRESTRICTED)

    fun isImpossible(): Boolean = equals(IMPOSSIBLE)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as DirectionResolverResult
        return inEdgeRight == that.inEdgeRight &&
                outEdgeRight == that.outEdgeRight &&
                inEdgeLeft == that.inEdgeLeft &&
                outEdgeLeft == that.outEdgeLeft
    }

    override fun hashCode(): Int = Objects.hash(inEdgeRight, outEdgeRight, inEdgeLeft, outEdgeLeft)

    override fun toString(): String {
        return if (!isRestricted()) {
            "unrestricted"
        } else if (isImpossible()) {
            "impossible"
        } else {
            "in-edge-right: " + pretty(inEdgeRight) + ", out-edge-right: " + pretty(outEdgeRight) + ", in-edge-left: " + pretty(inEdgeLeft) + ", out-edge-left: " + pretty(outEdgeLeft)
        }
    }

    private fun pretty(edgeId: Int): String {
        return when (edgeId) {
            NO_EDGE -> "NO_EDGE"
            ANY_EDGE -> "ANY_EDGE"
            else -> edgeId.toString()
        }
    }

    companion object {
        private val UNRESTRICTED = DirectionResolverResult(ANY_EDGE, ANY_EDGE, ANY_EDGE, ANY_EDGE)
        private val IMPOSSIBLE = DirectionResolverResult(NO_EDGE, NO_EDGE, NO_EDGE, NO_EDGE)

        @JvmStatic
        fun onlyLeft(inEdge: Int, outEdge: Int): DirectionResolverResult =
            DirectionResolverResult(NO_EDGE, NO_EDGE, inEdge, outEdge)

        @JvmStatic
        fun onlyRight(inEdge: Int, outEdge: Int): DirectionResolverResult =
            DirectionResolverResult(inEdge, outEdge, NO_EDGE, NO_EDGE)

        @JvmStatic
        fun restricted(inEdgeRight: Int, outEdgeRight: Int, inEdgeLeft: Int, outEdgeLeft: Int): DirectionResolverResult =
            DirectionResolverResult(inEdgeRight, outEdgeRight, inEdgeLeft, outEdgeLeft)

        @JvmStatic
        fun unrestricted(): DirectionResolverResult = UNRESTRICTED

        @JvmStatic
        fun impossible(): DirectionResolverResult = IMPOSSIBLE

        @JvmStatic
        fun getOutEdge(directionResolverResult: DirectionResolverResult, curbside: String): Int {
            var curbside = curbside
            if (curbside.trim().isEmpty()) {
                curbside = CURBSIDE_ANY
            }
            return when (curbside) {
                CURBSIDE_RIGHT -> directionResolverResult.getOutEdgeRight()
                CURBSIDE_LEFT -> directionResolverResult.getOutEdgeLeft()
                CURBSIDE_ANY -> ANY_EDGE
                else -> throw IllegalArgumentException("Unknown value for " + CURBSIDE + " : '" + curbside + "'. allowed: " + CURBSIDE_LEFT + ", " + CURBSIDE_RIGHT + ", " + CURBSIDE_ANY + ", " + CURBSIDE_AUTO)
            }
        }

        @JvmStatic
        fun getInEdge(directionResolverResult: DirectionResolverResult, curbside: String): Int {
            var curbside = curbside
            if (curbside.trim().isEmpty()) {
                curbside = CURBSIDE_ANY
            }
            return when (curbside) {
                CURBSIDE_RIGHT -> directionResolverResult.getInEdgeRight()
                CURBSIDE_LEFT -> directionResolverResult.getInEdgeLeft()
                CURBSIDE_ANY -> ANY_EDGE
                // note: the quote placement differs from getOutEdge in the Java original and is kept as-is
                else -> throw IllegalArgumentException("Unknown value for '" + CURBSIDE + " : " + curbside + "'. allowed: " + CURBSIDE_LEFT + ", " + CURBSIDE_RIGHT + ", " + CURBSIDE_ANY + ", " + CURBSIDE_AUTO)
            }
        }
    }
}
