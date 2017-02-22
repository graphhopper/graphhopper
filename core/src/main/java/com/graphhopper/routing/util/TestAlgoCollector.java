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
package com.graphhopper.routing.util;

import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Peter Karich
 */
public class TestAlgoCollector {
    public final List<String> errors = new ArrayList<String>();
    private final String name;
    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final TranslationMap trMap = new TranslationMap().doImport();

    public TestAlgoCollector(String name) {
        this.name = name;
    }

    public TestAlgoCollector assertDistance(AlgoHelperEntry algoEntry, List<QueryResult> queryList,
                                            OneRun oneRun) {
        List<Path> altPaths = new ArrayList<>();
        QueryGraph queryGraph = new QueryGraph(algoEntry.getForQueryGraph());
        queryGraph.lookup(queryList);
        AlgorithmOptions opts = algoEntry.getAlgorithmOptions();
        FlagEncoder encoder = opts.getWeighting().getFlagEncoder();
        if (encoder.supports(TurnWeighting.class)) {
            if (!opts.getTraversalMode().isEdgeBased()) {
                errors.add("Cannot use TurnWeighting with a node based traversal");
                return this;
            }
            algoEntry.setAlgorithmOptions(AlgorithmOptions.start(opts).weighting(new TurnWeighting(opts.getWeighting(), (TurnCostExtension) queryGraph.getExtension())).build());
        }

        RoutingAlgorithmFactory factory = algoEntry.createRoutingFactory();
        for (int i = 0; i < queryList.size() - 1; i++) {
            RoutingAlgorithm algo = factory.createAlgo(queryGraph, algoEntry.getAlgorithmOptions());

//            if (!algoEntry.getExpectedAlgo().equals(algo.toString())) {
//                errors.add("Algorithm expected " + algoEntry.getExpectedAlgo() + " but was " + algo.toString());
//                return this;
//            }

            Path path = algo.calcPath(queryList.get(i).getClosestNode(), queryList.get(i + 1).getClosestNode());
            altPaths.add(path);
        }

        PathMerger pathMerger = new PathMerger().
                setCalcPoints(true).
                setSimplifyResponse(false).
                setEnableInstructions(true);
        PathWrapper rsp = new PathWrapper();
        pathMerger.doWork(rsp, altPaths, trMap.getWithFallBack(Locale.US));

        if (rsp.hasErrors()) {
            errors.add("response for " + algoEntry + " contains errors. Expected distance: " + oneRun.getDistance()
                    + ", expected points: " + oneRun + ". " + queryList + ", errors:" + rsp.getErrors());
            return this;
        }

        PointList pointList = rsp.getPoints();
        double tmpDist = pointList.calcDistance(distCalc);
        if (Math.abs(rsp.getDistance() - tmpDist) > 2) {
            errors.add(algoEntry + " path.getDistance was  " + rsp.getDistance()
                    + "\t pointList.calcDistance was " + tmpDist + "\t (expected points " + oneRun.getLocs()
                    + ", expected distance " + oneRun.getDistance() + ") " + queryList);
        }

        if (Math.abs(rsp.getDistance() - oneRun.getDistance()) > 2) {
            errors.add(algoEntry + " returns path not matching the expected distance of " + oneRun.getDistance()
                    + "\t Returned was " + rsp.getDistance() + "\t (expected points " + oneRun.getLocs()
                    + ", was " + pointList.getSize() + ") " + queryList);
        }

        // There are real world instances where A-B-C is identical to A-C (in meter precision).
        if (Math.abs(pointList.getSize() - oneRun.getLocs()) > 1) {
            errors.add(algoEntry + " returns path not matching the expected points of " + oneRun.getLocs()
                    + "\t Returned was " + pointList.getSize() + "\t (expected distance " + oneRun.getDistance()
                    + ", was " + rsp.getDistance() + ") " + queryList);
        }
        return this;
    }

