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

/**
 * This class contains various parameters that control the behavior of {@link Router}.
 */
public class RouterConfig {
    /**
 * This class contains various parameters that control the behavior of {@link Router}.
 */
public class RouterConfig implements IRouterConfig {
    private int maxVisitedNodes = Integer.MAX_VALUE;
    private long timeoutMillis = Long.MAX_VALUE;
    private int maxRoundTripRetries = 3;
    private int nonChMaxWaypointDistance = Integer.MAX_VALUE;
    private boolean calcPoints = true;
    private boolean instructionsEnabled = true;
    private boolean simplifyResponse = true;
    private double elevationWayPointMaxDistance = Double.MAX_VALUE;
    private int activeLandmarkCount = 8;

    @Override
    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    @Override
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public int getMaxRoundTripRetries() {
        return maxRoundTripRetries;
    }

    @Override
    public void setMaxRoundTripRetries(int maxRoundTripRetries) {
        this.maxRoundTripRetries = maxRoundTripRetries;
    }

    @Override
    public int getNonChMaxWaypointDistance() {
        return nonChMaxWaypointDistance;
    }

    @Override
    public void setNonChMaxWaypointDistance(int nonChMaxWaypointDistance) {
        this.nonChMaxWaypointDistance = nonChMaxWaypointDistance;
    }

    @Override
    public boolean isCalcPoints() {
        return calcPoints;
    }

    @Override
    public void setCalcPoints(boolean calcPoints) {
        this.calcPoints = calcPoints;
    }

    @Override
    public boolean isInstructionsEnabled() {
        return instructionsEnabled;
    }

    @Override
    public void setInstructionsEnabled(boolean instructionsEnabled) {
        this.instructionsEnabled = instructionsEnabled;
    }

    @Override
    public boolean isSimplifyResponse() {
        return simplifyResponse;
    }

    @Override
    public void setSimplifyResponse(boolean simplifyResponse) {
        this.simplifyResponse = simplifyResponse;
    }

    @Override
    public int getActiveLandmarkCount() {
        return activeLandmarkCount;
    }

    @Override
    public void setActiveLandmarkCount(int activeLandmarkCount) {
        this.activeLandmarkCount = activeLandmarkCount;
    }

    @Override
    public double getElevationWayPointMaxDistance() {
        return elevationWayPointMaxDistance;
    }

    @Override
    public void setElevationWayPointMaxDistance(double elevationWayPointMaxDistance) {
        this.elevationWayPointMaxDistance = elevationWayPointMaxDistance;
    }

}
}