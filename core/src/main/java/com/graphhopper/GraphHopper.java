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
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.*;
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
    private Location2IDIndex locationIndex;
    private int preciseIndexResolution = 500;
    private boolean searchRegion = true;
    // for prepare
    private int minNetworkSize = 200;
    // for CH prepare
    private AlgorithmPreparation prepare;
    private boolean doPrepare = true;
    private boolean chEnabled = true;
    private String chType = "fastest";
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
        ensureNotLoaded();
        preciseIndexResolution = precision;
        return this;
    }

    public GraphHopper setInMemory( boolean inMemory, boolean storeOnFlush )
    {
        ensureNotLoaded();
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
        ensureNotLoaded();
        dataAccessType = DAType.MMAP;
        return this;
    }

    // not yet stable enough to offer it for everyone
    private GraphHopper setUnsafeMemory()
    {
        ensureNotLoaded();
        dataAccessType = DAType.UNSAFE_STORE;
        return this;
    }

    public GraphHopper setDoPrepare( boolean doPrepare )
    {
        this.doPrepare = doPrepare;
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times.
     * <p/>
     * @param type can be "fastest", "shortest" or your own weight-calculation type.
     * @see #disableCHShortcuts()
     */
    public GraphHopper setCHShortcuts( String type )
    {
        ensureNotLoaded();
        chEnabled = true;
        chType = type;
        return this;
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

    public Graph getGraph()
    {
        if (graph == null)
            throw new IllegalStateException("Graph not initialized");

        return graph;
    }

    public void setGraph( GraphStorage graph )
    {
        this.graph = graph;
    }

    public Location2IDIndex getLocationIndex()
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
     * @deprecated until #12 is fixed
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

        // optimizable prepare
        minNetworkSize = args.getInt("prepare.minNetworkSize", minNetworkSize);

        // prepare CH
        doPrepare = args.getBool("prepare.doPrepare", doPrepare);
        String chShortcuts = args.get("prepare.chShortcuts", "fastest");
        chEnabled = "true".equals(chShortcuts)
                || "fastest".equals(chShortcuts) || "shortest".equals(chShortcuts);
        if (chEnabled)
            setCHShortcuts(chShortcuts);

        if (args.has("prepare.updates.periodic"))
            periodicUpdates = args.getInt("prepare.updates.periodic", periodicUpdates);

        if (args.has("prepare.updates.lazy"))
            lazyUpdates = args.getInt("prepare.updates.lazy", lazyUpdates);

        if (args.has("prepare.updates.neighbor"))
            neighborUpdates = args.getInt("prepare.updates.neighbor", neighborUpdates);

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
            process(ghLocation, osmFile);
        } else
        {
            printInfo();
        }
        return this;
    }

    /**
     * Creates the graph from OSM data.
     */
    private GraphHopper process( String graphHopperLocation, String osmFileStr )
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
        cleanUp();
        optimize();
        postProcessing();
        flush();
        return this;
    }

    protected OSMReader importOSM( String _osmFile ) throws IOException
    {
        if (graph == null)
            throw new IllegalStateException("Load graph before importing OSM data");

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
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath() + " to " + graphHopperFolder, ex);
                }
            }
        }
        setGraphHopperLocation(graphHopperFolder);

        GHDirectory dir = new GHDirectory(ghLocation, dataAccessType);

        if (chEnabled)
            graph = new LevelGraphStorage(dir, encodingManager);
        else
            graph = new GraphStorage(dir, encodingManager);

        graph.setSegmentSize(defaultSegmentSize);
        if (!graph.loadExisting())
            return false;

        postProcessing();
        fullyLoaded = true;
        return true;
    }

    protected void postProcessing()
    {
        encodingManager = graph.getEncodingManager();
        if (chEnabled)
        {
            FlagEncoder encoder = encodingManager.getSingle();
            PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies(encoder,
                    createType(chType, encoder));
            tmpPrepareCH.setPeriodicUpdates(periodicUpdates).
                    setLazyUpdates(lazyUpdates).
                    setNeighborUpdates(neighborUpdates);

            prepare = tmpPrepareCH;
            prepare.setGraph(graph);
        }

        if (!"true".equals(graph.getProperties().get("prepare.done")))
            prepare();
        initLocationIndex();
    }

    protected WeightCalculation createType( String type, FlagEncoder encoder )
    {
        // ignore case
        type = type.toLowerCase();
        if ("shortest".equals(type))
            return new ShortestCalc();
        return new FastestCalc(encoder);
    }

    @Override
    public GHResponse route( GHRequest request )
    {
        request.check();
        if (graph == null || !fullyLoaded)
            throw new IllegalStateException("Call load or importOrLoad before routing");

        StopWatch sw = new StopWatch().start();
        GHResponse rsp = new GHResponse();

        if (!encodingManager.supports(request.getVehicle()))
        {
            rsp.addError(new IllegalArgumentException("Vehicle " + request.getVehicle() + " unsupported. Supported are: " + getEncodingManager()));
            return rsp;
        }

        FlagEncoder encoder = encodingManager.getEncoder(request.getVehicle());
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);        
        LocationIDResult fromRes = locationIndex.findClosest(request.getFrom().lat, request.getFrom().lon, edgeFilter);
        LocationIDResult toRes = locationIndex.findClosest(request.getTo().lat, request.getTo().lon, edgeFilter);       
        
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
                throw new IllegalStateException("Preparation object is null. CH-preparation wasn't done or did you forgot to call setCHShortcuts(false, chType)?");

            if (request.getAlgorithm().equals("dijkstrabi"))
                algo = prepare.createAlgo();
            else if (request.getAlgorithm().equals("astarbi"))
                algo = ((PrepareContractionHierarchies) prepare).createAStar();
            else
                rsp.addError(new IllegalStateException("Only dijkstrabi and astarbi is supported for LevelGraph (using contraction hierarchies)!"));

        } else
        {
            WeightCalculation weightCalc = createType(request.getType(), encoder);
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, request.getAlgorithm(),
                    encoder, weightCalc);
            algo = prepare.createAlgo();
        }

        if (rsp.hasErrors())
            return rsp;

        debug += ", algoInit:" + sw.stop().getSeconds() + "s";
        sw = new StopWatch().start();

        Path path = algo.calcPath(fromRes, toRes);
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s, " + path.getDebugInfo();

        calcPoints = request.getHint("calcPoints", calcPoints);
        if (calcPoints)
        {
            PointList points = path.calcPoints();
            rsp.setFound(points.getSize() > 1);
            simplifyRequest = request.getHint("simplifyRequest", simplifyRequest);
            if (simplifyRequest)
            {
                sw = new StopWatch().start();
                int orig = points.getSize();
                double minPathPrecision = request.getHint("douglas.minprecision", 1d);
                if (minPathPrecision > 0)
                    new DouglasPeucker().setMaxDistance(minPathPrecision).simplify(points);

                debug += ", simplify (" + orig + "->" + points.getSize() + "):" + sw.stop().getSeconds() + "s";
            }
            rsp.setPoints(points);

            enableInstructions = request.getHint("instructions", enableInstructions);
            if (enableInstructions)
            {
                sw = new StopWatch().start();
                rsp.setInstructions(path.calcInstructions());
                debug += ", instructions:" + sw.stop().getSeconds() + "s";
            }
        } else
            rsp.setFound(path.isFound());

        return rsp.setDistance(path.getDistance()).setTime(path.getTime()).setDebugInfo(debug);
    }

    protected Location2IDIndex createLocationIndex( Directory dir )
    {
        Location2IDIndex tmpIndex;
        if (preciseIndexResolution > 0)
        {
            Location2NodesNtree tmpNIndex;
            if (graph instanceof LevelGraph)
            {
                tmpNIndex = new Location2NodesNtreeLG((LevelGraph) graph, dir);
            } else
            {
                tmpNIndex = new Location2NodesNtree(graph, dir);
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

        // move this into the GraphStorage.optimize method?
        if (sortGraph)
        {
            logger.info("sorting ... (" + Helper.getMemInfo() + ")");
            GraphStorage newGraph = GHUtility.newStorage(graph);
            GHUtility.sortDFS(graph, newGraph);
            graph = newGraph;
        }
    }

    protected void prepare()
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
        preparation.setMinNetworkSize(minNetworkSize);
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
        fullyLoaded = true;
    }

    void close()
    {
        if (graph != null)
            graph.close();

        if (locationIndex != null)
            locationIndex.close();
    }

    private void ensureNotLoaded()
    {
        if (fullyLoaded)
            throw new IllegalStateException("No configuration changes are possible after loading the graph");
    }
}
