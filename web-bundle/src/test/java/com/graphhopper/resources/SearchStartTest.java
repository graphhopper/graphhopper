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
package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.OSMWayID;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The isochrone and SPT endpoints have no custom_model input, but a subclass can pass one to
 * {@link SearchStart#create} via {@code createStart}, like the route endpoint does with the request hints.
 */
class SearchStartTest {
    private static final String GH_LOCATION = "target/search-start-gh";
    private static final GHPoint POINT = new GHPoint(43.727687, 7.418737);
    private static GraphHopper hopper;

    @BeforeAll
    static void setUp() {
        Helper.removeDir(new File(GH_LOCATION));
        Profile profile = TestProfiles.accessAndSpeed("car", "car");
        profile.getCustomModel().setParameter("closed_ways", List.of()).
                addToPriority(If("p_closed_ways.contains(osm_way_id)", MULTIPLY, "0"));
        hopper = new GraphHopper().init(new GraphHopperConfig().
                putObject("graph.location", GH_LOCATION).
                putObject("datareader.file", "../core/files/monaco.osm.gz").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.encoded_values", "car_access, car_average_speed, osm_way_id").
                putObject("routing.custom_model.max_set_size", 2).
                setProfiles(List.of(profile)));
        hopper.importOrLoad();
    }

    @AfterAll
    static void tearDown() {
        hopper.close();
        Helper.removeDir(new File(GH_LOCATION));
    }

    @Test
    void testSetParameter() {
        IntEncodedValue wayIdEnc = hopper.getEncodingManager().getIntEncodedValue(OSMWayID.KEY);
        SearchStart start = SearchStart.create(hopper, "car", new PMap(), POINT);
        int closedWay = start.snap().getClosestEdge().get(wayIdEnc);
        assertTrue(countEdgesOfWay(start, closedWay) > 0);

        // the set of the request reaches the weighting: the closed way is neither snapped to nor explored
        PMap hints = new PMap().putObject(CustomModel.KEY, new CustomModel().setParameter("closed_ways", List.of(closedWay)));
        SearchStart closed = SearchStart.create(hopper, "car", hints, POINT);
        assertNotEquals(closedWay, closed.snap().getClosestEdge().get(wayIdEnc));
        assertEquals(0, countEdgesOfWay(closed, closedWay));

        // and is limited via routing.custom_model.max_set_size
        PMap tooLarge = new PMap().putObject(CustomModel.KEY, new CustomModel().setParameter("closed_ways", List.of(1, 2, 3)));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SearchStart.create(hopper, "car", tooLarge, POINT));
        assertTrue(ex.getMessage().contains("must not have more than 2 members in total, but had: 3"), ex.getMessage());
    }

    /**
     * Searches like the isochrone and SPT endpoints and counts the explored edges of the given way
     */
    private static int countEdgesOfWay(SearchStart start, int wayId) {
        IntEncodedValue wayIdEnc = hopper.getEncodingManager().getIntEncodedValue(OSMWayID.KEY);
        QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), start.snap());
        ShortestPathTree tree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(start.weighting()), false, NODE_BASED);
        tree.setTimeLimit(300_000);
        int[] count = {0};
        tree.search(start.snap().getClosestNode(), label -> {
            if (label.edge != EdgeIterator.NO_EDGE && queryGraph.getEdgeIteratorState(label.edge, label.node).get(wayIdEnc) == wayId)
                count[0]++;
        });
        return count[0];
    }
}
