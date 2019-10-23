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

import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Random;

public class ViaRoutingMeasurement {

    public static void main(String[] args) {
        String pathToMap = "/home/andi/maps_and_graphs/osm/germany-140101.osm.pbf";
        String ghFolder = "/home/andi/maps_and_graphs/gh/qgmeasurement2-gh";
        GraphHopper hopper = new GraphHopperOSM()
                // use forDesktop, because forServer is very slow because of path simplification
//                .forDesktop()
                .forServer()
                .setDataReaderFile(pathToMap)
                .setEncodingManager(EncodingManager.create("car"))
                .setGraphHopperLocation(ghFolder)
                .setStoreOnFlush(true)
                .setMinNetworkSize(10_000, 10_000)
                .importOrLoad();

        final long seed = 1234;
        final int numPoints = 50;
        GraphHopper.useQueryGraphCache = false;
        // just let it run and watch output
        final int numRuns = 1000;
        LongArrayList routingTimes = new LongArrayList();
        LongArrayList explorerBuildingTimes = new LongArrayList();
        // only when using hopper.forServer()
        LongArrayList pathSimplificationTimes = new LongArrayList();
        for (int run = 0; run < numRuns; run++) {
            Random rnd = new Random(seed);
            BBox bounds = hopper.getGraphHopperStorage().getBounds();
            LocationIndex index = hopper.getLocationIndex();
            GHRequest request = new GHRequest(numPoints);
            int pointCount = 0;
            while (pointCount < numPoints) {
                double lat = rnd.nextDouble() * (bounds.maxLat - bounds.minLat) + bounds.minLat;
                double lon = rnd.nextDouble() * (bounds.maxLon - bounds.minLon) + bounds.minLon;
                if (index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).isValid()) {
                    request.addPoint(new GHPoint(lat, lon));
                    pointCount++;
                }
            }
            StopWatch sw = new StopWatch().start();
            GHResponse response = hopper.route(request);
            if (response.hasErrors()) {
                System.out.println(response.getErrors());
                continue;
            }
            long nanos = sw.stop().getNanos();
            // we keep track of the running averages over all runs
            long routingAverage = Integer.MIN_VALUE;
            long explorerAverage = Integer.MIN_VALUE;
            long pathSimplificationAverage = Integer.MIN_VALUE;
            // allow some warmup
            int numWarmupRuns = 10;
            if (run >= numWarmupRuns) {
                routingTimes.add(nanos);
                explorerBuildingTimes.add(QueryGraph.sw.getNanos());
                pathSimplificationTimes.add(PathMerger.sw.getNanos());
                routingAverage = avg(routingTimes);
                explorerAverage = avg(explorerBuildingTimes);
                pathSimplificationAverage = avg(pathSimplificationTimes);
            }
            if (run < numWarmupRuns) {
                System.out.println("warmup");
            }
            System.out.println(
                    "routing: " + nanosToMillis(nanos) + "ms (" + nanosToMillis(nanos / numPoints) + "/point, avg: " + nanosToMillis(routingAverage) + "), " +
                            "explorers: " + nanosToMillis(QueryGraph.sw.getNanos()) + "ms (" + nanosToMillis(QueryGraph.sw.getNanos() / numPoints) + "/point, avg: " + nanosToMillis(explorerAverage) + " (" + percentage(explorerAverage, routingAverage) + "), " +
                            "simplification: " + nanosToMillis(PathMerger.sw.getNanos()) + "ms (" + nanosToMillis(PathMerger.sw.getNanos() / numPoints) + "/point, avg: " + nanosToMillis(pathSimplificationAverage) + " (" + percentage(pathSimplificationAverage, routingAverage) + "), " +
                            "num explorers: " + QueryGraph.count + ", " +
                            "checksum: " + (int) response.getBest().getRouteWeight());
            QueryGraph.count = 0;
            QueryGraph.sw = new StopWatch();
            PathMerger.sw = new StopWatch();
        }
    }

    private static String percentage(long amount, long total) {
        return String.format("%.2f%%", (amount * 100.0 / total));
    }

    private static long nanosToMillis(long routingAverage) {
        return routingAverage / 1_000_000;
    }

    private static long avg(LongArrayList list) {
        long result = 0;
        for (int i = 0; i < list.size(); i++) {
            result += list.get(i);
        }
        return result / list.size();
    }

}
