/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper;

import com.graphhopper.reader.OSMReader;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.index.Location2IDQuadtree;
// import com.graphhopper.storage.StorableProperties;
import com.graphhopper.storage.index.Location2NodesNtree;
import com.graphhopper.storage.index.Location2NodesNtreeLG;
import com.graphhopper.util.*;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 * <p/>
 * @see GraphHopperAPI
 * @author Peter Karich
 */
public class GraphHopper implements GraphHopperAPI
{
    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args);
        hopper.importOrLoad();
        RoutingAlgorithmSpecialAreaTests tests = new RoutingAlgorithmSpecialAreaTests(hopper);
        if (args.getBool("graph.testIT", false))
        {
            tests.start();
        }
    }
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // for graph:
    private GraphStorage graph;
    private String ghLocation = "";
    private DAType dataAccessType = DAType.RAM;
    private boolean sortGraph = false;
    boolean removeZipped = true;
    // for routing:
    private boolean simplifyRequest = true;
    private String defaultAlgorithm = "bidijkstra";
    // for index:
    private Location2IDIndex index;
    private int preciseIndexResolution = 500;
    private boolean edgeCalcOnSearch = true;
    private boolean searchRegion = true;
    // for prepare
    private AlgorithmPreparation prepare;
    private boolean doPrepare = true;
    private boolean chUsage = false;
    private boolean chFast = true;
    private int periodicUpdates = 3;
    private int lazyUpdates = 10;
    private int neighborUpdates = 20;
    // for OSM import:
    private String osmFile;
    private EncodingManager encodingManager;
    private long expectedCapacity = 100;
    private double wayPointMaxDistance = 1;
    private int workerThreads = -1;
    private int defaultSegmentSize = -1;
    private boolean enableInstructions = true;

    public GraphHopper()
    {
    }

    /**
     * For testing
     */
    GraphHopper( GraphStorage g )
    {
        this();
        this.graph = g;
        initIndex();
    }

    public GraphHopper setEncodingManager( EncodingManager acceptWay )
    {
        this.encodingManager = acceptWay;
        return this;
    }

    public EncodingManager getEncodingManager()
    {
        return encodingManager;
    }

    public GraphHopper forServer()
    {
        // simplify to reduce network IO
        setSimplifyRequest(true);
        setPreciseIndexResolution(500);
        return setInMemory(true, true);
    }

    public GraphHopper forDesktop()
    {
        setSimplifyRequest(false);
        setPreciseIndexResolution(500);
        return setInMemory(true, true);
    }

    public GraphHopper forMobile()
    {
        setSimplifyRequest(false);
        setPreciseIndexResolution(500);
        return setMemoryMapped();
    }

    /**
     * Precise location resolution index means also more space (disc/RAM) could be consumed and
     * probably slower query times, which would be e.g. not suitable for Android. The resolution
     * specifies the tile width (in meter).
     */
    public GraphHopper setPreciseIndexResolution( int precision )
    {
        preciseIndexResolution = precision;
        return this;
    }

    public GraphHopper setInMemory( boolean inMemory, boolean storeOnFlush )
    {
        if (inMemory)
        {
            if (storeOnFlush)
                dataAccessType = DAType.RAM_STORE;
            else
                dataAccessType = DAType.RAM;
        } else
        {
            setMemoryMapped();
        }
        return this;
    }

    public GraphHopper setMemoryMapped()
    {
        dataAccessType = DAType.MMAP;
        return this;
    }

    public GraphHopper doPrepare( boolean doPrepare )
    {
        this.doPrepare = doPrepare;
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times.
     * <p/>
     * @param enable if fastest route should be calculated (instead of shortest)
     */
    public GraphHopper setCHShortcuts( boolean enable, boolean fast )
    {
        chUsage = enable;
        chFast = fast;
        if (chUsage)
        {
            defaultAlgorithm = "bidijkstra";
        }
        return this;
    }

    /**
     * This method specifies if the import should include way names to be able to return
     * instructions for a route.
     */
    public GraphHopper setEnableInstructions( boolean b )
    {
        enableInstructions = b;
        return this;
    }

    /**
     * This method specifies if the returned path should be simplified or not, via douglas-peucker
     * or similar algorithm.
     */
    private GraphHopper setSimplifyRequest( boolean doSimplify )
    {
        this.simplifyRequest = doSimplify;
        return this;
    }

    /**
     * Sets the graphhopper folder.
     */
    public GraphHopper setGraphHopperLocation( String ghLocation )
    {
        if (ghLocation == null)
            throw new NullPointerException("graphhopper location cannot be null");

        this.ghLocation = ghLocation;
        return this;
    }

    public String getGraphHopperLocation()
    {
        return ghLocation;
    }

    /**
     * This file can be an osm xml (.osm), a compressed xml (.osm.zip or .osm.gz) or a protobuf file
     * (.pbf).
     */
    public GraphHopper setOSMFile( String osmFileStr )
    {
        if (Helper.isEmpty(osmFileStr))
            throw new IllegalArgumentException("OSM file cannot be empty.");

        osmFile = osmFileStr;
        return this;
    }

    public String getOSMFile()
    {
        return osmFile;
    }

    public Graph getGraph()
    {
        return graph;
    }

    public void setGraph( GraphStorage graph )
    {
        this.graph = graph;
    }

    public Location2IDIndex getIndex()
    {
        return index;
    }

    public AlgorithmPreparation getPreparation()
    {
        return prepare;
    }

    /**
     * @deprecated until #12 is fixed
     */
    public GraphHopper setSortGraph( boolean sortGraph )
    {
        this.sortGraph = sortGraph;
        return this;
    }

    public GraphHopper init( CmdArgs args ) throws IOException
    {
        if (!Helper.isEmpty(args.get("config", "")))
        {
            CmdArgs tmp = CmdArgs.readFromConfig(args.get("config", ""), "graphhopper.config");
            // command line configuration overwrites the ones in the config file
            tmp.merge(args);
            args = tmp;
        }

        String tmpOsmFile = args.get("osmreader.osm", "");
        if (!Helper.isEmpty(tmpOsmFile))
            osmFile = tmpOsmFile;

        String graphHopperFolder = args.get("graph.location", "");
        if (Helper.isEmpty(graphHopperFolder) && Helper.isEmpty(ghLocation))
        {
            if (Helper.isEmpty(osmFile))
                throw new IllegalArgumentException("You need to specify an OSM file.");

            graphHopperFolder = Helper.pruneFileEnd(osmFile) + "-gh";
        }

        // graph
        setGraphHopperLocation(graphHopperFolder);
        expectedCapacity = args.getLong("graph.expectedCapacity", expectedCapacity);
        defaultSegmentSize = args.getInt("graph.dataaccess.segmentSize", defaultSegmentSize);
        String dataAccess = args.get("graph.dataaccess", "RAM_STORE").toUpperCase();
        if (dataAccess.contains("MMAP"))
        {
            setMemoryMapped();
        } else
        {
            if (dataAccess.contains("SAVE") || dataAccess.contains("INMEMORY"))
                throw new IllegalStateException("configuration names for dataAccess changed. Use eg. RAM or RAM_STORE");

            if (dataAccess.contains("RAM_STORE"))
                setInMemory(true, true);
            else
                setInMemory(true, false);
        }

        if (dataAccess.contains("SYNC"))
            dataAccessType = new DAType(dataAccessType, true);

        sortGraph = args.getBool("graph.doSort", sortGraph);
        removeZipped = args.getBool("graph.removeZipped", removeZipped);

        // prepare
        doPrepare = args.getBool("prepare.doPrepare", doPrepare);
        String chShortcuts = args.get("prepare.chShortcuts", "fastest");
        boolean levelGraph = "true".equals(chShortcuts)
                || "fastest".equals(chShortcuts) || "shortest".equals(chShortcuts);
        if (levelGraph)
            setCHShortcuts(true, !"shortest".equals(chShortcuts));

        if (args.has("prepare.updates.periodic"))
            periodicUpdates = args.getInt("prepare.updates.periodic", periodicUpdates);

        if (args.has("prepare.updates.lazy"))
            lazyUpdates = args.getInt("prepare.updates.lazy", lazyUpdates);

        if (args.has("prepare.updates.neighbor"))
            neighborUpdates = args.getInt("prepare.updates.neighbor", neighborUpdates);

        // routing
        defaultAlgorithm = args.get("routing.defaultAlgorithm", defaultAlgorithm);

        // osm import
        wayPointMaxDistance = args.getDouble("osmreader.wayPointMaxDistance", wayPointMaxDistance);
        String type = args.get("osmreader.acceptWay", "CAR");
        encodingManager = new EncodingManager(type);
        workerThreads = args.getInt("osmreader.workerThreads", workerThreads);
        enableInstructions = args.getBool("osmreader.instructions", enableInstructions);

        // index
        preciseIndexResolution = args.getInt("index.highResolution", preciseIndexResolution);
        return this;
    }

    private void printInfo()
    {
        logger.info("version " + Constants.VERSION
                + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")");
        logger.info("graph " + graph.toString() + ", details:" + graph.toDetailsString());
    }

    public GraphHopper importOrLoad()
    {
        if (!load(ghLocation))
        {
            printInfo();
            importOSM(ghLocation, osmFile);
        } else
        {
            printInfo();
        }
        return this;
    }

    private GraphHopper importOSM( String graphHopperLocation, String osmFileStr )
    {
        if (encodingManager == null)
            throw new IllegalStateException("No encodingManager was specified");

        setGraphHopperLocation(graphHopperLocation);
        try
        {
            importOSM(osmFileStr);
        } catch (IOException ex)
        {
            throw new RuntimeException("Cannot parse OSM file " + osmFileStr, ex);
        }
        postProcessing();
        cleanUp();
        optimize();
        prepare();
        flush();
        initIndex();
        return this;
    }

    protected OSMReader importOSM( String _osmFile ) throws IOException
    {
        if (graph == null)
            throw new IllegalStateException("Load or init graph before import OSM data");

        setOSMFile(_osmFile);
        File osmTmpFile = new File(osmFile);
        if (!osmTmpFile.exists())
        {
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmTmpFile.getAbsolutePath());
        }

        logger.info("start creating graph from " + osmFile);
        OSMReader reader = new OSMReader(graph, expectedCapacity).
                setWorkerThreads(workerThreads).
                setEncodingManager(encodingManager).
                setWayPointMaxDistance(wayPointMaxDistance).
                setEnableInstructions(enableInstructions);
        logger.info("using " + graph.toString() + ", memory:" + Helper.getMemInfo());
        reader.doOSM2Graph(osmTmpFile);
        return reader;
    }

    /**
     * Opens or creates a graph. The specified args need a property 'graph' (a folder) and if no
     * such folder exist it'll create a graph from the provided osm file (property 'osm'). A
     * property 'size' is used to preinstantiate a datastructure/graph to avoid over-memory
     * allocation or reallocation (default is 5mio)
     * <p/>
     * @param graphHopperFolder is the folder containing graphhopper files (which can be compressed
     * too)
     */
    @Override
    public boolean load( String graphHopperFolder )
    {
        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("graphHopperLocation is not specified. call init before");

        if (graph != null)
            throw new IllegalStateException("graph is already loaded");

        if (graphHopperFolder.endsWith("-gh"))
        {
            // do nothing  
        } else if (graphHopperFolder.endsWith(".osm") || graphHopperFolder.endsWith(".xml"))
        {
            throw new IllegalArgumentException("To import an osm file you need to use importOrLoad");
        } else if (graphHopperFolder.indexOf(".") < 0)
        {
            if (new File(graphHopperFolder + "-gh").exists())
                graphHopperFolder += "-gh";
        } else
        {
            File compressed = new File(graphHopperFolder + ".ghz");
            if (compressed.exists() && !compressed.isDirectory())
            {
                try
                {
                    new Unzipper().unzip(compressed.getAbsolutePath(), graphHopperFolder, removeZipped);
                } catch (IOException ex)
                {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath() + " to " + graphHopperFolder, ex);
                }
            }
        }
        setGraphHopperLocation(graphHopperFolder);

        GHDirectory dir = new GHDirectory(ghLocation, dataAccessType);
        if (chUsage)
            graph = new LevelGraphStorage(dir, encodingManager);
        else
            graph = new GraphStorage(dir, encodingManager);

        graph.setSegmentSize(defaultSegmentSize);
        if (!graph.loadExisting())
            return false;

        postProcessing();
        initIndex();
        return true;
    }

    private void postProcessing()
    {
        encodingManager = graph.getEncodingManager();
        if (chUsage)
        {
            PrepareContractionHierarchies tmpPrepareCH;
            FlagEncoder encoder = encodingManager.getSingle();
            if (chFast)
                tmpPrepareCH = new PrepareContractionHierarchies(encoder, new FastestCalc(encoder));
            else
                tmpPrepareCH = new PrepareContractionHierarchies(encoder, new ShortestCalc());

            tmpPrepareCH.setPeriodicUpdates(periodicUpdates).
                    setLazyUpdates(lazyUpdates).
                    setNeighborUpdates(neighborUpdates);

            prepare = tmpPrepareCH;
            prepare.setGraph(graph);
        }

        if ("false".equals(graph.getProperties().get("prepare.done")))
            prepare();
    }

    private boolean setSupportsVehicle( String encoder )
    {
        return encodingManager.supports(encoder);
    }

    @Override
    public GHResponse route( GHRequest request )
    {
        request.check();
        StopWatch sw = new StopWatch().start();
        GHResponse rsp = new GHResponse();

        if (!setSupportsVehicle(request.getVehicle()))
        {
            rsp.addError(new IllegalArgumentException("Vehicle " + request.getVehicle() + " unsupported. Supported are: " + getEncodingManager()));
            return rsp;
        }

        EdgeFilter edgeFilter = new DefaultEdgeFilter(encodingManager.getEncoder(request.getVehicle()));
        int from = index.findClosest(request.getFrom().lat, request.getFrom().lon, edgeFilter).getClosestNode();
        int to = index.findClosest(request.getTo().lat, request.getTo().lon, edgeFilter).getClosestNode();
        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        if (from < 0)
            rsp.addError(new IllegalArgumentException("Cannot find point 1: " + request.getFrom()));

        if (to < 0)
            rsp.addError(new IllegalArgumentException("Cannot find point 2: " + request.getTo()));

        if (from == to)
            rsp.addError(new IllegalArgumentException("Point 1 is equal to point 2"));

        sw = new StopWatch().start();
        RoutingAlgorithm algo = null;

        if (chUsage)
        {
            if (request.getAlgorithm().equals("dijkstrabi"))
                algo = prepare.createAlgo();
            else if (request.getAlgorithm().equals("astarbi"))
                algo = ((PrepareContractionHierarchies) prepare).createAStar();
            else
                rsp.addError(new IllegalStateException("Only dijkstrabi and astarbi is supported for LevelGraph (using contraction hierarchies)!"));

        } else
        {
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.getAlgorithm(),
                    encodingManager.getEncoder(request.getVehicle()), request.getType());
            algo = prepare.createAlgo();
        }

        if (rsp.hasErrors())
        {
            return rsp;
        }
        debug += ", algoInit:" + sw.stop().getSeconds() + "s";

        sw = new StopWatch().start();
        Path path = algo.calcPath(from, to);
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s"
                + ", " + path.getDebugInfo();
        PointList points = path.calcPoints();
        simplifyRequest = request.getHint("simplifyRequest", simplifyRequest);
        if (simplifyRequest)
        {
            sw = new StopWatch().start();
            int orig = points.getSize();
            double minPathPrecision = request.getHint("douglas.minprecision", 1d);
            if (minPathPrecision > 0)
            {
                new DouglasPeucker().setMaxDistance(minPathPrecision).simplify(points);
            }
            debug += ", simplify (" + orig + "->" + points.getSize() + "):" + sw.stop().getSeconds() + "s";
        }

        enableInstructions = request.getHint("instructions", enableInstructions);
        if (enableInstructions)
        {
            sw = new StopWatch().start();
            rsp.setInstructions(path.calcInstructions());
            debug += ", instructions:" + sw.stop().getSeconds() + "s";
        }
        return rsp.setPoints(points).setDistance(path.getDistance()).setTime(path.getTime()).setDebugInfo(debug);
    }

    private void initIndex()
    {
        Directory dir = graph.getDirectory();
        if (preciseIndexResolution > 0)
        {
            Location2NodesNtree tmpIndex;
            if (graph instanceof LevelGraph)
            {
                tmpIndex = new Location2NodesNtreeLG((LevelGraph) graph, dir);
            } else
            {
                tmpIndex = new Location2NodesNtree(graph, dir);
            }
            tmpIndex.setResolution(preciseIndexResolution);
            tmpIndex.setEdgeCalcOnFind(edgeCalcOnSearch);
            tmpIndex.setSearchRegion(searchRegion);
            index = tmpIndex;
        } else
        {
            index = new Location2IDQuadtree(graph, dir);
            index.setResolution(Helper.calcIndexSize(graph.getBounds()));
        }
        index.setSegmentSize(defaultSegmentSize);
        if (!index.loadExisting())
        {
            index.prepareIndex();
        }
    }

    private void optimize()
    {
        logger.info("optimizing ... (" + Helper.getMemInfo() + ")");
        graph.optimize();
        logger.info("finished optimize (" + Helper.getMemInfo() + ")");

        // move this into the GraphStorage.optimize method?
        if (sortGraph)
        {
            logger.info("sorting ... (" + Helper.getMemInfo() + ")");
            GraphStorage newGraph = GHUtility.newStorage(graph);
            GHUtility.sortDFS(graph, newGraph);
            graph = newGraph;
        }
    }

    public void prepare()
    {
        boolean tmpPrepare = doPrepare && prepare != null;
        graph.getProperties().put("prepare.done", tmpPrepare);
        if (tmpPrepare)
        {
            if (prepare instanceof PrepareContractionHierarchies && encodingManager.getVehicleCount() > 1)
            {
                throw new IllegalArgumentException("Contraction hierarchies preparation "
                        + "requires (at the moment) only one vehicle. But was:" + encodingManager);
            }
            logger.info("calling prepare.doWork ... (" + Helper.getMemInfo() + ")");
            prepare.doWork();
        }
    }

    protected void cleanUp()
    {
        int prev = graph.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graph, encodingManager);
        logger.info("start finding subnetworks, " + Helper.getMemInfo());
        preparation.doWork();
        int n = graph.getNodes();
        // calculate remaining subnetworks
        int remainingSubnetworks = preparation.findSubnetworks().size();
        logger.info("edges: " + graph.getAllEdges().getMaxId()
                + ", nodes " + n + ", there were " + preparation.getSubNetworks()
                + " subnetworks. removed them => " + (prev - n)
                + " less nodes. Remaining subnetworks:" + remainingSubnetworks);
    }

    private void flush()
    {
        logger.info("flushing graph " + graph.toString() + ", details:" + graph.toDetailsString() + ", "
                + Helper.getMemInfo() + ")");
        graph.flush();
    }

    void close()
    {
        if (graph != null)
        {
            graph.close();
        }
        if (index != null)
        {
            index.close();
        }
    }
}
