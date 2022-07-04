package com.graphhopper.routing.matrix;

import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

public class GHMatrixRequest {
    // GHMatrixRequest doesn't have any hint because it is just possible to work just for now with CH, so the profile
    // settings cannot be changed. On the other hand, for now we don't want to open the API to be configurable, to reduce the complexity
    // of the flow.

    private String algo = Parameters.Algorithms.DIJKSTRA_MANY_TO_MANY;
    private String profile = "";
    private final List<GHPoint> origins = new ArrayList<>();
    private final List<GHPoint> destinations = new ArrayList<>();
    private final PMap hints = new PMap();

    /**
     * One or more locations to use as the starting point for calculating travel distance and time.
     */
    public List<GHPoint> getOrigins() {
        return origins;
    }

    public void setOrigins(List<GHPoint> origins) {
        this.origins.addAll(origins);
    }

    /**
     * One or more locations to use as the finishing point for calculating travel distance and time.
     */
    public List<GHPoint> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<GHPoint> destinations) {
        this.destinations.addAll(destinations);
    }

    public String getAlgorithm() {
        return algo;
    }

    public GHMatrixRequest setAlgorithm(String algo) {
        if (algo != null)
            this.algo = Helper.camelCaseToUnderScore(algo);
        return this;
    }

    public String getProfile() {
        return profile;
    }

    public GHMatrixRequest setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public PMap getHints() {
        return hints;
    }

    public GHMatrixRequest putHint(String fieldName, Object value) {
        this.hints.putObject(fieldName, value);
        return this;
    }
}
