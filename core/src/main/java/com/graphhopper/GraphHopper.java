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
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.*;
import com.graphhopper.util.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
            tests.start();
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
    // for index:
    private LocationIndex locationIndex;
    private int preciseIndexResolution = 500;
    private boolean searchRegion = true;
    // for prepare
    private int minNetworkSize = 200;
    // for CH prepare
    private AlgorithmPreparation prepare;
    private boolean doPrepare = true;
    private boolean chEnabled = true;
    private String chWeighting = "fastest";
    private int periodicUpdates = 20;
    private int lazyUpdates = 10;
    private int neighborUpdates = 20;
    private double logMessages = 20;
    // for OSM import:
    private String osmFile;
    private EncodingManager encodingManager;
    private long expectedCapacity = 100;
    private double wayPointMaxDistance = 1;
    private int workerThreads = -1;
    private int defaultSegmentSize = -1;
    private boolean turnCosts = false;
    private boolean enableInstructions = true;
    private boolean calcPoints = true;
    private boolean fullyLoaded = false;

    public GraphHopper()
    {
    }

    /**
     * For testing
     */
    GraphHopper loadGraph( GraphStorage g )
    {
        this.graph = g;
        fullyLoaded = true;
        initLocationIndex();
        return this;
    }

    /**
     * Specify which vehicles can be read by this GraphHopper instance. An encoding manager defines
     * how data from every vehicle is written (und read) into edges of the graph.
     */
    public GraphHopper setEncodingManager( EncodingManager acceptWay )
    {
        ensureNotLoaded();
        this.encodingManager = acceptWay;
        return this;
    }

    public EncodingManager getEncodingManager()
    {
        return encodingManager;
    }

    /**
     * Configures the underlying storage to be used on a well equipped server.
     */
    public GraphHopper forServer()
    {
        // simplify to reduce network IO
        setSimplifyRequest(true);
        setPreciseIndexResolution(500);
        return setInMemory(true);
    }

    /**
     * Configures the underlying storage to be used on a Desktop computer with enough RAM but no
     * network latency.
     */
    public GraphHopper forDesktop()
    {
        setSimplifyRequest(false);
        setPreciseIndexResolution(500);
        return setInMemory(true);
    }

    /**
     * Configures the underlying storage to be used on a less powerful machine like Android and
     * Raspberry Pi with only few RAM.
     */
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
        ensureNotLoaded();
        preciseIndexResolution = precision;
        return this;
    }

    /**
     * This method call results in an in-memory graph. Specify storeOnFlush to true if you want that
     * existing data will be loaded FROM disc and all in-memory data will be flushed TO disc after
     * flush is called e.g. while OSM import.
     */
    public GraphHopper setInMemory( boolean storeOnFlush )
    {
        ensureNotLoaded();
        if (storeOnFlush)
            dataAccessType = DAType.RAM_STORE;
        else
            dataAccessType = DAType.RAM;

        return this;
    }

    /**
     * Enable memory mapped configuration if not enough memory is available on the target platform.
     */
    public GraphHopper setMemoryMapped()
    {
        ensureNotLoaded();
        dataAccessType = DAType.MMAP;
        return this;
    }

    /**
     * Not yet stable enough to offer it for everyone
     */
    private GraphHopper setUnsafeMemory()
    {
        ensureNotLoaded();
        dataAccessType = DAType.UNSAFE_STORE;
        return this;
    }

    /**
     * Disables "CH-preparation". Use only if you know what you do.
     */
    public GraphHopper setDoPrepare( boolean doPrepare )
    {
        this.doPrepare = doPrepare;
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times. Enabled by default.
     * <p/>
     * @param weighting can be "fastest", "shortest" or your own weight-calculation type.
     * @see #disableCHShortcuts()
     */
    public GraphHopper setCHShortcuts( String weighting )
    {
        ensureNotLoaded();
        chEnabled = true;
        chWeighting = weighting;
        return this;
    }

    public String getCHWeighting()
    {
        return chWeighting;
    }

    /**
     * Disables contraction hierarchies. Enabled by default.
     */
    public GraphHopper disableCHShortcuts()
    {
        ensureNotLoaded();
        chEnabled = false;
        return this;
    }

    public boolean isCHEnabled()
    {
        return chEnabled;
    }

    /**
     * @return if import of turn restrictions is enabled
     */
    public boolean isEnableTurnRestrictions()
    {
        return turnCosts;
    }

    /**
     * This method specifies if the import should include turn restrictions if available
     */
    public GraphHopper setEnableTurnRestrictions( boolean b )
    {
        ensureNotLoaded();
        turnCosts = b;
        return this;
    }

    /**
     * This method specifies if the import should include way names to be able to return
     * instructions for a route.
     */
    public GraphHopper setEnableInstructions( boolean b )
    {
        ensureNotLoaded();
        enableInstructions = b;
        return this;
    }

    /**
     * This methods enables gps point calculation. If disabled only distance will be calculated.
     */
    public GraphHopper setEnableCalcPoints( boolean b )
    {
        calcPoints = b;
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
        ensureNotLoaded();
        if (ghLocation == null)
            throw new IllegalArgumentException("graphhopper location cannot be null");

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
        ensureNotLoaded();
        if (Helper.isEmpty(osmFileStr))
            throw new IllegalArgumentException("OSM file cannot be empty.");

        osmFile = osmFileStr;
        return this;
    }

    public String getOSMFile()
    {
        return osmFile;
    }

    /**
     * The underlying graph used in algorithms.
     * <p>
     * @throws IllegalStateException if graph is not instantiated.
     */
    public GraphStorage getGraph()
    {
        if (graph == null)
            throw new IllegalStateException("Graph not initialized");

        return graph;
    }

    public void setGraph( GraphStorage graph )
    {
        this.graph = graph;
    }

    /**
     * The location index created from the graph.
     * <p>
     * @throws IllegalStateException if index is not initialized
     */
    public LocationIndex getLocationIndex()
    {
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return locationIndex;
    }

    public AlgorithmPreparation getPreparation()
    {
        return prepare;
    }

    /**
     * Sorts the graph which requires more RAM while import. See #12
     */
    public GraphHopper setSortGraph( boolean sortGraph )
    {
        ensureNotLoaded();
        this.sortGraph = sortGraph;
        return this;
    }

    /*
     * Command line configuration overwrites the ones in the config file
     */
    protected CmdArgs mergeArgsFromConfig( CmdArgs args ) throws IOException
    {
        if (!Helper.isEmpty(args.get("config", "")))
        {
            CmdArgs tmp = CmdArgs.readFromConfig(args.get("config", ""), "graphhopper.config");
            tmp.merge(args);
            return tmp;
        }
        return args;
    }

    /**
     * Reads configuration from a CmdArgs object. Which can be manually filled, or via main(String[]
     * args) ala CmdArgs.read(args) or via configuration file ala
     * CmdArgs.readFromConfig("config.properties", "graphhopper.config")
     */
    public GraphHopper init( CmdArgs args ) throws IOException
    {
        args = mergeArgsFromConfig(args);
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
        } else if (dataAccess.contains("UNSAFE"))
        {
            setUnsafeMemory();
        } else
        {
            if (dataAccess.contains("RAM_STORE"))
                setInMemory(true);
            else
                setInMemory(false);
        }

        if (dataAccess.contains("SYNC"))
            dataAccessType = new DAType(dataAccessType, true);

        sortGraph = args.getBool("graph.doSort", sortGraph);
        removeZipped = args.getBool("graph.removeZipped", removeZipped);
        turnCosts = args.getBool("graph.turnCosts", turnCosts);

        // optimizable prepare
        minNetworkSize = args.getInt("prepare.minNetworkSize", minNetworkSize);

        // prepare CH
        doPrepare = args.getBool("prepare.doPrepare", doPrepare);
        String chShortcuts = args.get("prepare.chShortcuts", "fastest");
        chEnabled = "true".equals(chShortcuts) || "fastest".equals(chShortcuts) || "shortest".equals(chShortcuts);
        if (chEnabled)
            setCHShortcuts(chShortcuts);

        periodicUpdates = args.getInt("prepare.updates.periodic", periodicUpdates);
        lazyUpdates = args.getInt("prepare.updates.lazy", lazyUpdates);
        neighborUpdates = args.getInt("prepare.updates.neighbor", neighborUpdates);
        logMessages = args.getDouble("prepare.logmessages", logMessages);

        // osm import
        wayPointMaxDistance = args.getDouble("osmreader.wayPointMaxDistance", wayPointMaxDistance);
        String flagEncoders = args.get("osmreader.acceptWay", "CAR");
        int bytesForFlags = args.getInt("osmreader.bytesForFlags", 4);
        encodingManager = new EncodingManager(flagEncoders, bytesForFlags);
        workerThreads = args.getInt("osmreader.workerThreads", workerThreads);
        enableInstructions = args.getBool("osmreader.instructions", enableInstructions);

        // index
        preciseIndexResolution = args.getInt("index.highResolution", preciseIndexResolution);
        return this;
    }

    private void printInfo()
    {
        logger.info("version " + Constants.VERSION + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")");
        logger.info("graph " + graph.toString() + ", details:" + graph.toDetailsString());
    }

    /**
     * Imports provided data from disc and creates graph. Depending on the settings the resulting
     * graph will be stored to disc so on a second call this method will only load the graph from
     * disc which is usually a lot faster.
     */
    public GraphHopper importOrLoad()
    {
        if (!load(ghLocation))
        {
            if (osmFile == null)
                throw new IllegalStateException("Couldn't load from existing folder: " + ghLocation
                        + " but also cannot import from OSM file as it wasn't specified!");
            printInfo();
            process(ghLocation);
        } else
        {
            printInfo();
        }
        return this;
    }

    /**
     * Creates the graph from OSM data.
     */
    private GraphHopper process( String graphHopperLocation )
    {
        setGraphHopperLocation(graphHopperLocation);
        try
        {
            importOSM();
            graph.getProperties().put("osmreader.import.date", formatDateTime(new Date()));
        } catch (IOException ex)
        {
            throw new RuntimeException("Cannot parse OSM file " + getOSMFile(), ex);
        }
        cleanUp();
        optimize();
        postProcessing();
        flush();
        return this;
    }

    protected OSMReader importOSM() throws IOException
    {
        if (graph == null)
            throw new IllegalStateException("Load graph before importing OSM data");

        if (osmFile == null)
            throw new IllegalArgumentException("No OSM file specified");

        File osmTmpFile = new File(osmFile);
        logger.info("start creating graph from " + osmFile);
        OSMReader reader = new OSMReader(graph, expectedCapacity).setWorkerThreads(workerThreads).setEncodingManager(encodingManager)
                .setWayPointMaxDistance(wayPointMaxDistance).setEnableInstructions(enableInstructions);
        logger.info("using " + graph.toString() + ", memory:" + Helper.getMemInfo());
        reader.doOSM2Graph(osmTmpFile);
        return reader;
    }

    /**
     * Opens existing graph.
     * <p/>
     * @param graphHopperFolder is the folder containing graphhopper files (which can be compressed
     * too)
     */
    @Override
    public boolean load( String graphHopperFolder )
    {
        if (Helper.isEmpty(graphHopperFolder))
            throw new IllegalStateException("graphHopperLocation is not specified. call init before");

        if (fullyLoaded)
            throw new IllegalStateException("graph is already successfully loaded");

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
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath()
                            + " to " + graphHopperFolder, ex);
                }
            }
        }
        setGraphHopperLocation(graphHopperFolder);

        GHDirectory dir = new GHDirectory(ghLocation, dataAccessType);

        if (chEnabled)
            graph = new LevelGraphStorage(dir, encodingManager);
        else if (turnCosts)
            graph = new GraphHopperStorage(dir, encodingManager, new TurnCostStorage());
        else
            graph = new GraphHopperStorage(dir, encodingManager);

        graph.setSegmentSize(defaultSegmentSize);
        if (!graph.loadExisting())
            return false;

        postProcessing();
        fullyLoaded = true;
        return true;
    }

    /**
     * Sets EncodingManager, does the preparation and creates the locationIndex
     */
    protected void postProcessing()
    {
        encodingManager = graph.getEncodingManager();
        if (chEnabled)
            initCHPrepare();

        if (!isPrepared())
            prepare();
        initLocationIndex();
    }

    private boolean isPrepared()
    {
        return "true".equals(graph.getProperties().get("prepare.done"));
    }

    protected void initCHPrepare()
    {
        FlagEncoder encoder = encodingManager.getSingle();
        PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies(encoder,
                createWeighting(chWeighting, encoder));
        tmpPrepareCH.setPeriodicUpdates(periodicUpdates).
                setLazyUpdates(lazyUpdates).
                setNeighborUpdates(neighborUpdates).
                setLogMessages(logMessages);

        prepare = tmpPrepareCH;
        prepare.setGraph(graph);
    }

    protected Weighting createWeighting( String weighting, FlagEncoder encoder )
    {
        // ignore case
        weighting = weighting.toLowerCase();
        if ("shortest".equals(weighting))
            return new ShortestWeighting();
        return new FastestWeighting(encoder);
    }

    @Override
    public GHResponse route( GHRequest request )
    {        
        if (graph == null || !fullyLoaded)
            throw new IllegalStateException("Call load or importOrLoad before routing");

        StopWatch sw = new StopWatch().start();
        GHResponse rsp = new GHResponse();

        if (!encodingManager.supports(request.getVehicle()))
        {
            rsp.addError(new IllegalArgumentException("Vehicle " + request.getVehicle() + " unsupported. Supported are: "
                    + getEncodingManager()));
            return rsp;
        }

        FlagEncoder encoder = encodingManager.getEncoder(request.getVehicle());
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        QueryResult fromRes = locationIndex.findClosest(request.getFrom().lat, request.getFrom().lon, edgeFilter);
        QueryResult toRes = locationIndex.findClosest(request.getTo().lat, request.getTo().lon, edgeFilter);

        String debug = "idLookup:" + sw.stop().getSeconds() + "s";

        if (!fromRes.isValid())
            rsp.addError(new IllegalArgumentException("Cannot find point 1: " + request.getFrom()));

        if (!toRes.isValid())
            rsp.addError(new IllegalArgumentException("Cannot find point 2: " + request.getTo()));

        sw = new StopWatch().start();
        RoutingAlgorithm algo = null;
        if (chEnabled)
        {
            if (prepare == null)
                throw new IllegalStateException(
                        "Preparation object is null. CH-preparation wasn't done or did you forgot to call disableCHShortcuts()?");

            if (request.getAlgorithm().equals("dijkstrabi"))
                algo = prepare.createAlgo();
            else if (request.getAlgorithm().equals("astarbi"))
                algo = ((PrepareContractionHierarchies) prepare).createAStar();
            else
                rsp.addError(new IllegalStateException(
                        "Only dijkstrabi and astarbi is supported for LevelGraph (using contraction hierarchies)!"));

        } else
        {
            Weighting weighting = createWeighting(request.getWeighting(), encoder);
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.getAlgorithm(), encoder, weighting);
            algo = prepare.createAlgo();
        }

        if (rsp.hasErrors())
            return rsp;

        debug += ", algoInit:" + sw.stop().getSeconds() + "s";
        sw = new StopWatch().start();

        Path path = algo.calcPath(fromRes, toRes);
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s, " + path.getDebugInfo();

        enableInstructions = request.getHint("instructions", enableInstructions);
        calcPoints = request.getHint("calcPoints", calcPoints);
        simplifyRequest = request.getHint("simplifyRequest", simplifyRequest);
        double minPathPrecision = request.getHint("douglas.minprecision", 1d);
        DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(minPathPrecision);

        if (enableInstructions)
        {
            InstructionList il = path.calcInstructions();
            rsp.setInstructions(il);
            rsp.setFound(il.getSize() > 0);

            sw = new StopWatch().start();
            int orig = 0;
            PointList points = new PointList(il.size() * 5);
            for (Instruction i : il)
            {
                if (simplifyRequest && minPathPrecision > 0)
                {
                    orig += i.getPoints().size();
                    peucker.simplify(i.getPoints());
                }
                points.add(i.getPoints());
            }

            if (simplifyRequest)
                debug += ", simplify (" + orig + "->" + points.getSize() + "):" + sw.stop().getSeconds() + "s";

            rsp.setPoints(points);

        } else if (calcPoints)
        {
            PointList points = path.calcPoints();
            rsp.setFound(points.getSize() > 1);

            if (simplifyRequest)
            {
                sw = new StopWatch().start();
                int orig = points.getSize();

                if (minPathPrecision > 0)
                    peucker.simplify(points);

                debug += ", simplify (" + orig + "->" + points.getSize() + "):" + sw.stop().getSeconds() + "s";
            }
            rsp.setPoints(points);
        } else
            rsp.setFound(path.isFound());

        return rsp.setDistance(path.getDistance()).setMillis(path.getMillis()).setDebugInfo(debug);
    }

    protected LocationIndex createLocationIndex( Directory dir )
    {
        LocationIndex tmpIndex;
        if (preciseIndexResolution > 0)
        {
            LocationIndexTree tmpNIndex;
            if (graph instanceof LevelGraph)
            {
                tmpNIndex = new LocationIndexTreeSC((LevelGraph) graph, dir);
            } else
            {
                tmpNIndex = new LocationIndexTree(graph, dir);
            }
            tmpNIndex.setResolution(preciseIndexResolution);
            tmpNIndex.setSearchRegion(searchRegion);
            tmpIndex = tmpNIndex;
        } else
        {
            tmpIndex = new Location2IDQuadtree(graph, dir);
            tmpIndex.setResolution(Helper.calcIndexSize(graph.getBounds()));
        }

        if (!tmpIndex.loadExisting())
            tmpIndex.prepareIndex();

        return tmpIndex;
    }

    /**
     * Initializes the location index. Currently this has to be done after the ch-preparation!
     * Because - to improve performance - certain edges won't be available in a ch-graph and the
     * index needs to know this and selects the correct nodes which still see the correct neighbors.
     */
    protected void initLocationIndex()
    {
        if (locationIndex != null)
            throw new IllegalStateException("Cannot initialize locationIndex twice!");

        locationIndex = createLocationIndex(graph.getDirectory());
    }

    protected void optimize()
    {
        logger.info("optimizing ... (" + Helper.getMemInfo() + ")");
        graph.optimize();
        logger.info("finished optimize (" + Helper.getMemInfo() + ")");

        // Later: move this into the GraphStorage.optimize method
        // Or: Doing it after preparation to optimize shortcuts too. But not possible yet https://github.com/graphhopper/graphhopper/issues/12
        if (sortGraph)
        {
            if (graph instanceof LevelGraph && isPrepared())
                throw new IllegalArgumentException("Sorting prepared LevelGraph is not possible yet. See #12");

            logger.info("sorting ... (" + Helper.getMemInfo() + ")");
            GraphStorage newGraph = GHUtility.newStorage(graph);
            GHUtility.sortDFS(graph, newGraph);
            graph = newGraph;
        }
    }

    protected void prepare()
    {
        boolean tmpPrepare = doPrepare && prepare != null;
        if (tmpPrepare)
        {
            if (prepare instanceof PrepareContractionHierarchies && encodingManager.getVehicleCount() > 1)
                throw new IllegalArgumentException("Contraction hierarchies preparation "
                        + "requires (at the moment) only one vehicle. But was:" + encodingManager);

            logger.info("calling prepare.doWork ... (" + Helper.getMemInfo() + ")");
            prepare.doWork();
            graph.getProperties().put("prepare.date", formatDateTime(new Date()));
        }
        graph.getProperties().put("prepare.done", tmpPrepare);
    }

    protected void cleanUp()
    {
        int prev = graph.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graph, encodingManager);
        preparation.setMinNetworkSize(minNetworkSize);
        logger.info("start finding subnetworks, " + Helper.getMemInfo());
        preparation.doWork();
        int n = graph.getNodes();
        // calculate remaining subnetworks
        int remainingSubnetworks = preparation.findSubnetworks().size();
        logger.info("edges: " + graph.getAllEdges().getMaxId() + ", nodes " + n + ", there were " + preparation.getSubNetworks()
                + " subnetworks. removed them => " + (prev - n) + " less nodes. Remaining subnetworks:" + remainingSubnetworks);
    }

    protected void flush()
    {
        logger.info("flushing graph " + graph.toString() + ", details:" + graph.toDetailsString() + ", " + Helper.getMemInfo() + ")");
        graph.flush();
        fullyLoaded = true;
    }

    /**
     * Releases all associated resources like memory or files.
     */
    public void close()
    {
        if (graph != null)
            graph.close();

        if (locationIndex != null)
            locationIndex.close();
    }

    protected void ensureNotLoaded()
    {
        if (fullyLoaded)
            throw new IllegalStateException("No configuration changes are possible after loading the graph");
    }

    // make sure this is identical to buildDate used in pom.xml
    // <maven.build.timestamp.format>yyyy-MM-dd'T'HH:mm:ssZ</maven.build.timestamp.format>
    private String formatDateTime( Date date )
    {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);
    }
}
