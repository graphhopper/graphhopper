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

package com.graphhopper.storage;

import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.getMemInfo;
import static com.graphhopper.util.Helper.nf;

public class CHImport {
    private static final Logger logger = LoggerFactory.getLogger(CHImport.class);

    public static void main(String[] args) throws IOException {
        PMap pMap = PMap.read(args);
        String profilesStr = pMap.getString("profiles", "car");
        List<Profile> profiles = Arrays.stream(profilesStr.split(","))
                .map(String::trim)
                .map(p -> new Profile(p).setVehicle(p).setTurnCosts(true)).collect(Collectors.toList());

        String ghLocation = pMap.getString("graph.location", "my_import_edge_default-gh");
        if (pMap.getBool("graph.clean", false))
            Helper.removeDir(new File(ghLocation));

        EncodingManager.Builder emBuilder = new EncodingManager.Builder();
        emBuilder.addAll(new DefaultTagParserFactory(), pMap.getString("graph.encoded_values", ""));
        emBuilder.addAll(new DefaultFlagEncoderFactory(), pMap.getString("graph.flag_encoders", ""));
        EncodingManager em = emBuilder.build();
        GraphBuilder graphBuilder = new GraphBuilder(em);
        graphBuilder.setDir(new GHDirectory(ghLocation, DAType.RAM_STORE));
        graphBuilder.withTurnCosts(true);
        GraphHopperStorage graph = graphBuilder.build();
        List<CHConfig> chConfigs = profiles.stream().map(p ->
                CHConfig.edgeBased(p.getName(), new FastestWeighting(em.getEncoder(p.getName()), new DefaultTurnCostProvider(
                        em.getEncoder(p.getName()), graph.getTurnCostStorage())))).collect(Collectors.toList());
        graph.addCHGraphs(chConfigs);

        if (!graph.loadExisting()) {
            OSMReader reader = new OSMReader(graph);
            String osmFile = pMap.getString("datareader.file", "");
            reader.setFile(new File(osmFile));
            reader.readGraph();
            int minNetworkSize = pMap.getInt("prepare.min_network_size", 200);
            new PrepareRoutingSubnetworks(graph, buildSubnetworkRemovalJobs(em, graph))
                    .setMinNetworkSize(minNetworkSize)
                    .doWork();
            graph.freeze();
            // make sure we close and flush waygeometry and names
            graph.flushAndCloseEarly();
            // flush properties and base graph, but do not close them and do not flush CH graphs
            graph.getProperties().flush();
            ((BaseGraph) graph.getBaseGraph()).flush();
        } else {
            chConfigs.forEach(c -> {
                CHGraphImpl chGraph = (CHGraphImpl) graph.getCHGraph(c.getName());
                chGraph.create(100);
                chGraph._prepareForContraction();
            });
        }
        logger.info("Finished loading graph: nodes: " + nf(graph.getNodes()) + ", edges: " + nf(graph.getEdges()));
        List<PrepareContractionHierarchies> preparations = chConfigs.stream()
                .map(c -> PrepareContractionHierarchies.fromGraphHopperStorage(graph, c).setParams(pMap))
                .collect(Collectors.toList());
        int numThreads = pMap.getInt("ch.threads", preparations.size());
        prepareCH(numThreads, preparations);
        logger.info("Finished CH preparation");
        chConfigs.forEach(c -> {
            CHGraph chGraph = graph.getCHGraph(c.getName());
            logger.info("CH graph '{}', nodes: {}, edges {}, shortcuts {}",
                    c.getName(), nf(chGraph.getNodes()), nf(chGraph.getOriginalEdges()), nf(chGraph.getEdges() - chGraph.getOriginalEdges()));
        });
    }

    private static void prepareCH(int numThreads, List<PrepareContractionHierarchies> preparations) {
        ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        for (final PrepareContractionHierarchies prepare : preparations) {
            logger.info((++counter) + "/" + preparations.size() + " calling " +
                    "CH prepare.doWork for profile '" + prepare.getCHConfig().getName() + "' " + prepare.getCHConfig().getTraversalMode() + " ... (" + getMemInfo() + ")");
            final String name = prepare.getCHConfig().getName();
            completionService.submit(() -> {
                // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                Thread.currentThread().setName(name);
                prepare.doWork();
            }, name);
        }

        threadPool.shutdown();

        try {
            for (int i = 0; i < preparations.size(); i++) {
                completionService.take().get();
            }
        } catch (Exception e) {
            threadPool.shutdownNow();
            throw new RuntimeException(e);
        }
    }

    private static List<PrepareRoutingSubnetworks.PrepareJob> buildSubnetworkRemovalJobs(EncodingManager encodingManager, GraphHopperStorage ghStorage) {
        List<FlagEncoder> encoders = encodingManager.fetchEdgeEncoders();
        List<PrepareRoutingSubnetworks.PrepareJob> jobs = new ArrayList<>();
        for (FlagEncoder encoder : encoders) {
            // for encoders with turn costs we do an edge-based subnetwork removal, because they *might* be used with
            // a profile with turn_costs=true
            if (encoder.supportsTurnCosts()) {
                // u-turn costs are zero as we only want to make sure the graph is fully connected assuming finite
                // u-turn costs
                TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(encoder, ghStorage.getTurnCostStorage(), 0);
                jobs.add(new PrepareRoutingSubnetworks.PrepareJob(encoder.toString(), encoder.getAccessEnc(), turnCostProvider));
            } else {
                jobs.add(new PrepareRoutingSubnetworks.PrepareJob(encoder.toString(), encoder.getAccessEnc(), null));
            }
        }
        return jobs;
    }

}
