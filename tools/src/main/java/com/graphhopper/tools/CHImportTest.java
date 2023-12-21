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

package com.graphhopper.tools;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ch.CHParameters;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.util.Helper;
import com.graphhopper.util.MiniPerfTest;
import com.graphhopper.util.PMap;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class CHImportTest {
    public static void main(String[] args) {
        System.out.println("running for args: " + Arrays.toString(args));
        PMap map = PMap.read(args);
        String vehicle = map.getString("vehicle", "car");
        GraphHopperConfig config = new GraphHopperConfig(map);
        config.putObject("datareader.file", map.getString("pbf", "map-matching/files/leipzig_germany.osm.pbf"));
        config.putObject("graph.location", map.getString("gh", "ch-import-test-gh"));
        config.setProfiles(Collections.singletonList(new Profile(vehicle).setCustomModel(Helper.createBaseModel(vehicle))));
        config.setCHProfiles(Collections.singletonList(new CHProfile(vehicle)));
        config.putObject(CHParameters.PERIODIC_UPDATES, map.getInt("periodic", 0));
        config.putObject(CHParameters.LAST_LAZY_NODES_UPDATES, map.getInt("lazy", 100));
        config.putObject(CHParameters.NEIGHBOR_UPDATES, map.getInt("neighbor", 100));
        config.putObject(CHParameters.NEIGHBOR_UPDATES_MAX, map.getInt("neighbor_max", 2));
        config.putObject(CHParameters.CONTRACTED_NODES, map.getInt("contracted", 100));
        config.putObject(CHParameters.LOG_MESSAGES, map.getInt("logs", 20));
        config.putObject(CHParameters.EDGE_DIFFERENCE_WEIGHT, map.getDouble("edge_diff", 10));
        config.putObject(CHParameters.ORIGINAL_EDGE_COUNT_WEIGHT, map.getDouble("orig_edge", 1));
        config.putObject(CHParameters.MAX_POLL_FACTOR_HEURISTIC_NODE, map.getDouble("mpf_heur", 5));
        config.putObject(CHParameters.MAX_POLL_FACTOR_CONTRACTION_NODE, map.getDouble("mpf_contr", 200));
        GraphHopper hopper = new GraphHopper();
        hopper.init(config);
        if (map.getBool("use_country_rules", false))
            // note that using this requires a new import of the base graph!
            hopper.setCountryRuleFactory(new CountryRuleFactory());
        hopper.importOrLoad();
        runQueries(hopper, vehicle);
    }

    private static void runQueries(GraphHopper hopper, String profile) {
        // Bavaria, but trying to avoid regions that are not covered
        BBox bounds = new BBox(10.508422, 12.326602, 47.713457, 49.940615);
        int numQueries = 10_000;
        long seed = 123;
        Random rnd = new Random(seed);
        AtomicInteger notFoundCount = new AtomicInteger();
        MiniPerfTest test = new MiniPerfTest().setIterations(numQueries).start((warmup, run) -> {
            GHPoint from = getRandomPoint(rnd, bounds);
            GHPoint to = getRandomPoint(rnd, bounds);
            GHRequest req = new GHRequest(from, to).setProfile(profile);
            GHResponse rsp = hopper.route(req);
            if (rsp.hasErrors()) {
                if (rsp.getErrors().stream().anyMatch(t -> !(t instanceof PointNotFoundException || t instanceof ConnectionNotFoundException)))
                    throw new IllegalStateException("Unexpected error: " + rsp.getErrors().toString());
                notFoundCount.incrementAndGet();
                return 0;
            } else {
                return (int) rsp.getBest().getRouteWeight();
            }
        });
        System.out.println("Total queries: " + numQueries + ", Failed queries: " + notFoundCount.get());
        System.out.println(test.getReport());
    }

    private static GHPoint getRandomPoint(Random rnd, BBox bounds) {
        double lat = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
        double lon = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
        return new GHPoint(lat, lon);
    }
}
