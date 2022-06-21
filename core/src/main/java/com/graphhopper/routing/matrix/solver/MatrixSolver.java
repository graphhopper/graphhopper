package com.graphhopper.routing.matrix.solver;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.matrix.GHMatrixRequest;
import com.graphhopper.routing.matrix.MatrixCalculator;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.DirectedEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Helper;

import java.util.Map;

public abstract class MatrixSolver {
    protected final GHMatrixRequest request;
    private final Map<String, Profile> profilesByName;
    protected Profile profile;
    protected Weighting weighting;
    protected final EncodedValueLookup lookup;

    public MatrixSolver(GHMatrixRequest request, Map<String, Profile> profilesByName, RouterConfig routerConfig, EncodedValueLookup lookup) {
        this.request = request;
        this.profilesByName = profilesByName;
        this.lookup = lookup;
    }

    public void checkRequest() {
        checkProfileSpecified();
    }

    private void checkProfileSpecified() {
        if (Helper.isEmpty(request.getProfile()))
            throw new IllegalArgumentException("You need to specify a profile to perform a routing request, see docs/core/profiles.md");
    }

    public void init() {
        profile = getProfile();
        weighting = createWeighting();
    }

    protected Profile getProfile() {
        Profile profile = profilesByName.get(request.getProfile());
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
        return profile;
    }

    protected abstract Weighting createWeighting();

    public EdgeFilter createSnapFilter() {
        return new DefaultSnapFilter(weighting, lookup.getBooleanEncodedValue(Subnetwork.key(profile.getName())));
    }

    public DirectedEdgeFilter createDirectedEdgeFilter() {
        BooleanEncodedValue inSubnetworkEnc = lookup.getBooleanEncodedValue(Subnetwork.key(profile.getName()));
        return (edgeState, reverse) -> !edgeState.get(inSubnetworkEnc) && Double.isFinite(weighting.calcEdgeWeightWithAccess(edgeState, reverse));
    }

    public abstract MatrixCalculator createMatrixCalculator(QueryGraph queryGraph);
}
