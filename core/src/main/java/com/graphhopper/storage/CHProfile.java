package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.graphhopper.config.ProfileConfig.validateProfileName;

/**
 * Specifies all properties of a CH routing profile. Generally these properties cannot be changed after the CH
 * pre-processing is finished and are stored on disk along with the prepared graph data.
 *
 * @author easbar
 */
public class CHProfile {
    private final String profileName;
    private final Weighting weighting;
    private final boolean edgeBased;

    public static CHProfile nodeBased(Weighting weighting) {
        return nodeBased(defaultName(weighting, false), weighting);
    }

    public static CHProfile nodeBased(String profileName, Weighting weighting) {
        return new CHProfile(profileName, weighting, false);
    }

    public static CHProfile edgeBased(Weighting weighting) {
        return edgeBased(defaultName(weighting, true), weighting);
    }

    public static CHProfile edgeBased(String profileName, Weighting weighting) {
        return new CHProfile(profileName, weighting, true);
    }

    public CHProfile(Weighting weighting, boolean edgeBased) {
        this(defaultName(weighting, edgeBased), weighting, edgeBased);
    }

    public CHProfile(String profileName, Weighting weighting, boolean edgeBased) {
        validateProfileName(profileName);
        this.profileName = profileName;
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
        return profileName;
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

    public String getName() {
        return profileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CHProfile chProfile = (CHProfile) o;
        return getName().equals(chProfile.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    private static String defaultName(Weighting weighting, boolean edgeBased) {
        String result = AbstractWeighting.weightingToFileName(weighting);
        // this is how we traditionally named the files, something like 'fastest_edge_utc40'
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
}
