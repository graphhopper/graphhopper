/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.reader.GTFSReader;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.LocationTime2IDIndex;
import com.graphhopper.storage.index.LocationTime2NodeNtree;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class GHPublicTransit implements GraphHopperAPI {

    private Logger logger = LoggerFactory.getLogger(getClass());
    // for graph:
    private GraphStorage graph;
    private String ghLocation = "";
    private boolean inMemory = true;
    private boolean storeOnFlush = true;
    private boolean memoryMapped;
    // for routing
    private String defaultAlgorithm = "dijkstra";
    // for index:
    private LocationTime2IDIndex index;
    private int preciseIndexResolution = 1000;
    private boolean edgeCalcOnSearch = true;
    private boolean searchRegion = true;
    // for GTFS
    private String gtfsFile = "";
    private int defaultAlightTime = 240;
    private StorableProperties properties;
    // for prepare
    private AlgorithmPreparation prepare;
    private boolean doPrepare = true;

    public GHPublicTransit forServer() {
        preciseIndexResolution(1000);
        return setInMemory(true, true);
    }

    public GHPublicTransit forDesktop() {
        preciseIndexResolution(1000);
        return setInMemory(true, true);
    }

    public GHPublicTransit forMobile() {
        // make new index faster (but unprecise) and disable searchRegion
        preciseIndexResolution(500);
        return memoryMapped();
    }

    /**
     * Precise location resolution index means also more space (disc/RAM) could
     * be consumed and probably slower query times, which would be e.g. not
     * suitable for Android. The resolution specifies the tile width (in meter).
     */
    public GHPublicTransit preciseIndexResolution(int precision) {
        preciseIndexResolution = precision;
        return this;
    }

    public GHPublicTransit setInMemory(boolean inMemory, boolean storeOnFlush) {
        if (inMemory) {
            this.inMemory = true;
            this.memoryMapped = false;
            this.storeOnFlush = storeOnFlush;
        } else {
            memoryMapped();
        }
        return this;
    }

    public GHPublicTransit memoryMapped() {
        this.inMemory = false;
        memoryMapped = true;
        return this;
    }

    public Graph graph() {
        return graph;
    }
    
    public GHPublicTransit gtfsFile(String file) {
        if (Helper.isEmpty(file)) {
            throw new IllegalArgumentException("OSM file cannot be empty.");
        }
        this.gtfsFile = file;
        return this;
    }

    public LocationTime2IDIndex index() {
        return index;
    }

    public AlgorithmPreparation preparation() {
        return prepare;
    }

    private GHPublicTransit importGTFS(String graphHopperLocation, String gtfsFile) {
        graphHopperLocation(graphHopperLocation);
        logger.info("start creating graph from " + gtfsFile);
        GTFSReader reader = new GTFSReader(graph);
        try {
            File file = new File(gtfsFile);
            reader.setDefaultAlightTime(defaultAlightTime);
            reader.load(file);
            reader.close();
            graph = reader.graph();
        } catch (IOException ex) {
            throw new RuntimeException("Could not load GTFS file ", ex);
        }
        properties.putCurrentVersions();
        initTransitIndex();
        flush();
        optimize();

        return this;
    }

    private void printInfo(StorableProperties props) {
        String versionInfoStr = "";
        if (props != null) {
            versionInfoStr = " | load:" + props.versionsToString();
        }

        logger.info("version " + Constants.VERSION
                + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")"
                + versionInfoStr);
        logger.info("graph " + graph.toString());
    }

    public GHPublicTransit importOrLoad() {
        if (!load(ghLocation)) {
            printInfo(null);
            importGTFS(ghLocation, gtfsFile);
        } else {
            printInfo(properties());
        }
        return this;
    }

    StorableProperties properties() {
        return properties;
    }

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a
     * folder) and if no such folder exist it'll create a graph from the
     * provided osm file (property 'osm'). A property 'size' is used to
     * preinstantiate a datastructure/graph to avoid over-memory allocation or
     * reallocation (default is 5mio)
     *
     * @param graphHopperFolder is the folder containing graphhopper files
     * (which can be compressed too)
     */
    public boolean load(String graphHopperFolder) {
        if (Helper.isEmpty(graphHopperFolder)) {
            throw new IllegalStateException("graphHopperLocation is not specified. call init before");
        }
        if (graph != null) {
            throw new IllegalStateException("graph is already loaded");
        }

        if (graphHopperFolder.indexOf(".") < 0) {
            if (new File(graphHopperFolder + "-gh").exists()) {
                graphHopperFolder += "-gh";
            } else if (graphHopperFolder.endsWith(".zip")) {
                throw new IllegalArgumentException("To import an GTFS file you need to use importOrLoad");
            }
        }
        graphHopperLocation(graphHopperFolder);
        Directory dir;
        if (memoryMapped) {
            dir = new MMapDirectory(ghLocation);
        } else if (inMemory) {
            dir = new RAMDirectory(ghLocation, storeOnFlush);
        } else {
            throw new IllegalArgumentException("either memory mapped or in-memory has to be specified!");
        }

        properties = new StorableProperties(dir, "properties");
        graph = new GraphStorage(dir);
        prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, defaultAlgorithm, new PublicTransitFlagEncoder());

        if (!graph.loadExisting()) {
            return false;
        }

        properties.loadExisting();
        properties.checkVersions(false);
        if ("false".equals(properties.get("prepare.done"))) {
            prepare();
        }

        initTransitIndex();
        return true;
    }

    public void prepare() {
        boolean tmpPrepare = doPrepare && prepare != null;
        properties.put("prepare.done", tmpPrepare);
        if (tmpPrepare) {
            logger.info("calling prepare.doWork ... (" + Helper.memInfo() + ")");
            prepare.doWork();
        }
    }

    @Override
    public GHResponse route(GHRequest request) {
        request.check();
        StopWatch sw = new StopWatch().start();
        GHResponse rsp = new GHResponse();


        EdgeFilter edgeFilter = new DefaultEdgeFilter(request.vehicle());
        int from = index.findID(request.from().lat, request.from().lon, request.from().getTime());
        int to = index.findExitNode(request.to().lat, request.to().lon);
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        if (from < 0) {
            rsp.addError(new IllegalArgumentException("Cannot find point 1: " + request.from()));
        }
        if (to < 0) {
            rsp.addError(new IllegalArgumentException("Cannot find point 2: " + request.to()));
        }
        if (from == to) {
            rsp.addError(new IllegalArgumentException("Point 1 is equal to point 2"));
        }

        sw = new StopWatch().start();
        RoutingAlgorithm algo = null;
        prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.algorithm(), request.vehicle());
        algo = prepare.createAlgo();
        algo.type(request.type());
        if (rsp.hasError()) {
            return rsp;
        }
        debug += ", algoInit:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        Path path = algo.calcPath(from, to);
        debug += ", " + algo.name() + "-routing:" + sw.stop().getSeconds() + "s"
                + ", " + path.debugInfo();
        PointList points = path.calcPoints();
        return rsp.points(points).distance(path.distance()).time(path.time()).debugInfo(debug);
    }

    private void initTransitIndex() {
        Directory dir = graph.directory();
        if (preciseIndexResolution > 0) {
            LocationTime2NodeNtree tmpIndex = new LocationTime2NodeNtree(graph, dir);
            tmpIndex.resolution(preciseIndexResolution);
            tmpIndex.edgeCalcOnFind(edgeCalcOnSearch);
            tmpIndex.searchRegion(searchRegion);
            index = tmpIndex;
        } else {
            throw new RuntimeException("Needs a preciseIndexResolution > 0 ");
        }
        if (!index.loadExisting()) {
            index.prepareIndex();
        }
    }

    private void optimize() {
        logger.info("optimizing ... (" + Helper.memInfo() + ")");
        graph.optimize();
        logger.info("finished optimize (" + Helper.memInfo() + ")");
    }

    /**
     * Sets the graphhopper folder.
     */
    public GHPublicTransit graphHopperLocation(String ghLocation) {
        if (ghLocation == null) {
            throw new NullPointerException("graphhopper location cannot be null");
        }
        this.ghLocation = ghLocation;
        return this;
    }

    private void flush() {
        logger.info("flushing graph " + graph.toString() + ", " + Helper.memInfo() + ")");
        graph.flush();
        properties.flush();

    }
}
