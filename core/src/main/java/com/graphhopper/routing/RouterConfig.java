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

package com.graphhopper.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains various parameters that control the behavior of {@link Router}.
 */
public class RouterConfig {
    private int maxVisitedNodes = Integer.MAX_VALUE;
    private long timeoutMillis = Long.MAX_VALUE;
    private int maxRoundTripRetries = 3;
    private int nonChMaxWaypointDistance = Integer.MAX_VALUE;
    private boolean calcPoints = true;
    private boolean instructionsEnabled = true;
    private boolean simplifyResponse = true;
    private double elevationWayPointMaxDistance = Double.MAX_VALUE;
    private int activeLandmarkCount = 8;
    private List<String> turnLanesProfiles = new ArrayList<>();

    public void setTurnLanesProfiles(List<String> turnLanesProfiles) {
        this.turnLanesProfiles = turnLanesProfiles;
    }

    public List<String> getTurnLanesProfiles() {
        return turnLanesProfiles;
    }

    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    /**
     * This methods stops the algorithm from searching further if the resulting path would go over
     * the specified node count, important if none-CH routing is used.
     */
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Limits the runtime of routing requests to the given amount of milliseconds. This only works up to a certain
     * precision, but should be sufficient to cancel long-running requests in most cases. The exact implementation of
     * the timeout depends on the routing algorithm.
     */
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public int getMaxRoundTripRetries() {
        return maxRoundTripRetries;
    }

    public void setMaxRoundTripRetries(int maxRoundTripRetries) {
        this.maxRoundTripRetries = maxRoundTripRetries;
    }

    public int getNonChMaxWaypointDistance() {
        return nonChMaxWaypointDistance;
    }

    public void setNonChMaxWaypointDistance(int nonChMaxWaypointDistance) {
        this.nonChMaxWaypointDistance = nonChMaxWaypointDistance;
    }

    public boolean isCalcPoints() {
        return calcPoints;
    }

    /**
     * This methods enables gps point calculation. If disabled only distance will be calculated.
     */
    public void setCalcPoints(boolean calcPoints) {
        this.calcPoints = calcPoints;
    }

    public boolean isInstructionsEnabled() {
        return instructionsEnabled;
    }

    public void setInstructionsEnabled(boolean instructionsEnabled) {
        this.instructionsEnabled = instructionsEnabled;
    }

    public boolean isSimplifyResponse() {
        return simplifyResponse;
    }

    /**
     * This method specifies if the returned path should be simplified or not, via Ramer-Douglas-Peucker
     * or similar algorithm.
     */
    public void setSimplifyResponse(boolean simplifyResponse) {
        this.simplifyResponse = simplifyResponse;
    }

    public int getActiveLandmarkCount() {
        return activeLandmarkCount;
    }

    public void setActiveLandmarkCount(int activeLandmarkCount) {
        this.activeLandmarkCount = activeLandmarkCount;
    }

    public double getElevationWayPointMaxDistance() {
        return elevationWayPointMaxDistance;
    }

    public void setElevationWayPointMaxDistance(double elevationWayPointMaxDistance) {
        this.elevationWayPointMaxDistance = elevationWayPointMaxDistance;
    }
}
