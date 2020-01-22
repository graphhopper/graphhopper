package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Specifies all properties of a CH routing profile. Generally these properties cannot be changed after the CH
 * pre-processing is finished and are stored on disk along with the prepared graph data.
 *
 * @author easbar
 */
public class CHProfile {
    private final Weighting weighting;
    private final boolean edgeBased;

    public static CHProfile nodeBased(Weighting weighting) {
        // todonow: should we do such a check (check if the weighting has turn costs ?)
//        if (!edgeBased && uTurnCosts != INFINITE_U_TURN_COSTS) {
//            throw new IllegalArgumentException("Finite u-turn costs are only allowed for edge-based CH");
//        }
        return new CHProfile(weighting, TraversalMode.NODE_BASED);
    }

    public static CHProfile edgeBased(Weighting weighting) {
        // todonow: should we check/assure that the weighting must have some valid turn costs ?
        return new CHProfile(weighting, TraversalMode.EDGE_BASED);
    }

    public static List<CHProfile> createProfilesForWeightings(Collection<? extends Weighting> weightings) {
        List<CHProfile> result = new ArrayList<>(weightings.size());
        for (Weighting weighting : weightings) {
            result.add(nodeBased(weighting));
        }
        return result;
    }

    public CHProfile(Weighting weighting, TraversalMode traversalMode) {
        this(weighting, traversalMode.isEdgeBased());
    }

    public CHProfile(Weighting weighting, boolean edgeBased) {
        this.weighting = weighting;
        this.edgeBased = edgeBased;
    }

    public Weighting getWeighting() {
        return weighting;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

    public TraversalMode getTraversalMode() {
        return edgeBased ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
    }

    public String toFileName() {
        // todonow: this must go to turn cost provider ?
//        return AbstractWeighting.weightingToFileName(weighting) + "_" + (edgeBased ? ("edge_utc" + uTurnCosts) : "node");
        String result = AbstractWeighting.weightingToFileName(weighting);
//        + "_" + (edgeBased ? "edge" : "node");
        // turn this into legacy filename:
        result = result.replaceAll("u_turn_costs=", "utc");
        String[] parts = result.split("_");
        result = parts[0] + "_" + parts[1] + "_" + (edgeBased ? "edge" : "node");
        if (parts.length > 2) {
            result += "_" + parts[2];
        }
        return result;
    }

    public String toString() {
        // todonow: do we need this u_turn_costs string we previously had somewhere ? get it in turn cost provider ?
//        return weighting + "|edge_based=" + edgeBased + "|u_turn_costs=" + uTurnCosts;
        return weighting + "|edge_based=" + edgeBased;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CHProfile chProfile = (CHProfile) o;
        return edgeBased == chProfile.edgeBased &&
                Objects.equals(weighting, chProfile.weighting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weighting, edgeBased);
    }
}