    void queryIndex(Graph g, LocationIndex idx, double lat, double lon, double expectedDist) {
        QueryResult res = idx.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        if (!res.isValid()) {
            errors.add("node not found for " + lat + "," + lon);
            return;
        }

        GHPoint found = res.getSnappedPoint();
        double dist = distCalc.calcDist(lat, lon, found.lat, found.lon);
        if (Math.abs(dist - expectedDist) > .1) {
            errors.add("queried lat,lon=" + (float) lat + "," + (float) lon
                    + " (found: " + (float) found.lat + "," + (float) found.lon + ")"
                    + "\n   expected distance:" + expectedDist + ", but was:" + dist);
        }
    }

    @Override
    public String toString() {
        String str = "";
        str += "FOUND " + errors.size() + " ERRORS.\n";
        for (String s : errors) {
            str += s + ".\n";
        }
        return str;
    }

    void printSummary() {
        if (errors.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(toString());
        } else {
            System.out.println("SUCCESS for " + name + "!");
        }
    }

    public static class AlgoHelperEntry {
        private final LocationIndex idx;
        private Graph forQueryGraph;
        private String expectedAlgo;
        private AlgorithmOptions opts;

        public AlgoHelperEntry(Graph g, AlgorithmOptions opts, LocationIndex idx, String expectedAlgo) {
            this.forQueryGraph = g;
            this.opts = opts;
            this.idx = idx;
            this.expectedAlgo = expectedAlgo;
        }

        public Graph getForQueryGraph() {
            return forQueryGraph;
        }

        public void setAlgorithmOptions(AlgorithmOptions opts) {
            this.opts = opts;
        }

        public RoutingAlgorithmFactory createRoutingFactory() {
            return new RoutingAlgorithmFactorySimple();
        }

        public AlgorithmOptions getAlgorithmOptions() {
            return opts;
        }

        public LocationIndex getIdx() {
            return idx;
        }

        public String getExpectedAlgo() {
            return expectedAlgo;
        }

        @Override
        public String toString() {
            String algo = opts.getAlgorithm();
            if (getExpectedAlgo().contains("landmarks"))
                algo += "|landmarks";
            if (forQueryGraph instanceof CHGraph)
                algo += "|ch";

            return "algoEntry(" + algo + ")";
        }
    }

    public static class OneRun {
        private final List<AssumptionPerPath> assumptions = new ArrayList<AssumptionPerPath>();

        public OneRun() {
        }

        public OneRun(double fromLat, double fromLon, double toLat, double toLon, double dist, int locs) {
            add(fromLat, fromLon, 0, 0);
            add(toLat, toLon, dist, locs);
        }

        public OneRun add(double lat, double lon, double dist, int locs) {
            assumptions.add(new AssumptionPerPath(lat, lon, dist, locs));
            return this;
        }

        public int getLocs() {
            int sum = 0;
            for (AssumptionPerPath as : assumptions) {
                sum += as.locs;
            }
            return sum;
        }

        public void setLocs(int index, int locs) {
            assumptions.get(index).locs = locs;
        }

        public double getDistance() {
            double sum = 0;
            for (AssumptionPerPath as : assumptions) {
                sum += as.distance;
            }
            return sum;
        }

        public void setDistance(int index, double dist) {
            assumptions.get(index).distance = dist;
        }

        public List<QueryResult> getList(LocationIndex idx, EdgeFilter edgeFilter) {
            List<QueryResult> qr = new ArrayList<QueryResult>();
            for (AssumptionPerPath p : assumptions) {
                qr.add(idx.findClosest(p.lat, p.lon, edgeFilter));
            }
            return qr;
        }

        @Override
        public String toString() {
            return assumptions.toString();
        }
    }

    static class AssumptionPerPath {
        double lat, lon;
        int locs;
        double distance;

        public AssumptionPerPath(double lat, double lon, double distance, int locs) {
            this.lat = lat;
            this.lon = lon;
            this.locs = locs;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return lat + ", " + lon + ", locs:" + locs + ", dist:" + distance;
        }
    }
}
