package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.AccessControl;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

import java.util.List;

import static com.graphhopper.routing.ev.AccessControl.FULL;
import static com.graphhopper.routing.ev.RoadClass.MOTORWAY;
import static com.graphhopper.routing.ev.RoadClass.TRUNK;
import static com.graphhopper.routing.ev.RoadEnvironment.*;

public class SnapPreventionEdgeFilter implements EdgeFilter {

    private final EnumEncodedValue<RoadEnvironment> reEnc;
    private final EnumEncodedValue<RoadClass> rcEnc;
    private final EnumEncodedValue<AccessControl> acEnc;
    private final EdgeFilter filter;
    private boolean avoidMotorway = false, avoidTrunk;
    private boolean avoidTunnel, avoidBridge, avoidFerry, avoidFord;
    private boolean avoidAccessControlFull = false;

    public SnapPreventionEdgeFilter(EdgeFilter filter, EnumEncodedValue<RoadClass> rcEnc,
                                    EnumEncodedValue<RoadEnvironment> reEnc, EnumEncodedValue<AccessControl> acEnc,
                                    List<String> snapPreventions) {
        this.filter = filter;
        this.reEnc = reEnc;
        this.rcEnc = rcEnc;
        this.acEnc = acEnc;

        for (String roadClassOrRoadEnv : snapPreventions) {
            if ("motorway".equals(roadClassOrRoadEnv)) {
                avoidMotorway = true;
                continue;
            } else if ("trunk".equals(roadClassOrRoadEnv)) {
                avoidTrunk = true;
                continue;
            } else if ("access_control_restricted".equals(roadClassOrRoadEnv)) {
                avoidAccessControlFull = true;
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
                && !(avoidMotorway && edgeState.get(rcEnc) == MOTORWAY)
                && !(avoidTrunk && edgeState.get(rcEnc) == TRUNK)
                && !(avoidTunnel && edgeState.get(reEnc) == TUNNEL)
                && !(avoidBridge && edgeState.get(reEnc) == BRIDGE)
                && !(avoidFord && edgeState.get(reEnc) == FORD)
                && !(avoidFerry && edgeState.get(reEnc) == FERRY)
                && !(avoidAccessControlFull && edgeState.get(acEnc) == FULL);
    }
}
