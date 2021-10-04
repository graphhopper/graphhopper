package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;

import java.util.Objects;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;

/**
 * Specifies all properties of a CH routing profile. Generally these properties cannot be changed after the CH
 * pre-processing is finished and are stored on disk along with the prepared graph data.
 *
 * @author easbar
 */
@Deprecated // TODO ORS: see config/CHProfile. Should we add a CaltProfile there?
public class CHProfile {
    private final Weighting weighting;
    private final boolean edgeBased;
    private final int uTurnCosts;
    // ORS-GH MOD START
    // CALT add member variable
    public static final String TYPE_CORE = "core";
    private String type = "ch";  // Either "ch" or "core"
    // ORS-GH MOD END

    public static CHProfile nodeBased(Weighting weighting) {
        return new CHProfile(weighting, TraversalMode.NODE_BASED, INFINITE_U_TURN_COSTS);
    }

    public static CHProfile edgeBased(Weighting weighting, int uTurnCosts) {
        return new CHProfile(weighting, TraversalMode.EDGE_BASED, uTurnCosts);
    }

    public CHProfile(Weighting weighting, TraversalMode traversalMode, int uTurnCosts) {
        this(weighting, traversalMode.isEdgeBased(), uTurnCosts);
    }

    // ORS-GH MOD START
    public CHProfile(Weighting weighting, TraversalMode traversalMode, int uTurnCosts, String type) {
        this(weighting, traversalMode.isEdgeBased(), uTurnCosts);
        this.type = type;
    }
    // ORS-GH MOD END

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

    // ORS-GH MOD START
    public String getType() {
        return type;
    }
    // ORS-GH MOD END

    @Deprecated // TODO ORS (minor): remove old unused code
    public String toFileName() {
        //return AbstractWeighting.weightingToFileName(weighting) + "_" + (edgeBased ? ("edge_utc" + uTurnCosts) : "node");
        throw new RuntimeException("weightingToFileName has been removed from GH3");
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
        // ORS-GH MOD START
                type == chProfile.type &&
        // ORS-GH MOD END
                Objects.equals(weighting, chProfile.weighting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weighting, edgeBased, uTurnCosts, type);
    }
}
