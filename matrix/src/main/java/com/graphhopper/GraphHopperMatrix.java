package com.graphhopper;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.RouterMatrix;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.matrix.GHMatrixRequest;
import com.graphhopper.routing.matrix.GHMatrixResponse;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetailsBuilderFactory;

import java.util.Map;

public class GraphHopperMatrix extends GraphHopper {
    public GHMatrixResponse matrix(GHMatrixRequest request) {
        return createMatrixRouter().matrix(request);
    }

    protected RouterMatrix createMatrixRouter() {
        if (ghStorage == null || !fullyLoaded)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");
        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return doCreateRouter(ghStorage, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, createWeightingFactory(), chGraphs, landmarks);
    }

    protected RouterMatrix doCreateRouter(GraphHopperStorage ghStorage, LocationIndex locationIndex, Map<String, Profile> profilesByName,
                                          PathDetailsBuilderFactory pathBuilderFactory, TranslationMap trMap, RouterConfig routerConfig,
                                          WeightingFactory weightingFactory, Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        return new RouterMatrix(ghStorage.getBaseGraph(), ghStorage.getEncodingManager(), locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks
        );
    }

    public GraphHopperMatrix setGraphHopperLocation(String ghLocation) {
        super.setGraphHopperLocation(ghLocation);
        return this;
    }

    public GraphHopperMatrix setOSMFile(String osmFile) {
        super.setOSMFile(osmFile);
        return this;
    }

    public GraphHopperMatrix init(GraphHopperConfig ghConfig) {
        super.init(ghConfig);
        return this;
    }

    public GraphHopperMatrix importOrLoad() {
        super.importOrLoad();
        return this;
    }
}
