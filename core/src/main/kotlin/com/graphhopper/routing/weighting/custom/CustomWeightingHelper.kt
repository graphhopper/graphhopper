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
package com.graphhopper.routing.weighting.custom

import com.graphhopper.json.MinMax
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.CustomModel
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import com.graphhopper.util.JsonFeature
import com.graphhopper.util.shapes.Polygon

/**
 * This class is for internal usage only. It is subclassed by Janino, then special expressions are
 * injected into init, getSpeed and getPriority. At the end an instance is created and used in CustomWeighting.
 */
open class CustomWeightingHelper protected constructor() {

    @JvmField
    protected var lookup: EncodedValueLookup? = null

    @JvmField
    protected var customModel: CustomModel? = null

    open fun init(customModel: CustomModel?, lookup: EncodedValueLookup?, areas: Map<String, @JvmSuppressWildcards JsonFeature>?) {
        this.lookup = lookup
        this.customModel = customModel
    }

    open fun getPriority(edge: EdgeIteratorState, reverse: Boolean): Double {
        return 1.0
    }

    open fun getSpeed(edge: EdgeIteratorState, reverse: Boolean): Double {
        return 1.0
    }

    open fun getTurnPenalty(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, viaNode: Int, outEdge: Int): Double {
        return 0.0
    }

    fun calcMaxSpeed(): Double {
        val minMaxSpeed = MinMax(0.0, GLOBAL_MAX_SPEED)
        FindMinMax.findMinMax(minMaxSpeed, customModel!!.getSpeed(), lookup!!)
        if (minMaxSpeed.min < 0)
            throw IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")")
        if (minMaxSpeed.max <= 0)
            throw IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max)
        if (minMaxSpeed.max == GLOBAL_MAX_SPEED)
            throw IllegalArgumentException("The first statement for 'speed' must be unconditionally to set the speed. But it was " + customModel!!.getSpeed()[0])

        return minMaxSpeed.max
    }

    fun calcMaxPriority(): Double {
        val minMaxPriority = MinMax(0.0, GLOBAL_PRIORITY)
        val statements = customModel!!.getPriority()
        if (!statements.isEmpty() && "true" == statements[0].condition()) {
            val value = statements[0].value()
            if (lookup!!.hasEncodedValue(value))
                minMaxPriority.max = lookup!!.getDecimalEncodedValue(value).maxOrMaxStorableDecimal
        }
        FindMinMax.findMinMax(minMaxPriority, statements, lookup!!)
        if (minMaxPriority.min < 0)
            throw IllegalArgumentException("priority has to be >=0 but can be negative (" + minMaxPriority.min + ")")
        if (minMaxPriority.max < 0)
            throw IllegalArgumentException("maximum priority has to be >=0 but was " + minMaxPriority.max)
        return minMaxPriority.max
    }

    companion object {
        internal const val GLOBAL_MAX_SPEED = 999.0
        internal const val GLOBAL_PRIORITY = 1.0

        @JvmStatic
        fun `in`(p: Polygon, edge: EdgeIteratorState): Boolean {
            val edgeBBox = GHUtility.createBBox(edge)
            val polyBBOX = p.bounds
            if (!polyBBOX.intersects(edgeBBox))
                return false
            if (p.isRectangle() && polyBBOX.contains(edgeBBox))
                return true
            return p.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()) // TODO PERF: cache bbox and edge wayGeometry for multiple area
        }

        @JvmStatic
        fun calcChangeAngle(edgeIntAccess: EdgeIntAccess, orientationEnc: DecimalEncodedValue,
                            inEdge: Int, inEdgeReverse: Boolean, outEdge: Int, outEdgeReverse: Boolean): Double {
            val prevAzimuth = orientationEnc.getDecimal(inEdgeReverse, inEdge, edgeIntAccess)
            val azimuth = orientationEnc.getDecimal(outEdgeReverse, outEdge, edgeIntAccess)
            return calcChangeAngle(prevAzimuth, azimuth)
        }

        @JvmStatic
        fun calcChangeAngle(prevAzimuth: Double, azimuth: Double): Double {
            // bring parallel to prevOrientation
            val parallelAzimuth = (azimuth + 180) % 360.0

            val changeAngle = parallelAzimuth - prevAzimuth

            // keep in [-180, 180]
            return (changeAngle + 540.0) % 360.0 - 180.0
        }
    }
}
