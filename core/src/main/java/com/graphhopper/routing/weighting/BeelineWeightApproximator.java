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
package com.graphhopper.routing.weighting;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;

/**
 * Approximates the distance to the goal node by weighting the beeline distance according to the
 * distance weighting
 *
 * @author jansoe
 */
public class BeelineWeightApproximator implements WeightApproximator {
    private final NodeAccess nodeAccess;
    private final Weighting weighting;
    private DistanceCalc distanceCalc = Helper.DIST_EARTH;
    private double toLat, toLon;
    private double epsilon = 1;

    public BeelineWeightApproximator(NodeAccess nodeAccess, Weighting weighting) {
        this.nodeAccess = nodeAccess;
        this.weighting = weighting;
    }

    @Override
    public void setTo(int toNode) {
        toLat = nodeAccess.getLatitude(toNode);
        toLon = nodeAccess.getLongitude(toNode);
    }

    public WeightApproximator setEpsilon(double epsilon) {
        this.epsilon = epsilon;
        return this;
    }

    @Override
    public WeightApproximator reverse() {
        return new BeelineWeightApproximator(nodeAccess, weighting).setDistanceCalc(distanceCalc).setEpsilon(epsilon);
    }

    @Override
    public double approximate(int fromNode) {
        double fromLat = nodeAccess.getLatitude(fromNode);
        double fromLon = nodeAccess.getLongitude(fromNode);
        double dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon);
        double weight2goal = weighting.getMinWeight(dist2goal);
        return weight2goal * epsilon;
    }

    public BeelineWeightApproximator setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
        return this;
    }

    @Override
    public String toString() {
        return "beeline";
    }
}
