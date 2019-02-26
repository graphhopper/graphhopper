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
package com.graphhopper.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.PrincetonReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.TestAlgoCollector.AlgoHelperEntry;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.Assert.assertTrue;

/**
 * Try algorithms, indices and graph storages with real data
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmIT {
    public static List<AlgoHelperEntry> createAlgos(final GraphHopper hopper, final HintsMap hints, TraversalMode tMode) {
        GraphHopperStorage ghStorage = hopper.getGraphHopperStorage();
        LocationIndex idx = hopper.getLocationIndex();

        String addStr = "";
        if (tMode.isEdgeBased())
            addStr = "turn|";

        FlagEncoder encoder = hopper.getEncodingManager().getEncoder(hints.getVehicle());
        Weighting weighting = hopper.createWeighting(hints, encoder, hopper.getGraphHopperStorage());

        HintsMap defaultHints = new HintsMap().put(Parameters.CH.DISABLE, true).put(Parameters.Landmark.DISABLE, true)
                .setVehicle(hints.getVehicle()).setWeighting(hints.getWeighting());

        AlgorithmOptions defaultOpts = AlgorithmOptions.start(new AlgorithmOptions("", weighting, tMode)).hints(defaultHints).build();
        List<AlgoHelperEntry> prepare = new ArrayList<>();
        prepare.add(new AlgoHelperEntry(ghStorage, AlgorithmOptions.start(defaultOpts).algorithm(ASTAR).build(), idx, "astar|beeline|" + addStr + weighting));
        // later: include dijkstraOneToMany
        prepare.add(new AlgoHelperEntry(ghStorage, AlgorithmOptions.start(defaultOpts).algorithm(DIJKSTRA).build(), idx, "dijkstra|" + addStr + weighting));

        AlgorithmOptions astarbiOpts = AlgorithmOptions.start(defaultOpts).algorithm(ASTAR_BI).build();
        astarbiOpts.getHints().put(ASTAR_BI + ".approximation", "BeelineSimplification");
        AlgorithmOptions dijkstrabiOpts = AlgorithmOptions.start(defaultOpts).algorithm(DIJKSTRA_BI).build();
        prepare.add(new AlgoHelperEntry(ghStorage, astarbiOpts, idx, "astarbi|beeline|" + addStr + weighting));
        prepare.add(new AlgoHelperEntry(ghStorage, dijkstrabiOpts, idx, "dijkstrabi|" + addStr + weighting));

        // add additional preparations if CH and LM preparation are enabled
        if (hopper.getLMFactoryDecorator().isEnabled()) {
            final HintsMap lmHints = new HintsMap(defaultHints).put(Parameters.Landmark.DISABLE, false);
            prepare.add(new AlgoHelperEntry(ghStorage, AlgorithmOptions.start(astarbiOpts).hints(lmHints).build(), idx, "astarbi|landmarks|" + weighting) {
                @Override
                public RoutingAlgorithmFactory createRoutingFactory() {
                    return hopper.getAlgorithmFactory(lmHints);
                }
            });
        }

        if (hopper.getCHFactoryDecorator().isEnabled()) {
            final HintsMap chHints = new HintsMap(defaultHints);
            chHints.put(Parameters.CH.DISABLE, false);
            chHints.put(Parameters.Routing.EDGE_BASED, tMode.isEdgeBased());
            Weighting pickedWeighting = null;
            for (Weighting tmpWeighting : hopper.getCHFactoryDecorator().getNodeBasedWeightings()) {
                if (tmpWeighting.equals(weighting)) {
                    pickedWeighting = tmpWeighting;
                    break;
                }
            }
            // todo: not so sure about this, can the edge based weighting entry overwrite the picked weighting found
            // in the node based weightings ?
            for (Weighting tmpWeighting : hopper.getCHFactoryDecorator().getEdgeBasedWeightings()) {
                if (tmpWeighting.equals(weighting)) {
                    pickedWeighting = tmpWeighting;
                    break;
                }
            }
            if (pickedWeighting == null)
                throw new IllegalStateException("Didn't find weighting " + hints.getWeighting() + " in " + hopper.getCHFactoryDecorator().getNodeBasedWeightings());

            prepare.add(new AlgoHelperEntry(ghStorage.getGraph(CHGraph.class, pickedWeighting),
                    AlgorithmOptions.start(dijkstrabiOpts).hints(chHints).build(), idx, "dijkstrabi|ch|prepare|" + hints.getWeighting()) {
                @Override
                public RoutingAlgorithmFactory createRoutingFactory() {
                    return hopper.getAlgorithmFactory(chHints);
                }
            });

            prepare.add(new AlgoHelperEntry(ghStorage.getGraph(CHGraph.class, pickedWeighting),
                    AlgorithmOptions.start(astarbiOpts).hints(chHints).build(), idx, "astarbi|ch|prepare|" + hints.getWeighting()) {
                @Override
                public RoutingAlgorithmFactory createRoutingFactory() {
                    return hopper.getAlgorithmFactory(chHints);
                }
            });
        }

        return prepare;
    }

    @Test
    public void testPerformance() throws IOException {
        int N = 10;
        int noJvmWarming = N / 4;

        Random rand = new Random(0);
        final EncodingManager eManager = EncodingManager.create("car");
        final GraphHopperStorage graph = new GraphBuilder(eManager).create();

        String bigFile = "10000EWD.txt.gz";
        new PrincetonReader(graph).setStream(new GZIPInputStream(PrincetonReader.class.getResourceAsStream(bigFile))).read();
        GraphHopper hopper = new GraphHopper() {
            {
                setCHEnabled(false);
                setEncodingManager(eManager);
                loadGraph(graph);
            }

            @Override
            protected LocationIndex createLocationIndex(Directory dir) {
                return new LocationIndexTree(graph, dir);
            }
        };

        Collection<AlgoHelperEntry> prepares = createAlgos(hopper, new HintsMap().setWeighting("shortest").setVehicle("car"), TraversalMode.NODE_BASED);

        for (AlgoHelperEntry entry : prepares) {
            StopWatch sw = new StopWatch();
            for (int i = 0; i < N; i++) {
                int node1 = Math.abs(rand.nextInt(graph.getNodes()));
                int node2 = Math.abs(rand.nextInt(graph.getNodes()));
                RoutingAlgorithm d = entry.createRoutingFactory().createAlgo(graph, entry.getAlgorithmOptions());
                if (i >= noJvmWarming)
                    sw.start();

                Path p = d.calcPath(node1, node2);
                // avoid jvm optimization => call p.distance
                if (i >= noJvmWarming && p.getDistance() > -1)
                    sw.stop();

                // System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.nodes());
            }

            float perRun = sw.stop().getSeconds() / ((float) (N - noJvmWarming));
            System.out.println("# " + getClass().getSimpleName() + " " + entry
                    + ":" + sw.stop().getSeconds() + ", per run:" + perRun);
            assertTrue("speed too low!? " + perRun + " per run", perRun < 0.08);
        }
    }
}
