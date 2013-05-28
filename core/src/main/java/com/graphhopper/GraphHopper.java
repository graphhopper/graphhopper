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
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.RoutingAlgorithmSpecialAreaTests;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.DefaultTurnCostsCalc;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.GraphStorageTurnCosts;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.index.Location2IDQuadtree;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.Location2NodesNtree;
import com.graphhopper.storage.index.Location2NodesNtreeLG;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Constants;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 *
 * @see GraphHopperAPI
 * @author Peter Karich
 */
public class GraphHopper implements GraphHopperAPI {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args);
        hopper.importOrLoad();
        RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(hopper);
        if (args.getBool("graph.testIT", false))
            tests.start();
    }
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // for graph:
    private GraphStorage graph;
    private String ghLocation = "";
    private boolean inMemory = true;
    private boolean storeOnFlush = true;
    private boolean memoryMapped;
    private boolean sortGraph = false;
    boolean removeZipped = true;
    // for routing:
    private boolean simplifyRequest = true;
    private String defaultAlgorithm = "bidijkstra";
    // for index:
    private Location2IDIndex index;
    private int preciseIndexResolution = 1000;
    private boolean turnCosts = false;
    private boolean edgeCalcOnSearch = true;
    private boolean searchRegion = true;
    // for prepare
    private AlgorithmPreparation prepare;
    private boolean doPrepare = true;
    private boolean chUsage = false;
    private boolean chFast = true;
    // for OSM import:
    private String osmFile;
    private AcceptWay acceptWay = new AcceptWay("CAR");
	private long expectedNodes = 10;
    private double wayPointMaxDistance = 1;
    private int periodicUpdates = 3;
    private int lazyUpdates = 10;
    private int neighborUpdates = 20;
    private StorableProperties properties;

    public GraphHopper() {
    }

    /**
     * For testing
     */
    GraphHopper(GraphStorage g) {
        this();
        this.graph = g;
        initIndex();
    }

    public GraphHopper acceptWay(AcceptWay acceptWay) {
        this.acceptWay = acceptWay;
        return this;
    }

    public AcceptWay acceptWay() {
        return acceptWay;
    }

    public GraphHopper forServer() {
        // simplify to reduce network IO
        simplifyRequest(true);
        preciseIndexResolution(1000);
        return setInMemory(true, true);
    }

    public GraphHopper forDesktop() {
        simplifyRequest(false);
        preciseIndexResolution(1000);
        return setInMemory(true, true);
    }

    public GraphHopper forMobile() {
        simplifyRequest(false);
        // make new index faster (but unprecise) and disable searchRegion
        preciseIndexResolution(500);
        return memoryMapped();
    }

    /**
     * Precise location resolution index means also more space (disc/RAM) could
     * be consumed and probably slower query times, which would be e.g. not
     * suitable for Android. The resolution specifies the tile width (in meter).
     */
    public GraphHopper preciseIndexResolution(int precision) {
        preciseIndexResolution = precision;
        return this;
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
     * @param enable if fastest route should be calculated (instead of shortest)
     */
    public GraphHopper chShortcuts(boolean enable, boolean fast) {
        chUsage = enable;
        chFast = fast;
        if (chUsage)
            defaultAlgorithm = "bidijkstra";
        return this;
    }
    
    /**
     * Enables the usage of turn restrictions when routing.
     * 
     * Currently not available for contraction hierarchies
     */
    public GraphHopper enableTurnCosts() {
    	turnCosts = true;
        return this;
    }

    /**
     * This method specifies if the returned path should be simplified or not,
     * via douglas-peucker or similar algorithm.
     */
    private GraphHopper simplifyRequest(boolean doSimplify) {
        this.simplifyRequest = doSimplify;
        return this;
    }

    /**
     * Sets the graphhopper folder.
     */
    public GraphHopper graphHopperLocation(String ghLocation) {
        if (ghLocation == null)
            throw new NullPointerException("graphhopper location cannot be null");
        this.ghLocation = ghLocation;
        return this;
    }

    public String graphHopperLocation() {
        return ghLocation;
    }

    public GraphHopper osmFile(String strOsm) {
        if (Helper.isEmpty(strOsm))
            throw new IllegalArgumentException("OSM file cannot be empty.");
        osmFile = strOsm;
        return this;
    }

    public String osmFile() {
        return osmFile;
    }

    public Graph graph() {
        return graph;
    }

    public Location2IDIndex index() {
        return index;
    }

    public AlgorithmPreparation preparation() {
        return prepare;
    }

    /**
     * @deprecated until #12 is fixed
     */
    public GraphHopper sortGraph(boolean sortGraph) {
        this.sortGraph = sortGraph;
        return this;
    }

    public GraphHopper init(CmdArgs args) throws IOException {
        if (!Helper.isEmpty(args.get("config", ""))) {
            CmdArgs tmp = CmdArgs.readFromConfig(args.get("config", ""), "graphhopper.config");
            // command line configuration overwrites the ones in the config file
            tmp.merge(args);
            args = tmp;
        }

        String tmpOsmFile = args.get("osmreader.osm", "");
        if (!Helper.isEmpty(tmpOsmFile))
            osmFile = tmpOsmFile;
        String graphHopperFolder = args.get("graph.location", "");
        if (Helper.isEmpty(graphHopperFolder) && Helper.isEmpty(ghLocation)) {
            if (Helper.isEmpty(osmFile))
                throw new IllegalArgumentException("You need to specify an OSM file.");

            graphHopperFolder = Helper.pruneFileEnd(osmFile) + "-gh";
        }

        // graph
        graphHopperLocation(graphHopperFolder);
        expectedNodes = args.getLong("graph.expectedSize", 10 * 1000);
        String dataAccess = args.get("graph.dataaccess", "inmemory+save");
        if ("mmap".equalsIgnoreCase(dataAccess)) {
            memoryMapped = true;
        } else {
            if ("inmemory+save".equalsIgnoreCase(dataAccess)) {
                setInMemory(true, true);
            } else
                setInMemory(true, false);
        }
        sortGraph = args.getBool("graph.doSort", false);
        removeZipped = args.getBool("graph.removeZipped", true);

        // prepare
        doPrepare = args.getBool("prepare.doPrepare", true);
        String chShortcuts = args.get("prepare.chShortcuts", "no");
        boolean levelGraph = "true".equals(chShortcuts)
                || "fastest".equals(chShortcuts) || "shortest".equals(chShortcuts);
        if (levelGraph)
            chShortcuts(true, !"shortest".equals(chShortcuts));
        if (args.has("prepare.updates.periodic"))
            periodicUpdates = args.getInt("prepare.updates.periodic", -1);
        if (args.has("prepare.updates.lazy"))
            lazyUpdates = args.getInt("prepare.updates.lazy", -1);
        if (args.has("prepare.updates.neighbor"))
            neighborUpdates = args.getInt("prepare.updates.neighbor", -1);

        // routing
        defaultAlgorithm = args.get("routing.defaultAlgorithm", defaultAlgorithm);

        // osm import
        wayPointMaxDistance = args.getDouble("osmreader.wayPointMaxDistance", 1);
        String type = args.get("osmreader.acceptWay", "CAR");
        acceptWay = new AcceptWay(type);

        // index
        preciseIndexResolution = args.getInt("index.highResolution", 1000);
        return this;
    }

    private void printInfo(StorableProperties props) {
        String versionInfoStr = "";
        if (props != null)
            versionInfoStr = " | load:" + props.versionsToString();

        logger.info("version " + Constants.VERSION
                + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")"
                + versionInfoStr);
        logger.info("graph " + graph.toString());
    }

    public GraphHopper importOrLoad() {
        if (!load(ghLocation)) {
            printInfo(null);
            importOSM(ghLocation, osmFile);
        } else
            printInfo(properties());
        return this;
    }

    private GraphHopper importOSM(String graphHopperLocation, String strOsm) {
        graphHopperLocation(graphHopperLocation);
        try {
            OSMReader reader = importOSM(strOsm);
            graph = reader.graph();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot parse file " + strOsm, ex);
        }
        cleanUp();
        optimize();
        prepare();
        flush();
        initIndex();
        return this;
    }

    protected OSMReader importOSM(String file) throws IOException {
        osmFile(file);
        File osmTmpFile = new File(osmFile);
        if (!osmTmpFile.exists())
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmTmpFile.getAbsolutePath());

        logger.info("start creating graph from " + file);
        OSMReader reader = new OSMReader(graph, expectedNodes);
        reader.acceptWay(acceptWay);
        reader.wayPointMaxDistance(wayPointMaxDistance);

        properties.put("osmreader.acceptWay", acceptWay.toString());
        properties.putCurrentVersions();
        String info = graph.getClass().getSimpleName()
                + "|" + graph.directory().getClass().getSimpleName()
                + "|" + properties.versionsToString();
        logger.info("using " + info + ", accepts:" + acceptWay + ", memory:" + Helper.memInfo());
        reader.osm2Graph(osmTmpFile);
        return reader;
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
    @Override
    public boolean load(String graphHopperFolder) {
        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("graphHopperLocation is not specified. call init before");
        if (graph != null)
            throw new IllegalStateException("graph is already loaded");

        if (graphHopperFolder.indexOf(".") < 0) {
            if (new File(graphHopperFolder + "-gh").exists())
                graphHopperFolder += "-gh";
            else if (graphHopperFolder.endsWith(".osm") || graphHopperFolder.endsWith(".xml"))
                throw new IllegalArgumentException("To import an osm file you need to use importOrLoad");
        } else {
            File compressed = new File(graphHopperFolder + ".ghz");
            if (compressed.exists() && !compressed.isDirectory()) {
                try {
                    Helper.unzip(compressed.getAbsolutePath(), graphHopperFolder, removeZipped);
                } catch (IOException ex) {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath() + " to " + graphHopperFolder, ex);
                }
            }
        }
        graphHopperLocation(graphHopperFolder);
        Directory dir;
        if (memoryMapped) {
            dir = new MMapDirectory(ghLocation);
        } else if (inMemory) {
            dir = new RAMDirectory(ghLocation, storeOnFlush);
        } else
            throw new IllegalArgumentException("either memory mapped or in-memory has to be specified!");

        properties = new StorableProperties(dir, "properties");
        if (chUsage) {
            graph = new LevelGraphStorage(dir);
            PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies();

            EdgePropertyEncoder encoder = acceptWay.getSingle();
            if (chFast)
                tmpPrepareCH.type(new FastestCalc(encoder));
            else
                tmpPrepareCH.type(new ShortestCalc());
            tmpPrepareCH.vehicle(encoder);
            tmpPrepareCH.periodicUpdates(periodicUpdates).
                    lazyUpdates(lazyUpdates).
                    neighborUpdates(neighborUpdates);

            prepare = tmpPrepareCH;
            prepare.graph(graph);
        } else if(turnCosts) { 
            graph = new GraphStorageTurnCosts(dir, true);
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, defaultAlgorithm, new CarFlagEncoder());
        } else {
            graph = new GraphStorage(dir);
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, defaultAlgorithm, new CarFlagEncoder());
        }
                
        if (!graph.loadExisting())
            return false;

        properties.loadExisting();

        // overwrite configured accept way
        String acceptStr = properties.get("osmreader.acceptWay");
        if (!acceptStr.isEmpty())
            acceptWay = new AcceptWay(acceptStr);
        properties.checkVersions(false);
        if ("false".equals(properties.get("prepare.done")))
            prepare();

        initIndex();
        return true;
    }
    
    private boolean supportsVehicle(EdgePropertyEncoder encoder) {
        return acceptWay.accepts(encoder);
    }

    @Override
    public GHResponse route(GHRequest request) {
        request.check();
        StopWatch sw = new StopWatch().start();
        GHResponse rsp = new GHResponse();

        if (!supportsVehicle(request.vehicle())) {
            rsp.addError(new IllegalArgumentException("Vehicle " + request.vehicle() + " unsupported. Supported are: " + acceptWay()));
            return rsp;
        }
        
        if(request.turnCosts() == null){
            //if turn costs calculation has not been set, the calculation which fits best to requested vehicle and weight calculation will be chosen
            request.turnCosts(new DefaultTurnCostsCalc(request.vehicle(), request.type()));
        }

        EdgeFilter edgeFilter = new DefaultEdgeFilter(request.vehicle());
        int from = index.findClosest(request.from().lat, request.from().lon, edgeFilter).closestNode();
        int to = index.findClosest(request.to().lat, request.to().lon, edgeFilter).closestNode();
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        if (from < 0)
            rsp.addError(new IllegalArgumentException("Cannot find point 1: " + request.from()));
        if (to < 0)
            rsp.addError(new IllegalArgumentException("Cannot find point 2: " + request.to()));
        if (from == to)
            rsp.addError(new IllegalArgumentException("Point 1 is equal to point 2"));

        sw = new StopWatch().start();
        RoutingAlgorithm algo = null;
        if (chUsage) {
            if (request.algorithm().equals("dijkstrabi"))
                algo = prepare.createAlgo();
            else if (request.algorithm().equals("astarbi"))
                algo = ((PrepareContractionHierarchies) prepare).createAStar();
            else
                // or use defaultAlgorithm here?
                rsp.addError(new IllegalStateException("Only dijkstrabi and astarbi is supported for LevelGraph (using contraction hierarchies)!"));
        } else {
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.algorithm(), request.vehicle());
            algo = prepare.createAlgo();
            algo.type(request.type());
            algo.turnCosts(request.turnCosts());
        }
        if (rsp.hasError())
            return rsp;
        debug += ", algoInit:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        Path path = algo.calcPath(from, to);
        debug += ", " + algo.name() + "-routing:" + sw.stop().getSeconds() + "s"
                + ", " + path.debugInfo();
        PointList points = path.calcPoints();
        simplifyRequest = request.getHint("simplifyRequest", simplifyRequest);
        if (simplifyRequest) {
            sw = new StopWatch().start();
            int orig = points.size();
            double minPathPrecision = request.getHint("douglas.minprecision", 1d);
            if (minPathPrecision > 0)
                new DouglasPeucker().maxDistance(minPathPrecision).simplify(points);
            debug += ", simplify (" + orig + "->" + points.size() + "):" + sw.stop().getSeconds() + "s";
        }
        return rsp.points(points).distance(path.distance()).time(path.time()).debugInfo(debug);
    }

    private void initIndex() {
        Directory dir = graph.directory();
        if (preciseIndexResolution > 0) {
            Location2NodesNtree tmpIndex;
            if (graph instanceof LevelGraph)
                tmpIndex = new Location2NodesNtreeLG((LevelGraph) graph, dir);
            else
                tmpIndex = new Location2NodesNtree(graph, dir);
            tmpIndex.resolution(preciseIndexResolution);
            tmpIndex.edgeCalcOnFind(edgeCalcOnSearch);
            tmpIndex.searchRegion(searchRegion);
            index = tmpIndex;
        } else {
            index = new Location2IDQuadtree(graph, dir);
            index.resolution(Helper.calcIndexSize(graph.bounds()));
        }
        if (!index.loadExisting())
            index.prepareIndex();
    }

    private void optimize() {
        logger.info("optimizing ... (" + Helper.memInfo() + ")");
        graph.optimize();
        logger.info("finished optimize (" + Helper.memInfo() + ")");

        // move this into the GraphStorage.optimize method?
        if (sortGraph) {
            logger.info("sorting ... (" + Helper.memInfo() + ")");
            GraphStorage newGraph = GHUtility.newStorage(graph);
            GHUtility.sortDFS(graph, newGraph);
            graph = newGraph;
        }
    }

    public void prepare() {
        boolean tmpPrepare = doPrepare && prepare != null;
        properties.put("prepare.done", tmpPrepare);
        if (tmpPrepare) {
            if (prepare instanceof PrepareContractionHierarchies && acceptWay.countVehicles() > 1)
                throw new IllegalArgumentException("Contraction hierarchies preparation "
                        + "requires (at the moment) only one vehicle. But was:" + acceptWay);
            logger.info("calling prepare.doWork ... (" + Helper.memInfo() + ")");
            prepare.doWork();
        }
    }

    protected void cleanUp() {
        int prev = graph.nodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graph);
        logger.info("start finding subnetworks, " + Helper.memInfo());
        preparation.doWork();
        int n = graph.nodes();
        logger.info("edges: " + graph.getAllEdges().maxId()
                + ", nodes " + n + ", there were " + preparation.subNetworks()
                + " subnetworks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + preparation.findSubnetworks().size());
    }

    private void flush() {
        logger.info("flushing graph " + graph.toString() + ", " + Helper.memInfo() + ")");
        graph.flush();
        properties.flush();
    }

    void close() {
        graph.close();
    }

    StorableProperties properties() {
        return properties;
    }
}
