package com.graphhopper.routing.matrix.solver;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.matrix.CHMatrixCalculator;
import com.graphhopper.routing.matrix.GHMatrixRequest;
import com.graphhopper.routing.matrix.MatrixCalculator;
import com.graphhopper.routing.matrix.algorithm.MatrixRoutingAlgorithmFactory;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import java.util.Map;

import static com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

public class CHMatrixSolver extends MatrixSolver {
    private final Map<String, RoutingCHGraph> chGraphs;
    private final RouterConfig routerConfig;

    public CHMatrixSolver(GHMatrixRequest request, Map<String, Profile> profilesByName,
                          RouterConfig routerConfig, EncodedValueLookup lookup, Map<String, RoutingCHGraph> chGraphs) {
        super(request, profilesByName, routerConfig, lookup);
        this.chGraphs = chGraphs;
        this.routerConfig = routerConfig;
    }

    @Override
    public void checkRequest() {
        super.checkRequest();

        // TODO Check algorithm is one of the ones supported by Matrix feature
        if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm()))
            throw new IllegalArgumentException("algorithm=round_trip cannot be used with CH");
    }

    @Override
    protected Weighting createWeighting() {
        // the request hints are ignored for CH as we cannot change the profile after the preparation like this.
        // the weighting here needs to be the same as the one we later use for CHPathCalculator and as it was
        // used for the preparation
        return getRoutingCHGraph(profile.getName()).getWeighting();
    }

    @Override
    public MatrixCalculator createMatrixCalculator(QueryGraph queryGraph) {

        int maxVisitedNodes = getMaxVisitedNodes();
        PMap opts = new PMap(request.getHints())
                .putObject(ALGORITHM, request.getAlgorithm())
                .putObject(MAX_VISITED_NODES, maxVisitedNodes);

        AlgorithmOptions algoOpts = getAlgoOpts().setHints(opts);


        return new CHMatrixCalculator(new MatrixRoutingAlgorithmFactory(getRoutingCHGraph(profile.getName()),
                queryGraph), algoOpts);
    }

    private int getMaxVisitedNodes() {
        return request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes());
    }

    public AlgorithmOptions getAlgoOpts() {
        AlgorithmOptions algoOpts = new AlgorithmOptions().
                setAlgorithm(request.getAlgorithm()).
                setTraversalMode(profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED).
                setMaxVisitedNodes(getMaxVisitedNodes()).
                setHints(request.getHints());

        // use A* for round trips
        if (ROUND_TRIP.equalsIgnoreCase(request.getAlgorithm())) {
            algoOpts.setAlgorithm(Parameters.Algorithms.ASTAR_BI);
            algoOpts.getHints().putObject(Parameters.Algorithms.AStarBi.EPSILON, 2);
        }
        return algoOpts;
    }

    private RoutingCHGraph getRoutingCHGraph(String profileName) {
        RoutingCHGraph chGraph = chGraphs.get(profileName);
        if (chGraph == null)
            throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profileName + "'" +
                    "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                    "\navailable CH profiles: " + chGraphs.keySet());
        return chGraph;
    }
}
