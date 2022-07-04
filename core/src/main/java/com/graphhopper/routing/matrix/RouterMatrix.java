package com.graphhopper.routing.matrix;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.Router;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.ViaRouting;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.matrix.DistanceMatrix;
import com.graphhopper.routing.matrix.GHMatrixRequest;
import com.graphhopper.routing.matrix.GHMatrixResponse;
import com.graphhopper.routing.matrix.MatrixCalculator;
import com.graphhopper.routing.matrix.solver.CHMatrixSolver;
import com.graphhopper.routing.matrix.solver.MatrixSolver;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DirectedEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouterMatrix extends Router {
    public RouterMatrix(BaseGraph graph, EncodingManager encodingManager, LocationIndex locationIndex, Map<String, Profile> profilesByName, PathDetailsBuilderFactory pathDetailsBuilderFactory, TranslationMap translationMap, RouterConfig routerConfig, WeightingFactory weightingFactory, Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        super(graph, encodingManager, locationIndex, profilesByName, pathDetailsBuilderFactory, translationMap, routerConfig, weightingFactory, chGraphs, landmarks);
    }

    public GHMatrixResponse matrix(GHMatrixRequest request) {

        Profile profile = profilesByName.get(request.getProfile());
        RoutingCHGraph chGraph = chGraphs.get(profile.getName());


        MatrixSolver solver = createMatrixSolver(request);
        solver.checkRequest();
        solver.init();

        GHMatrixResponse ghMtxRsp = new GHMatrixResponse();

        DirectedEdgeFilter directedEdgeFilter = solver.createDirectedEdgeFilter();
        // For the usage of the Matrix use case, we don't need neither pointHints, SnapPreventions or Headings.
        List<Double> headings = new ArrayList<>();
        List<String> pointHints = new ArrayList<>();
        List<String> snapPreventions = new ArrayList<>();
        List<Snap> origins = ViaRouting.lookupMatrix(encodingManager, request.getOrigins(), solver.createSnapFilter(), locationIndex,
                snapPreventions, pointHints, directedEdgeFilter, headings);

        List<Snap> destinations = ViaRouting.lookupMatrix(encodingManager, request.getDestinations(), solver.createSnapFilter(), locationIndex,
                snapPreventions, pointHints, directedEdgeFilter, headings);

        // (base) query graph used to resolve headings, curbsides etc. this is not necessarily the same thing as
        // the (possibly implementation specific) query graph used by PathCalculator
        List<Snap> allSnaps = new ArrayList<>(origins);
        allSnaps.addAll(destinations);
        QueryGraph queryGraph = QueryGraph.create(graph, allSnaps);


        MatrixCalculator matrixCalculator = solver.createMatrixCalculator(queryGraph);
        DistanceMatrix matrix = matrixCalculator.calcMatrix(origins, destinations);
        ghMtxRsp.setMatrix(matrix);

        return ghMtxRsp;
    }

    protected MatrixSolver createMatrixSolver(GHMatrixRequest request) {
        // TODO For now MatrixSolver is just implemented with CHMatrixSolver
        return new CHMatrixSolver(request, profilesByName, routerConfig, encodingManager, chGraphs);
    }
}
