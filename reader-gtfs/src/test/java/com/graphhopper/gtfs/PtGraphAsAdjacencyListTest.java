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

package com.graphhopper.gtfs;

import com.carrotsearch.hppc.BitSetIterator;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.subnetwork.TarjanSCC;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static com.graphhopper.gtfs.GtfsStorage.EdgeType.ENTER_PT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PtGraphAsAdjacencyListTest {

    private static final String GRAPH_LOC = "target/AnotherAgencyIT";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("gtfs.file", "files/sample-feed,files/another-sample-feed");
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                new Profile("car").setVehicle("car").setWeighting("fastest")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testRoute1() {
        PtGraph ptGraph = graphHopperGtfs.getPtGraph();
        PtGraphAsAdjacencyList ptGraphAsAdjacencyList = new PtGraphAsAdjacencyList(ptGraph);
        TarjanSCC.ConnectedComponents components = TarjanSCC.findComponents(ptGraphAsAdjacencyList, EdgeFilter.ALL_EDGES, false);
        System.out.println(components.getTotalComponents());
        System.out.println(components.getComponents().size());
        System.out.println(components.getSingleNodeComponents().cardinality());
        for (IntArrayList component : components.getComponents()) {
            System.out.println("------------");
            for (IntCursor intCursor : component) {
                printNode(ptGraph, intCursor.value);
            }
        }
        BitSetIterator iter = components.getSingleNodeComponents().iterator();
        for (int i = iter.nextSetBit(); i >= 0; i = iter.nextSetBit()) {
            System.out.println("------------");
            printNode(ptGraph, i);
        }
    }

    private void printNode(PtGraph ptGraph, int i) {
        EnumSet<GtfsStorage.EdgeType> inEdgeTypes = EnumSet.noneOf(GtfsStorage.EdgeType.class);
        for (PtGraph.PtEdge ptEdge : ptGraph.backEdgesAround(i)) {
            inEdgeTypes.add(ptEdge.getType());
        }
        EnumSet<GtfsStorage.EdgeType> outEdgeTypes = EnumSet.noneOf(GtfsStorage.EdgeType.class);
        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(i)) {
            outEdgeTypes.add(ptEdge.getType());
        }
        Set<GtfsStorage.FeedIdWithStopId> stops = new HashSet<>();
        if (inEdgeTypes.equals(EnumSet.of(GtfsStorage.EdgeType.EXIT_PT)) && outEdgeTypes.equals((EnumSet.of(ENTER_PT)))) {
            ptGraph.backEdgesAround(i).forEach(e -> stops.add(new GtfsStorage.FeedIdWithStopId(e.getAttrs().platformDescriptor.feed_id, e.getAttrs().platformDescriptor.stop_id)));
            ptGraph.edgesAround(i).forEach(e -> stops.add(new GtfsStorage.FeedIdWithStopId(e.getAttrs().platformDescriptor.feed_id, e.getAttrs().platformDescriptor.stop_id)));
            for (GtfsStorage.FeedIdWithStopId stop : stops) {
                System.out.printf("%d:\t%s\n", i, stop);
            }
            // System.out.printf("%d:\t%s %s\n", i, inEdgeTypes, outEdgeTypes);
        }
    }

}
