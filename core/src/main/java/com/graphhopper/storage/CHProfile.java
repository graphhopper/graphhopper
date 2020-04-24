package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;

import static com.graphhopper.config.ProfileConfig.validateProfileName;

/**
 * Container to hold properties used for CH preparation Specifies all properties of a CH routing profile.
 *
 * @author easbar
 */
public class CHProfile {
    /**
     * will be used to store and identify the CH graph data on disk
     */
    private final String profileName;
    private final Weighting weighting;
    private final boolean edgeBased;

    public static CHProfile nodeBased(String profileName, Weighting weighting) {
        return new CHProfile(profileName, weighting, false);
    }

    public static CHProfile edgeBased(String profileName, Weighting weighting) {
        return new CHProfile(profileName, weighting, true);
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
        return profileName;
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

}
