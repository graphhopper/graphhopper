package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;

import java.util.Objects;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;

/**
 * Specifies all properties of a CH routing profile. Generally these properties cannot be changed after the CH
 * pre-processing is finished and are stored on disk along with the prepared graph data.
 *
 * @author easbar
 */
public class CHProfile {
    private final Weighting weighting;
    private final boolean edgeBased;
    private final int uTurnCosts;

    public static CHProfile nodeBased(Weighting weighting) {
        return new CHProfile(weighting, TraversalMode.NODE_BASED, INFINITE_U_TURN_COSTS);
    }

    public static CHProfile edgeBased(Weighting weighting, int uTurnCosts) {
        return new CHProfile(weighting, TraversalMode.EDGE_BASED, uTurnCosts);
    }

    public CHProfile(Weighting weighting, TraversalMode traversalMode, int uTurnCosts) {
        this(weighting, traversalMode.isEdgeBased(), uTurnCosts);
    }

    /**
     * @param uTurnCosts the costs of a u-turn in seconds, for {@link TurnWeighting#INFINITE_U_TURN_COSTS} the u-turn costs
     *                   will be infinite
     */
    public CHProfile(Weighting weighting, boolean edgeBased, int uTurnCosts) {
        if (!edgeBased && uTurnCosts != INFINITE_U_TURN_COSTS) {
            throw new IllegalArgumentException("Finite u-turn costs are only allowed for edge-based CH");
        }
        this.weighting = weighting;
        this.edgeBased = edgeBased;
        if (uTurnCosts < 0 && uTurnCosts != INFINITE_U_TURN_COSTS) {
            throw new IllegalArgumentException("u-turn costs must be positive, or equal to " + INFINITE_U_TURN_COSTS + " (=infinite costs)");
        }
        this.uTurnCosts = uTurnCosts < 0 ? INFINITE_U_TURN_COSTS : uTurnCosts;
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

    public double getUTurnCosts() {
        return uTurnCosts < 0 ? Double.POSITIVE_INFINITY : uTurnCosts;
    }

    /**
     * Use this method when u-turn costs are used to check CHProfile equality
     */
    public int getUTurnCostsInt() {
        return uTurnCosts;
    }

    public TraversalMode getTraversalMode() {
        return edgeBased ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
    }

    public String toFileName() {
        return AbstractWeighting.weightingToFileName(weighting) + "_" + (edgeBased ? ("edge_utc" + uTurnCosts) : "node");
    }

    public String toString() {
        return weighting + "|edge_based=" + edgeBased + "|u_turn_costs=" + uTurnCosts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CHProfile chProfile = (CHProfile) o;
        return edgeBased == chProfile.edgeBased &&
                uTurnCosts == chProfile.uTurnCosts &&
                Objects.equals(weighting, chProfile.weighting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weighting, edgeBased, uTurnCosts);
    }
}
