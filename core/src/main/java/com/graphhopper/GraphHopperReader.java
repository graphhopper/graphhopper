package com.graphhopper;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.profiles.DefaultEncodedValueFactory;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.TranslationMap;

import java.io.File;
import java.util.List;

import static com.graphhopper.util.Helper.isEmpty;
import static com.graphhopper.util.Helper.toLowerCase;

public final class GraphHopperReader {

    private final TranslationMap trMap = new TranslationMap().doImport();
    private final EncodingManager encodingManager;
    private final GraphHopperStorage graphHopperStorage;
    private final CHPreparationHandler chHandler;
    private final LocationIndex locationIndex;

    public GraphHopperReader(GraphHopperWriter ghWriter) {
        this.encodingManager = ghWriter.getEncodingManager();
        this.graphHopperStorage = ghWriter.getGraphHopperStorage();
        this.chHandler = ghWriter.getCHPreparationHandler();
        this.locationIndex = ghWriter.getLocationIndex();
    }

    public GraphHopperReader(EncodingManager encodingManager, GraphHopperStorage graphHopperStorage, CHPreparationHandler chHandler, LocationIndex locationIndex) {
        this.encodingManager = encodingManager;
        this.graphHopperStorage = graphHopperStorage;
        this.chHandler = chHandler;
        this.locationIndex = locationIndex;
    }

    public static GraphHopperReader read(String graphCache, GraphConfig config, List<CHProfile> chProfiles) {
        if (isEmpty(graphCache))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        File tmpFileOrFolder = new File(graphCache);
        if (!tmpFileOrFolder.isDirectory() && tmpFileOrFolder.exists())
            throw new IllegalArgumentException("GraphHopperLocation cannot be an existing file. Has to be either non-existing or a folder.");

        GHDirectory dir = new GHDirectory(graphCache, config.getDAType());
        EncodingManager encodingManager = EncodingManager.create(new DefaultEncodedValueFactory(), new DefaultFlagEncoderFactory(), graphCache);
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(dir, encodingManager, config.hasElevation(),
                encodingManager.needsTurnCostsSupport(), config.getDefaultSegmentSize());
        graphHopperStorage.addCHGraphs(chProfiles);
        if (!graphHopperStorage.loadExisting())
            throw new RuntimeException("Cannot load Graph from " + graphCache);

        LocationIndexTree locationIndex = new LocationIndexTree(graphHopperStorage, dir);
        if (!locationIndex.loadExisting())
            throw new RuntimeException("Cannot load LocationIndex from " + graphCache);

        CHPreparationHandler chHandler = CHPreparationHandler.read(graphHopperStorage, chProfiles);
        return new GraphHopperReader(encodingManager, graphHopperStorage, chHandler, locationIndex);
    }

    // TODO NOW other customization mechanism than inheritance needed
    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        RoutingAlgorithmFactory routingAlgorithmFactory = new RoutingAlgorithmFactorySimple();
        if (chHandler.isEnabled())
            return chHandler.getDecoratedAlgorithmFactory(routingAlgorithmFactory, map);

        return routingAlgorithmFactory;
    }

    // TODO NOW other customization mechanism than inheritance needed
    public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder, TurnCostProvider turnCostProvider) {
        String weightingStr = toLowerCase(hintsMap.getWeighting());
        if ("shortest".equalsIgnoreCase(weightingStr)) {
            return new ShortestWeighting(encoder, turnCostProvider);
        } else if ("fastest".equalsIgnoreCase(weightingStr) || weightingStr.isEmpty()) {
            if (encoder.supports(PriorityWeighting.class))
                return new PriorityWeighting(encoder, hintsMap, turnCostProvider);

            return new FastestWeighting(encoder, hintsMap, turnCostProvider);
        }
        throw new IllegalArgumentException("weighting " + weightingStr + " not supported");
    }

    public GHResponse route(GHRequest request) {
        GHResponse ghRsp = new GHResponse();

        String vehicle = request.getVehicle();
        FlagEncoder encoder = vehicle.isEmpty() ? encodingManager.fetchEdgeEncoders().get(0) : encodingManager.getEncoder(vehicle);
        request.setAlgorithm(request.getAlgorithm().isEmpty() ? "astarbi" : request.getAlgorithm());

        Weighting weighting;
        Graph graph = graphHopperStorage;
        RoutingAlgorithmFactory algorithmFactory = getAlgorithmFactory(request.getHints());
        if (chHandler.isEnabled()) {
            if (algorithmFactory instanceof CHRoutingAlgorithmFactory) {
                CHProfile chProfile = ((CHRoutingAlgorithmFactory) algorithmFactory).getCHProfile();
                weighting = chProfile.getWeighting();
                graph = graphHopperStorage.getCHGraph(chProfile);
            } else {
                throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + algorithmFactory);
            }
        } else {
            weighting = createWeighting(request.getHints(), encoder, TurnCostProvider.NO_TURN_COST_PROVIDER);
        }

        ViaRoutingTemplate routingTemplate = new ViaRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        List<QueryResult> qResults = routingTemplate.lookup(request.getPoints());
        QueryGraph queryGraph = QueryGraph.lookup(graph, qResults);

        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(request.getAlgorithm()).traversalMode(TraversalMode.NODE_BASED).weighting(weighting).
                hints(request.getHints()).
                build();

        routingTemplate.calcPaths(queryGraph, algorithmFactory, algoOpts);
        PathMerger pathMerger = new PathMerger(queryGraph.getBaseGraph(), weighting);
        routingTemplate.finish(pathMerger, trMap.getWithFallBack(request.getLocale()));
        return ghRsp;
    }

    public void close() {
        locationIndex.close();
        graphHopperStorage.close();
    }
}
