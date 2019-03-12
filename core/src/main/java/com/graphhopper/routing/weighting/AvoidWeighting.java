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

import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.routing.profiles.Toll;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.profiles.RoadClass.*;
import static com.graphhopper.routing.profiles.RoadEnvironment.*;

/**
 * A simple Weighting to demonstrate how to avoid certain road classes based on the EncodedValue RoadClass.
 * <p>
 * Note: might be replaced in future versions.
 */
public class AvoidWeighting extends ShortFastestWeighting {

    private boolean avoidFerry;
    private boolean avoidBridge;
    private boolean avoidFord;
    private boolean avoidTunnel;

    private boolean avoidToll;

    private boolean avoidMotorway;
    private boolean avoidTrunk;
    private boolean avoidPrimary;
    private boolean avoidSecondary;
    private boolean avoidTertiary;
    private boolean avoidTrack;
    private boolean avoidResidential;
    private EnumEncodedValue<Toll> tollEnc;
    private EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    private EnumEncodedValue<RoadClass> roadClassEnc;
    private double avoidFactor;

    public AvoidWeighting(FlagEncoder encoder, PMap map) {
        super(encoder, map);

        String avoidStr = map.get("avoid", "");
        if (encoder.hasEncodedValue(RoadEnvironment.KEY)) {
            avoidFerry = avoidStr.contains("ferry");
            avoidBridge = avoidStr.contains("bridge");
            avoidFord = avoidStr.contains("ford");
            avoidTunnel = avoidStr.contains("tunnel");
            roadEnvEnc = encoder.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        }

        if (encoder.hasEncodedValue(Toll.KEY)) {
            avoidToll = avoidStr.contains("toll");
            tollEnc = encoder.getEnumEncodedValue(Toll.KEY, Toll.class);
        }

        if (encoder.hasEncodedValue(RoadClass.KEY)) {
            avoidMotorway = avoidStr.contains("motorway");
            avoidTrunk = avoidStr.contains("trunk");
            avoidPrimary = avoidStr.contains("primary");
            avoidSecondary = avoidStr.contains("secondary");
            avoidTertiary = avoidStr.contains("tertiary");
            avoidTrack = avoidStr.contains("track");
            avoidResidential = avoidStr.contains("residential");
            roadClassEnc = encoder.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        }

        // can be used for preferring too
        avoidFactor = Math.min(10, Math.max(0.1, map.getDouble("avoid.factor", 10)));
    }

    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double weight = super.calcWeight(edge, reverse, prevOrNextEdgeId);
        if (Double.isInfinite(weight))
            return Double.POSITIVE_INFINITY;

        if (roadClassEnc != null) {
            RoadClass roadClassEV = edge.get(roadClassEnc);
            if (avoidMotorway && roadClassEV == MOTORWAY || avoidTrunk && roadClassEV == TRUNK
                    || avoidPrimary && roadClassEV == PRIMARY || avoidSecondary && roadClassEV == SECONDARY
                    || avoidTertiary && roadClassEV == TERTIARY || avoidTrack && roadClassEV == TRACK
                    || avoidResidential && roadClassEV == RESIDENTIAL)
                return weight * avoidFactor;
        }

        if (roadEnvEnc != null) {
            RoadEnvironment roadEnvEV = edge.get(roadEnvEnc);
            if (avoidFerry && roadEnvEV == FERRY || avoidBridge && roadEnvEV == BRIDGE
                    || avoidFord && roadEnvEV == FORD || avoidTunnel && roadEnvEV == TUNNEL)
                return weight * avoidFactor;
        }

        if (avoidToll && edge.get(tollEnc) != Toll.NO)
            return weight * avoidFactor;
        return weight;
    }
}
