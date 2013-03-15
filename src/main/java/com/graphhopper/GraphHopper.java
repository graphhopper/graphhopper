/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.reader.OSMReader;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.index.Location2IDQuadtree;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import java.io.File;
import java.io.IOException;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 *
 * @see GraphHopperAPI
 * @author Peter Karich
 */
public class GraphHopper implements GraphHopperAPI {

    private Graph graph;
    private AlgorithmPreparation prepare;
    private Location2IDIndex index;
    private boolean inMemory = true;
    private boolean storeOnFlush = true;
    private boolean memoryMapped;
    private boolean chUsage = false;
    private String ghLocation = "";
    private boolean simplify = true;
    private boolean chFast = true;
    private AcceptWay acceptWay = new AcceptWay(true, false, false);

    public GraphHopper() {
    }

    /**
     * For testing
     */
    GraphHopper(Graph g) {
        this();
        this.graph = g;
        initIndex(new RAMDirectory());
    }

    public AcceptWay acceptWay() {
        return acceptWay;
    }

    public GraphHopper forDesktop() {
        return setInMemory(true, true);
    }

    public GraphHopper forServer() {
        return setInMemory(true, true);
    }

    public GraphHopper forAndroid() {
        // no need to simplify as no IO and simplifying costs a bit CPU
        simplify = false;
        return memoryMapped();
    }

    public GraphHopper setInMemory(boolean inMemory, boolean storeOnFlush) {
        if (inMemory) {
            this.inMemory = true;
            this.memoryMapped = false;
            this.storeOnFlush = storeOnFlush;
        } else {
            memoryMapped();
        }
        return this;
    }

    public GraphHopper memoryMapped() {
        this.inMemory = false;
        memoryMapped = true;
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times.
     *
     * @param true if fastest route should be calculated (instead of shortest)
     */
    public GraphHopper contractionHierarchies(boolean fast) {
        chUsage = true;
        chFast = fast;
        return this;
    }

    public GraphHopper simplify(boolean doSimplify) {
        this.simplify = doSimplify;
        return this;
    }

    public GraphHopper graphHopperLocation(String ghLocation) {
        if (ghLocation != null)
            this.ghLocation = ghLocation;
        return this;
    }

    @Override
    public GraphHopper load(String graphHopperFile) {
        if (graph != null)
            throw new IllegalStateException("graph is already loaded");

        if (graphHopperFile.indexOf(".") < 0) {
            if (new File(graphHopperFile + "-gh").exists())
                graphHopperFile += "-gh";
            else if (new File(graphHopperFile + ".osm").exists())
                graphHopperFile += ".osm";
            else
                throw new IllegalArgumentException("No file end and no existing osm or gh file found for " + graphHopperFile);
        }

        String tmpGHFile = graphHopperFile.toLowerCase();
        if (tmpGHFile.endsWith("-gh") || tmpGHFile.endsWith(".ghz")) {
            if (tmpGHFile.endsWith(".ghz")) {
                String to = Helper.pruneFileEnd(tmpGHFile) + "-gh";
                try {
                    Helper.unzip(tmpGHFile, to, true);
                } catch (IOException ex) {
                    throw new IllegalStateException("Couldn't extract file " + tmpGHFile + " to " + to, ex);
                }
            }

            GraphStorage storage;
            Directory dir;
            if (memoryMapped) {
                dir = new MMapDirectory(graphHopperFile);
            } else if (inMemory) {
                dir = new RAMDirectory(graphHopperFile, storeOnFlush);
            } else
                throw new IllegalStateException("either memory mapped or in-memory!");

            if (chUsage) {
                storage = new LevelGraphStorage(dir);
                PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies();

                VehicleEncoder encoder;
                if (acceptWay.acceptsCar())
                    encoder = new CarFlagEncoder();
                else
                    encoder = new FootFlagEncoder();
                if (chFast) {
                    tmpPrepareCH.type(new FastestCalc(encoder)).vehicle(encoder);
                } else {
                    tmpPrepareCH.type(new ShortestCalc()).vehicle(encoder);
                }
                prepare = tmpPrepareCH;
            } else
                storage = new GraphStorage(dir);

            if (!storage.loadExisting())
                throw new IllegalStateException("Couldn't load storage at " + graphHopperFile);

            graph = storage;
            initIndex(dir);
        } else if (tmpGHFile.endsWith(".osm") || tmpGHFile.endsWith(".xml")) {
            if (Helper.isEmpty(ghLocation))
                ghLocation = Helper.pruneFileEnd(graphHopperFile) + "-gh";
            CmdArgs args = new CmdArgs().put("osmreader.osm", graphHopperFile).
                    put("osmreader.graph-location", ghLocation);
            if (memoryMapped)
                args.put("osmreader.dataaccess", "mmap");
            else {
                if (inMemory && storeOnFlush) {
                    args.put("osmreader.dataaccess", "inmemory+save");
                } else
                    args.put("osmreader.dataaccess", "inmemory");
            }

            args.put("osmreader.type", acceptWay.toString());

            if (chUsage) {
                args.put("osmreader.levelgraph", "true");
                args.put("osmreader.chShortcuts", chFast ? "fastest" : "shortest");
            }

            try {
                OSMReader reader = OSMReader.osm2Graph(args);
                graph = reader.graph();
                prepare = reader.preparation();
                index = reader.location2IDIndex();
            } catch (IOException ex) {
                throw new RuntimeException("Cannot parse file " + graphHopperFile, ex);
            }
        } else
            throw new IllegalArgumentException("Unknown file end " + graphHopperFile);

        return this;
    }

    @Override
    public GHResponse route(GHRequest request) {
        request.check();
        StopWatch sw = new StopWatch().start();
        int from = index.findID(request.from().lat, request.from().lon);
        int to = index.findID(request.to().lat, request.to().lon);
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        RoutingAlgorithm algo;
        if (chUsage) {
            prepare.graph(graph);
            if (request.algorithm().equals("dijkstrabi"))
                algo = prepare.createAlgo();
            else if (request.algorithm().equals("astarbi"))
                algo = ((PrepareContractionHierarchies) prepare).createAStar();
            else
                throw new IllegalStateException("Only dijkstrabi and astarbi is supported for levelgraph/CH!");
        } else {
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.algorithm(), request.vehicle());
            algo = prepare.createAlgo();
            algo.type(request.type());
        }
        debug += ", algoInit:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        Path path = algo.calcPath(from, to);
        debug += ", " + algo.name() + "-routing:" + sw.stop().getSeconds() + "s"
                + ", " + path.debugInfo();
        PointList points = path.calcPoints();
        if (simplify) {
            sw = new StopWatch().start();
            int orig = points.size();
            double minPathPrecision = request.getHint("douglas.minprecision", 1d);
            new DouglasPeucker().maxDistance(minPathPrecision).simplify(points);
            debug += ", simplify (" + orig + "->" + points.size() + "):" + sw.stop().getSeconds() + "s";
        }
        return new GHResponse(points).distance(path.distance()).time(path.time()).debugInfo(debug);
    }

    private void initIndex(Directory dir) {
        Location2IDQuadtree tmp = new Location2IDQuadtree(graph, dir);
        if (!tmp.loadExisting())
            tmp.prepareIndex(Helper.calcIndexSize(graph.bounds()));

        index = tmp;
    }

    public Graph getGraph() {
        return graph;
    }
}
