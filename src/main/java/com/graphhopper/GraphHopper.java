/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.FastestCarCalc;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.Location2IDQuadtree;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private boolean levelGraph;
    private String ghLocation = "";

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

    public GraphHopper forDesktop() {
        return setInMemory(true, true);
    }

    public GraphHopper forServer() {
        return setInMemory(true, true);
    }

    public GraphHopper forAndroid() {
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

    public GraphHopper levelGraph() {
        levelGraph = true;
        return this;
    }

    public GraphHopper setGraphHopperLocation(String ghLocation) {
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

        String tmp = graphHopperFile.toLowerCase();
        if (tmp.endsWith("-gh") || tmp.endsWith(".ghz")) {
            if (tmp.endsWith(".ghz")) {
                String to = Helper.pruneFileEnd(tmp) + "-gh";
                try {
                    Helper.unzip(tmp, to, true);
                } catch (IOException ex) {
                    throw new IllegalStateException("Couldn't extract file " + tmp + " to " + to, ex);
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

            if (levelGraph) {
                storage = new LevelGraphStorage(dir);
                prepare = new PrepareContractionHierarchies().setType(FastestCarCalc.DEFAULT);
            } else
                storage = new GraphStorage(dir);

            if (!storage.loadExisting())
                throw new IllegalStateException("Couldn't load storage at " + graphHopperFile);

            graph = storage;
            initIndex(dir);
        } else if (tmp.endsWith(".osm") || tmp.endsWith(".xml")) {
            if (ghLocation.isEmpty())
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
            if (levelGraph) {
                args.put("osmreader.levelgraph", "true");
                args.put("osmreader.chShortcuts", "fastest");
            }

            try {
                OSMReader reader = OSMReader.osm2Graph(args);
                graph = reader.getGraph();
                prepare = reader.getPreparation();
                index = reader.getLocation2IDIndex();
            } catch (IOException ex) {
                throw new RuntimeException("Cannot parse file " + graphHopperFile, ex);
            }
        } else
            throw new IllegalArgumentException("Unknown file end " + graphHopperFile);

        return this;
    }

    @Override
    public GHResponse route(GHRequest request) {
        if (levelGraph) {
            if (!request.algorithm().equals("dijkstrabi"))
                throw new IllegalStateException("Only dijkstrabi is supported for levelgraph/CH! "
                        + "TODO we could allow bidirectional astar");
        } else
            prepare = Helper.createAlgoPrepare(request.algorithm());

        request.check();        
        StopWatch sw = new StopWatch().start();
        int from = index.findID(request.from().lat, request.from().lon);
        int to = index.findID(request.to().lat, request.to().lon);
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        prepare.setGraph(graph);
        RoutingAlgorithm algo = prepare.createAlgo();
        Path path = algo.calcPath(from, to);
        debug += " routing (" + algo.name() + "):" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        path.simplify(new DouglasPeucker(graph).setMaxDist(request.minPathPrecision()));
        debug += " simplify:" + sw.stop().getSeconds() + "s";

        int nodes = path.nodes();
        List<GHPoint> list = new ArrayList<GHPoint>(nodes);
        if (path.found()) {
            sw = new StopWatch().start();
            for (int i = 0; i < nodes; i++) {
                list.add(new GHPoint(graph.getLatitude(path.node(i)), graph.getLongitude(path.node(i))));
            }
            debug += " createList:" + sw.stop().getSeconds() + "s";
        }
        return new GHResponse(list).distance(path.distance()).time(path.time()).debugInfo(debug);
    }

    private void initIndex(Directory dir) {
        Location2IDQuadtree tmp = new Location2IDQuadtree(graph, dir);
        if (!tmp.loadExisting())
            tmp.prepareIndex(Helper.calcIndexSize(graph.getBounds()));

        index = tmp;
    }

    public Graph getGraph() {
        return graph;
    }
}
