package com.graphhopper.util;

import com.graphhopper.storage.GraphHopperStorage;
import java.util.Random;

public class EngineWarmUp {
    /**
     * 'Warm up' virtual machine so initial route calculation is not slower than consequent. To be used
     * on a less powerful machine like Android or Raspberry Pi.
     */
    public static void warmUp(GraphHopperStorage ghStorage, int iterations) {
        if (ghStorage == null)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");
        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");
        if(ghStorage.isCHPossible() == false)
            throw new IllegalStateException("Cannot warm up non CH network");

        BreadthFirstSearch bfs = new BreadthFirstSearch();
        final Random rand = new Random();

        for (int i = 0; i < iterations; i++) {
            int startNode = rand.nextInt(ghStorage.getBaseGraph().getNodes() + 1);
            bfs.start(ghStorage.getBaseGraph().createEdgeExplorer(), startNode);
        }
    }
}
