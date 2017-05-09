package com.graphhopper.util;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.storage.GraphHopperStorage;
import java.util.concurrent.ThreadLocalRandom;

public class EngineWarmUp {
    /**
     * 'Warm up' virtual machine so initial route calculation is not slower than consequent. To be used
     * on a less powerful machine like Android or Raspberry Pi.
     */
    public static void warmUp(GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();
        if (ghStorage == null)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");
        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");

        if (ghStorage.isCHPossible())
            warmUpCHSubNetwork(graphHopper, iterations);
        else
            warmUpNonCHSubNetwork(graphHopper, iterations);
    }

    private static void warmUpCHSubNetwork(GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();

        for (int i = 0; i < iterations; i++) {
            int startNode = ThreadLocalRandom.current().nextInt(0, graphHopper.getMaxVisitedNodes() + 1);
            int endNode = ThreadLocalRandom.current().nextInt(0, graphHopper.getMaxVisitedNodes() + 1);

            double fromLatitude = ghStorage.getNodeAccess().getLatitude(startNode);
            double fromLongitude = ghStorage.getNodeAccess().getLongitude(startNode);
            double toLatitude = ghStorage.getNodeAccess().getLatitude(endNode);
            double toLongitude = ghStorage.getNodeAccess().getLongitude(endNode);

            GHRequest request = new GHRequest(fromLatitude, fromLongitude, toLatitude, toLongitude);
            graphHopper.route(request);
        }
    }

    private static void warmUpNonCHSubNetwork(GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();

        BreadthFirstSearch bfs = new BreadthFirstSearch();
        for (int i = 0; i < iterations; i++) {
            int startNode = ThreadLocalRandom.current().nextInt(0, graphHopper.getMaxVisitedNodes() + 1);
            bfs.start(ghStorage.getBaseGraph().createEdgeExplorer(), startNode);
        }
    }
}
