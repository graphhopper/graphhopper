package com.graphhopper.routing.weighting;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.profiles.RoadClass.*;
import static com.graphhopper.routing.profiles.RoadEnvironment.*;

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
    private ObjectEncodedValue tollEnc;
    private ObjectEncodedValue roadEnvEnc;
    private ObjectEncodedValue roadClassEnc;
    private double avoidFactor;

    public AvoidWeighting(FlagEncoder encoder, PMap map) {
        super(encoder, map);

        String avoidStr = map.get("avoid", "");
        if (encoder.hasEncodedValue(RoadEnvironment.KEY)) {
            avoidFerry = avoidStr.contains("ferry");
            avoidBridge = avoidStr.contains("bridge");
            avoidFord = avoidStr.contains("ford");
            avoidTunnel = avoidStr.contains("tunnel");
            roadEnvEnc = encoder.getObjectEncodedValue(RoadEnvironment.KEY);
        }

        if (encoder.hasEncodedValue(Toll.KEY)) {
            avoidToll = avoidStr.contains("toll");
            tollEnc = encoder.getObjectEncodedValue(Toll.KEY);
        }

        if (encoder.hasEncodedValue(RoadClass.KEY)) {
            avoidMotorway = avoidStr.contains("motorway");
            avoidTrunk = avoidStr.contains("trunk");
            avoidPrimary = avoidStr.contains("primary");
            avoidSecondary = avoidStr.contains("secondary");
            avoidTertiary = avoidStr.contains("tertiary");
            avoidTrack = avoidStr.contains("track");
            avoidResidential = avoidStr.contains("residential");
            roadClassEnc = encoder.getObjectEncodedValue(RoadClass.KEY);
        }

        // can be used for preferring too
        avoidFactor = Math.min(10, Math.max(0.1, map.getDouble("avoid.factor", 10)));
    }

    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double weight = super.calcWeight(edge, reverse, prevOrNextEdgeId);
        if (Double.isInfinite(weight))
            return Double.POSITIVE_INFINITY;

        if (roadClassEnc != null) {
            IndexBased roadClassEV = edge.get(roadClassEnc);
            if (avoidMotorway && roadClassEV == MOTORWAY || avoidTrunk && roadClassEV == TRUNK
                    || avoidPrimary && roadClassEV == PRIMARY || avoidSecondary && roadClassEV == SECONDARY
                    || avoidTertiary && roadClassEV == TERTIARY || avoidTrack && roadClassEV == TRACK
                    || avoidResidential && roadClassEV == RESIDENTIAL)
                return weight * avoidFactor;
        }

        if (roadEnvEnc != null) {
            IndexBased roadEnvEV = edge.get(roadEnvEnc);
            if (avoidFerry && roadEnvEV == FERRY || avoidBridge && roadEnvEV == BRIDGE
                    || avoidFord && roadEnvEV == FORD || avoidTunnel && roadEnvEV == TUNNEL)
                return weight * avoidFactor;
        }

        if (avoidToll && edge.get(tollEnc) != Toll.NO)
            return weight * avoidFactor;
        return weight;
    }
}
