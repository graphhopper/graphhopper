package com.graphhopper.routing.util;

import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

import java.util.List;

import static com.graphhopper.routing.profiles.RoadClass.MOTORWAY;
import static com.graphhopper.routing.profiles.RoadClass.TRUNK;
import static com.graphhopper.routing.profiles.RoadEnvironment.*;

public class AvoidSnapEdgeFilter implements EdgeFilter {

    private final EnumEncodedValue<RoadEnvironment> reEnc;
    private final EnumEncodedValue<RoadClass> rcEnc;
    private final EdgeFilter filter;
    private boolean avoidMotorway = false, avoidTrunk;
    private boolean avoidTunnel, avoidBridge, avoidFerry, avoidFord;

    public AvoidSnapEdgeFilter(EdgeFilter filter, EnumEncodedValue<RoadClass> rcEnc,
                               EnumEncodedValue<RoadEnvironment> reEnc, List<String> avoidSnaps) {
        this.filter = filter;
        this.reEnc = reEnc;
        this.rcEnc = rcEnc;

        for (String key : avoidSnaps) {
            if ("motorway".equals(key)) {
                avoidMotorway = true;
                continue;
            } else if ("trunk".equals(key)) {
                avoidTrunk = true;
                continue;
            }

            RoadEnvironment rc = RoadEnvironment.find(key);
            if (rc == TUNNEL)
                avoidTunnel = true;
            else if (rc == BRIDGE)
                avoidBridge = true;
            else if (rc == FERRY)
                avoidFerry = true;
            else if (rc == FORD)
                avoidFord = true;
            else
                throw new IllegalArgumentException("Cannot find " + Parameters.Routing.AVOID_SNAP + ": " + key);
        }
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        return filter.accept(edgeState)
                && !(avoidMotorway && edgeState.get(rcEnc) == MOTORWAY)
                && !(avoidTrunk && edgeState.get(rcEnc) == TRUNK)
                && !(avoidTunnel && edgeState.get(reEnc) == TUNNEL)
                && !(avoidBridge && edgeState.get(reEnc) == BRIDGE)
                && !(avoidFord && edgeState.get(reEnc) == FORD)
                && !(avoidFerry && edgeState.get(reEnc) == FERRY);
    }
}
