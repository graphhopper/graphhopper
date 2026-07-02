package com.graphhopper.routing.util

import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.Parameters

class SnapPreventionEdgeFilter(
    private val filter: EdgeFilter,
    private val rcEnc: EnumEncodedValue<RoadClass>,
    private val reEnc: EnumEncodedValue<RoadEnvironment>,
    snapPreventions: List<String>
) : EdgeFilter {

    private var avoidMotorway = false
    private var avoidTrunk = false
    private var avoidTunnel = false
    private var avoidBridge = false
    private var avoidFerry = false
    private var avoidFord = false

    init {
        for (roadClassOrRoadEnv in snapPreventions) {
            if ("motorway" == roadClassOrRoadEnv) {
                avoidMotorway = true
                continue
            } else if ("trunk" == roadClassOrRoadEnv) {
                avoidTrunk = true
                continue
            }

            when (RoadEnvironment.find(roadClassOrRoadEnv)) {
                RoadEnvironment.TUNNEL -> avoidTunnel = true
                RoadEnvironment.BRIDGE -> avoidBridge = true
                RoadEnvironment.FERRY -> avoidFerry = true
                RoadEnvironment.FORD -> avoidFord = true
                else -> throw IllegalArgumentException("Cannot find " + Parameters.Routing.SNAP_PREVENTION + ": " + roadClassOrRoadEnv)
            }
        }
    }

    override fun accept(edgeState: EdgeIteratorState): Boolean =
        filter.accept(edgeState)
                && !(avoidMotorway && edgeState.get(rcEnc) == RoadClass.MOTORWAY)
                && !(avoidTrunk && edgeState.get(rcEnc) == RoadClass.TRUNK)
                && !(avoidTunnel && edgeState.get(reEnc) == RoadEnvironment.TUNNEL)
                && !(avoidBridge && edgeState.get(reEnc) == RoadEnvironment.BRIDGE)
                && !(avoidFord && edgeState.get(reEnc) == RoadEnvironment.FORD)
                && !(avoidFerry && edgeState.get(reEnc) == RoadEnvironment.FERRY)
}
