package com.graphhopper.Penalties;

import com.google.common.annotations.VisibleForTesting;
import com.graphhopper.WeightingWithPenalties;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import static com.graphhopper.Penalties.MapConfigurations.*;

public class TurnPenalty extends Penalty {
    final static double SLIGHT_TURN_FACTOR = .7;
    final static double SHARP_TURN_FACTOR = 1.5;

    private final double penaltyForTurnRight;
    private final double penaltyForTurnLeft;
    private final double penaltyForTrafficLights;

    public TurnPenalty(HintsMap hintsMap) {
        Object[] turnPenalties = MapConfigurations.getTurnPenalties(hintsMap.get("map", "")).toArray();
        penaltyForTrafficLights = (Double) turnPenalties[STRAIGHT];
        penaltyForTurnRight = (Double) turnPenalties[RIGHT];
        penaltyForTurnLeft = (Double) turnPenalties[LEFT];
    }

    @VisibleForTesting
    public TurnPenalty(double penaltyForTurnRight, double penaltyForTurnLeft, double penaltyForTrafficLights) {
        this.penaltyForTurnRight = penaltyForTurnRight;
        this.penaltyForTurnLeft = penaltyForTurnLeft;
        this.penaltyForTrafficLights = penaltyForTrafficLights;
    }

    public double getPenalty(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId, WeightingWithPenalties.WayData from, WeightingWithPenalties.WayData to) {
        if (from != null && to != null) {
            return calculatePenalty(from, to);
        }
        return 0;
    }

    private double calculatePenalty(WeightingWithPenalties.WayData from, WeightingWithPenalties.WayData to) {
        double prevOrientation = Helper.ANGLE_CALC.calcOrientation(from.firstWayPointLat, from.firstWayPointLng, from.lastWayPointLat, from.lastWayPointLng, false);
        double orientation = Helper.ANGLE_CALC.calcOrientation(to.firstWayPointLat, to.firstWayPointLng, to.lastWayPointLat, to.lastWayPointLng, false);

        double delta = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation) - prevOrientation;
        double absDelta = Math.abs(delta);

        double penalty;
        if (absDelta < 0.2) { // 0.2 ~= 11° CONTINUE_ON_STREET
            penalty = penaltyForTrafficLights;
        } else if (absDelta < 0.8) { // 0.8 ~= 40° TURN_SLIGHT
            if (delta > 0) {
                penalty = penaltyForTurnLeft * SLIGHT_TURN_FACTOR;
            } else {
                penalty = penaltyForTurnRight * SLIGHT_TURN_FACTOR;
            }
        }  else if (absDelta < 1.8) { // 1.8 ~= 103° //TURN
            if (delta > 0) {
                penalty = penaltyForTurnLeft;
            } else {
                penalty = penaltyForTurnRight;
            }
        } else { //TURN_SHARP
            if (delta > 0) {
                penalty = penaltyForTurnLeft * SHARP_TURN_FACTOR;
            } else {
                penalty = penaltyForTurnRight * SHARP_TURN_FACTOR;
            }
        }
        return penalty;
    }
}
