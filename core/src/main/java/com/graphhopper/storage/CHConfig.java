package com.graphhopper.storage;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Helper;

import static com.graphhopper.config.Profile.validateProfileName;

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
    // ORS-GH MOD START
    // CALT add member variable
    public static final String TYPE_CORE = "core";
    private String type = "ch";  // Either "ch" or "core"
    // ORS-GH MOD END

    public static CHConfig nodeBased(String chGraphName, Weighting weighting) {
        return new CHConfig(chGraphName, weighting, false);
    }

    public static CHConfig edgeBased(String chGraphName, Weighting weighting) {
        return new CHConfig(chGraphName, weighting, true);
    }

    public CHConfig(String chGraphName, Weighting weighting, boolean edgeBased) {
        validateProfileName(chGraphName);
        this.chGraphName = chGraphName;
        this.weighting = weighting;
        this.edgeBased = edgeBased;
    }

    public CHConfig(String chGraphName, Weighting weighting, boolean edgeBased, String type) {
        this(chGraphName, weighting, edgeBased);
        this.type = type;
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

    // ORS-GH MOD START
    public String getType() {
        return type;
    }
    // ORS-GH MOD END

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
