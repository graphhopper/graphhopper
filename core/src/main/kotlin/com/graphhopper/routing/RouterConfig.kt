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

/**
 * This class contains various parameters that control the behavior of [Router].
 */
class RouterConfig {
    private var maxVisitedNodes = Int.MAX_VALUE
    private var timeoutMillis = Long.MAX_VALUE
    private var maxRoundTripRetries = 3
    private var nonChMaxWaypointDistance = Int.MAX_VALUE
    private var calcPoints = true
    private var instructionsEnabled = true
    private var viaPointInstructionsEnabled = true
    private var simplifyResponse = true
    private var elevationWayPointMaxDistance = Double.MAX_VALUE
    private var activeLandmarkCount = 8

    fun getMaxVisitedNodes(): Int = maxVisitedNodes

    /**
     * This methods stops the algorithm from searching further if the resulting path would go over
     * the specified node count, important if none-CH routing is used.
     */
    fun setMaxVisitedNodes(maxVisitedNodes: Int) {
        this.maxVisitedNodes = maxVisitedNodes
    }

    fun getTimeoutMillis(): Long = timeoutMillis

    /**
     * Limits the runtime of routing requests to the given amount of milliseconds. This only works up to a certain
     * precision, but should be sufficient to cancel long-running requests in most cases. The exact implementation of
     * the timeout depends on the routing algorithm.
     */
    fun setTimeoutMillis(timeoutMillis: Long) {
        this.timeoutMillis = timeoutMillis
    }

    fun getMaxRoundTripRetries(): Int = maxRoundTripRetries

    fun setMaxRoundTripRetries(maxRoundTripRetries: Int) {
        this.maxRoundTripRetries = maxRoundTripRetries
    }

    fun getNonChMaxWaypointDistance(): Int = nonChMaxWaypointDistance

    fun setNonChMaxWaypointDistance(nonChMaxWaypointDistance: Int) {
        this.nonChMaxWaypointDistance = nonChMaxWaypointDistance
    }

    fun isCalcPoints(): Boolean = calcPoints

    /**
     * This methods enables gps point calculation. If disabled only distance will be calculated.
     */
    fun setCalcPoints(calcPoints: Boolean) {
        this.calcPoints = calcPoints
    }

    fun isInstructionsEnabled(): Boolean = instructionsEnabled

    fun setInstructionsEnabled(instructionsEnabled: Boolean) {
        this.instructionsEnabled = instructionsEnabled
    }

    fun isViaPointInstructionsEnabled(): Boolean = viaPointInstructionsEnabled

    fun setViaPointInstructionsEnabled(viaPointInstructionsEnabled: Boolean) {
        this.viaPointInstructionsEnabled = viaPointInstructionsEnabled
    }

    fun isSimplifyResponse(): Boolean = simplifyResponse

    /**
     * This method specifies if the returned path should be simplified or not, via Ramer-Douglas-Peucker
     * or similar algorithm.
     */
    fun setSimplifyResponse(simplifyResponse: Boolean) {
        this.simplifyResponse = simplifyResponse
    }

    fun getActiveLandmarkCount(): Int = activeLandmarkCount

    fun setActiveLandmarkCount(activeLandmarkCount: Int) {
        this.activeLandmarkCount = activeLandmarkCount
    }

    fun getElevationWayPointMaxDistance(): Double = elevationWayPointMaxDistance

    fun setElevationWayPointMaxDistance(elevationWayPointMaxDistance: Double) {
        this.elevationWayPointMaxDistance = elevationWayPointMaxDistance
    }
}
