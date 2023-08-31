package com.graphhopper.storage;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;

/**
 * Container to hold properties used for CH preparation Specifies all properties of a CH routing profile.
 *
 * @author easbar
 */
public class CHConfig {
    /**
     * will be used to store and identify the CH graph data on disk
     */
    private final String chGraphName;
    private final Weighting weighting;
    private final boolean edgeBased;

    public static CHConfig nodeBased(String chGraphName, Weighting weighting) {
        return new CHConfig(chGraphName, weighting, false);
    }

    public static CHConfig edgeBased(String chGraphName, Weighting weighting) {
        return new CHConfig(chGraphName, weighting, true);
    }

    public CHConfig(String chGraphName, Weighting weighting, boolean edgeBased) {
        Profile.validateProfileName(chGraphName);
        this.chGraphName = chGraphName;
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
        return chGraphName;
    }

    public String toString() {
        return chGraphName;
    }

    public String getName() {
        return chGraphName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CHConfig chConfig = (CHConfig) o;
        return getName().equals(chConfig.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
