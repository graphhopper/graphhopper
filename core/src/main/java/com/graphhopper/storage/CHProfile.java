package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return new CHProfile(weighting, TraversalMode.NODE_BASED);
    }

    public static CHProfile edgeBased(Weighting weighting) {
        return new CHProfile(weighting, TraversalMode.EDGE_BASED);
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
        String result = AbstractWeighting.weightingToFileName(weighting);
        // keeping legacy file names for now, like fastest_edge_utc40 (instead of fastest_40_edge), because we will
        // most likely use profile names soon: #1708
        Pattern pattern = Pattern.compile("-?\\d+");
        Matcher matcher = pattern.matcher(result);
        if (matcher.find()) {
            String turnCostPostfix = matcher.group();
            result = matcher.replaceAll("");
            result += edgeBased ? "edge" : "node";
            result += "_utc" + turnCostPostfix;
        } else {
            result += edgeBased ? "_edge" : "_node";
        }
        return result;
    }

    public String toString() {
        String result = weighting.toString();
        Pattern pattern = Pattern.compile("\\|u_turn_costs=-?\\d+");
        Matcher matcher = pattern.matcher(result);
        if (matcher.find()) {
            String uTurnCostPostFix = matcher.group();
            result = matcher.replaceAll("");
            result += "|edge_based=" + edgeBased + uTurnCostPostFix;
        } else {
            result += "|edge_based=" + edgeBased;
        }
        return result;
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
