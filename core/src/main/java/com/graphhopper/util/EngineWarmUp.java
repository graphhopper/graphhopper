/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.storage.GraphHopperStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * This class provides helper methods to reduce calculation speed difference of the first requests. Necessary
 * to force the JIT kicking in and e.g. for MMAP config to load the necessary data into RAM.
 */
public class EngineWarmUp {
    private static Logger LOGGER = LoggerFactory.getLogger(EngineWarmUp.class);

    /**
     * Do the 'warm up' for the specified GraphHopper instance.
     *
     * @param iterations the 'intensity' of the warm up procedure
     */
    public static void warmUp(GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();
        if (ghStorage == null)
            throw new IllegalArgumentException("The storage of GraphHopper must not be empty");

        try {
            if (ghStorage.isCHPossible())
                warmUpCHSubNetwork(graphHopper, iterations);
            else
                warmUpNonCHSubNetwork(graphHopper, iterations);
        } catch (Exception ex) {
            LOGGER.warn("Problem while sending warm up queries", ex);
        }
    }

    private static void warmUpCHSubNetwork(GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();
        Random rand = new Random(0);

        for (int i = 0; i < iterations; i++) {
            int startNode = rand.nextInt(graphHopper.getMaxVisitedNodes() + 1);
            int endNode = rand.nextInt(graphHopper.getMaxVisitedNodes() + 1);

            double fromLatitude = ghStorage.getNodeAccess().getLatitude(startNode);
            double fromLongitude = ghStorage.getNodeAccess().getLongitude(startNode);
            double toLatitude = ghStorage.getNodeAccess().getLatitude(endNode);
            double toLongitude = ghStorage.getNodeAccess().getLongitude(endNode);

            GHRequest request = new GHRequest(fromLatitude, fromLongitude, toLatitude, toLongitude);
            graphHopper.route(request);
        }
    }

    private static void warmUpNonCHSubNetwork(final GraphHopper graphHopper, int iterations) {
        GraphHopperStorage ghStorage = graphHopper.getGraphHopperStorage();
        Random rand = new Random(0);
        EdgeExplorer explorer = ghStorage.getBaseGraph().createEdgeExplorer();

        for (int i = 0; i < iterations; i++) {
            BreadthFirstSearch bfs = new BreadthFirstSearch() {
                int counter = 0;

                @Override
                public boolean goFurther(int nodeId) {
                    counter++;
                    return counter < graphHopper.getMaxVisitedNodes();
                }
            };
            int startNode = rand.nextInt(ghStorage.getBaseGraph().getNodes() + 1);
            bfs.start(explorer, startNode);
        }
    }
}
