package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

import java.util.List;

import static com.graphhopper.routing.ev.RoadClass.FOOTWAY;
import static com.graphhopper.routing.ev.RoadClass.MOTORWAY;
import static com.graphhopper.routing.ev.RoadClass.TRUNK;
import static com.graphhopper.routing.ev.RoadEnvironment.*;

public class SnapPreventionEdgeFilter implements EdgeFilter {

    private final EnumEncodedValue<RoadEnvironment> reEnc;
    private final EnumEncodedValue<RoadClass> rcEnc;
    private final EdgeFilter filter;
    private boolean avoidMotorway = false, avoidTrunk;
    private boolean avoidTunnel, avoidBridge, avoidFerry, avoidFord, avoidFootway;

    public SnapPreventionEdgeFilter(EdgeFilter filter, EnumEncodedValue<RoadClass> rcEnc,
                                    EnumEncodedValue<RoadEnvironment> reEnc, List<String> snapPreventions) {
        this.filter = filter;
        this.reEnc = reEnc;
        this.rcEnc = rcEnc;

        for (String roadClassOrRoadEnv : snapPreventions) {
            switch (roadClassOrRoadEnv) {
                case "motorway":
                    avoidMotorway = true;
                    continue;
                case "trunk":
                    avoidTrunk = true;
                    continue;
                case "footway":
                    avoidFootway = true;
                    continue;
            }

            RoadEnvironment rc = RoadEnvironment.find(roadClassOrRoadEnv);
            if (rc == TUNNEL)
                avoidTunnel = true;
            else if (rc == BRIDGE)
                avoidBridge = true;
            else if (rc == FERRY)
                avoidFerry = true;
            else if (rc == FORD)
                avoidFord = true;
            else
                throw new IllegalArgumentException("Cannot find " + Parameters.Routing.SNAP_PREVENTION + ": " + roadClassOrRoadEnv);
        }
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        return filter.accept(edgeState)
                && !(avoidFootway && edgeState.get(rcEnc) == FOOTWAY)
                && !(avoidMotorway && edgeState.get(rcEnc) == MOTORWAY)
                && !(avoidTrunk && edgeState.get(rcEnc) == TRUNK)
                && !(avoidTunnel && edgeState.get(reEnc) == TUNNEL)
                && !(avoidBridge && edgeState.get(reEnc) == BRIDGE)
                && !(avoidFord && edgeState.get(reEnc) == FORD)
                && !(avoidFerry && edgeState.get(reEnc) == FERRY);
    }
}
