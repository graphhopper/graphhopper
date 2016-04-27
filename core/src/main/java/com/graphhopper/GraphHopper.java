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

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.OSMReader;
import com.graphhopper.reader.dem.CGIARProvider;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;

/**
 * Easy to use access point to configure import and (offline) routing.
 *
 * @author Peter Karich
 * @see GraphHopperAPI
 */
public class GraphHopper implements GraphHopperAPI
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // for graph:
    private GraphHopperStorage ghStorage;
    private EncodingManager encodingManager;
    private int defaultSegmentSize = -1;
    private String ghLocation = "";
    private DAType dataAccessType = DAType.RAM_STORE;
    private boolean sortGraph = false;
    boolean removeZipped = true;
    private boolean elevation = false;
    private LockFactory lockFactory = new NativeFSLockFactory();
    private final String fileLockName = "gh.lock";
    private boolean allowWrites = true;
    boolean enableInstructions = true;
    private String preferredLanguage = "";
    private boolean fullyLoaded = false;
    // for routing
    private boolean simplifyResponse = true;
    private TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private final Set<RoutingAlgorithmFactoryDecorator> algoDecorators = new LinkedHashSet<>();
    private int maxVisitedNodes = Integer.MAX_VALUE;
    // for index
    private LocationIndex locationIndex;
    private int preciseIndexResolution = 300;
    private int maxRegionSearch = 4;
    // for prepare
    private int minNetworkSize = 200;
    private int minOneWayNetworkSize = 0;
    // for CH prepare    
    private final CHAlgoFactoryDecorator chFactoryDecorator = new CHAlgoFactoryDecorator();
    // for OSM import
    private String osmFile;
    private double osmReaderWayPointMaxDistance = 1;
    private int workerThreads = -1;
    private boolean calcPoints = true;
    // utils
    private final TranslationMap trMap = new TranslationMap().doImport();
    private ElevationProvider eleProvider = ElevationProvider.NOOP;

    public GraphHopper()
    {
        chFactoryDecorator.setEnabled(true);
        algoDecorators.add(chFactoryDecorator);
    }

    /**
     * For testing only
     */
    protected GraphHopper loadGraph( GraphHopperStorage g )
    {
        this.ghStorage = g;
        fullyLoaded = true;
        initLocationIndex();
        return this;
    }

    /**
     * Specify which vehicles can be read by this GraphHopper instance. An encoding manager defines
     * how data from every vehicle is written (und read) into edges of the graph.
     */
    public GraphHopper setEncodingManager( EncodingManager em )
    {
        ensureNotLoaded();
        this.encodingManager = em;
        if (em.needsTurnCostsSupport())
            traversalMode = TraversalMode.EDGE_BASED_2DIR;

        return this;
    }

    /**
     * @return the first flag encoder of the encoding manager
     */
    FlagEncoder getDefaultVehicle()
    {
        if (encodingManager == null)
            throw new IllegalStateException("No encoding manager specified or loaded");

        return encodingManager.fetchEdgeEncoders().get(0);
    }

    public EncodingManager getEncodingManager()
    {
        return encodingManager;
    }

    public GraphHopper setElevationProvider( ElevationProvider eleProvider )
    {
        if (eleProvider == null || eleProvider == ElevationProvider.NOOP)
            setElevation(false);
        else
            setElevation(true);
        this.eleProvider = eleProvider;
        return this;
    }

    /**
     * Threads for data reading.
     */
    protected int getWorkerThreads()
    {
        return workerThreads;
    }

    /**
     * Return maximum distance (in meter) to reduce points via douglas peucker while OSM import.
     */
    protected double getWayPointMaxDistance()
    {
        return osmReaderWayPointMaxDistance;
    }

    /**
     * This parameter specifies how to reduce points via douglas peucker while OSM import. Higher
     * value means more details, unit is meter. Default is 1. Disable via 0.
     */
    public GraphHopper setWayPointMaxDistance( double wayPointMaxDistance )
    {
        this.osmReaderWayPointMaxDistance = wayPointMaxDistance;
        return this;
    }

    /**
     * Sets the default traversal mode used for the algorithms and preparation.
     */
    public GraphHopper setTraversalMode( TraversalMode traversalMode )
    {
        this.traversalMode = traversalMode;
        return this;
    }

    public TraversalMode getTraversalMode()
    {
        return traversalMode;
    }

    /**
     * Configures the underlying storage and response to be used on a well equipped server. Result
     * also optimized for usage in the web module i.e. try reduce network IO.
     */
    public GraphHopper forServer()
    {
        setSimplifyResponse(true);
        return setInMemory();
    }

    /**
     * Configures the underlying storage to be used on a Desktop computer or within another Java
     * application with enough RAM but no network latency.
     */
    public GraphHopper forDesktop()
    {
        setSimplifyResponse(false);
        return setInMemory();
    }

    /**
     * Configures the underlying storage to be used on a less powerful machine like Android or
     * Raspberry Pi with only few MB of RAM.
     */
    public GraphHopper forMobile()
    {
        setSimplifyResponse(false);
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

    public GraphHopper setMinNetworkSize( int minNetworkSize, int minOneWayNetworkSize )
    {
        this.minNetworkSize = minNetworkSize;
        this.minOneWayNetworkSize = minOneWayNetworkSize;
        return this;
    }

    /**
     * This method call results in an in-memory graph.
     */
    public GraphHopper setInMemory()
    {
        ensureNotLoaded();
        dataAccessType = DAType.RAM_STORE;
        return this;
    }

    /**
     * Only valid option for in-memory graph and if you e.g. want to disable store on flush for unit
     * tests. Specify storeOnFlush to true if you want that existing data will be loaded FROM disc
     * and all in-memory data will be flushed TO disc after flush is called e.g. while OSM import.
     *
     * @param storeOnFlush true by default
     */
    public GraphHopper setStoreOnFlush( boolean storeOnFlush )
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
     * Wrapper method for {@link GraphHopper#setCHWeightings(List)}
     *
     * @deprecated Use getCHFactoryDecorator().setWeightingsAsStrings() instead. Will be removed in
     * 0.8.
     */
    public GraphHopper setCHWeightings( String... weightingNames )
    {
        return this.setCHWeightings(Arrays.asList(weightingNames));
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times. Enabled by default.
     * <p>
     *
     * @param weightingList A list containing multiple weightings like: "fastest", "shortest" or
     * your own weight-calculation type.
     *
     * @deprecated Use getCHFactoryDecorator().setWeightingsAsStrings() instead. Will be removed in
     * 0.8.
     */
    public GraphHopper setCHWeightings( List<String> weightingList )
    {
        ensureNotLoaded();
        chFactoryDecorator.setWeightingsAsStrings(weightingList);
        return this;
    }

    /**
     * Returns all CHWeighting names
     *
     * @deprecated Use getCHFactoryDecorator().getWeightingsAsStrings() instead. Will be removed in
     * 0.8.
     */
    public List<String> getCHWeightings()
    {
        return chFactoryDecorator.getWeightingsAsStrings();
    }

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory to increase this number!
     *
     * @deprecated Use getCHFactoryDecorator().setCHPrepareThreads() instead. Will be removed in
     * 0.8.
     */
    public GraphHopper setCHPrepareThreads( int prepareThreads )
    {
        chFactoryDecorator.setPreparationThreads(prepareThreads);
        return this;
    }

    /**
     * @deprecated Use getCHFactoryDecorator().getCHPrepareThreads() instead. Will be removed in
     * 0.8.
     */
    public int getCHPrepareThreads()
    {
        return chFactoryDecorator.getPreparationThreads();
    }

    /**
     *
     * @deprecated Use setEnabled() instead. Will be removed in 0.8.
     */
    public GraphHopper setCHEnable( boolean enable )
    {
        return setCHEnabled(enable);
    }

    /**
     * Enables or disables contraction hierarchies (CH). This speed-up mode is enabled by default.
     */
    public GraphHopper setCHEnabled( boolean enable )
    {
        ensureNotLoaded();
        chFactoryDecorator.setEnabled(enable);
        return this;
    }

    public final boolean isCHEnabled()
    {
        return chFactoryDecorator.isEnabled();
    }

    /**
     * This methods stops the algorithm from searching further if the resulting path would go over
     * the specified node count, important if CH is disabled.
     */
    public void setMaxVisitedNodes( int maxVisitedNodes )
    {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    /**
     * @return true if storing and fetching elevation data is enabled. Default is false
     */
    public boolean hasElevation()
    {
        return elevation;
    }

    /**
     * Enable storing and fetching elevation data. Default is false
     */
    public GraphHopper setElevation( boolean includeElevation )
    {
        this.elevation = includeElevation;
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
     * This method specifies the preferred language for way names during import.
     * <p>
     * Language code as defined in ISO 639-1 or ISO 639-2.
     * <ul>
     * <li>If no preferred language is specified, only the default language with no tag will be
     * imported.</li>
     * <li>If a language is specified, it will be imported if its tag is found, otherwise fall back
     * to default language.</li>
     * </ul>
     */
    public GraphHopper setPreferredLanguage( String preferredLanguage )
    {
        ensureNotLoaded();
        if (preferredLanguage == null)
            throw new IllegalArgumentException("preferred language cannot be null");

        this.preferredLanguage = preferredLanguage;
        return this;
    }

    public String getPreferredLanguage()
    {
        return preferredLanguage;
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
    private GraphHopper setSimplifyResponse( boolean doSimplify )
    {
        this.simplifyResponse = doSimplify;
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
     *
     * @throws IllegalStateException if graph is not instantiated.
     */
    public GraphHopperStorage getGraphHopperStorage()
    {
        if (ghStorage == null)
            throw new IllegalStateException("GraphHopper storage not initialized");

        return ghStorage;
    }

    public void setGraphHopperStorage( GraphHopperStorage ghStorage )
    {
        this.ghStorage = ghStorage;
        fullyLoaded = true;
    }

    protected void setLocationIndex( LocationIndex locationIndex )
    {
        this.locationIndex = locationIndex;
    }

    /**
     * The location index created from the graph.
     *
     * @throws IllegalStateException if index is not initialized
     */
    public LocationIndex getLocationIndex()
    {
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return locationIndex;
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

    /**
     * Specifies if it is allowed for GraphHopper to write. E.g. for read only filesystems it is not
     * possible to create a lock file and so we can avoid write locks.
     */
    public GraphHopper setAllowWrites( boolean allowWrites )
    {
        this.allowWrites = allowWrites;
        return this;
    }

    public boolean isAllowWrites()
    {
        return allowWrites;
    }

    public TranslationMap getTranslationMap()
    {
        return trMap;
    }

    /**
     * Reads configuration from a CmdArgs object. Which can be manually filled, or via main(String[]
     * args) ala CmdArgs.read(args) or via configuration file ala
     * CmdArgs.readFromConfig("config.properties", "graphhopper.config")
     */
    public GraphHopper init( CmdArgs args )
    {
        args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
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
        defaultSegmentSize = args.getInt("graph.dataaccess.segmentSize", defaultSegmentSize);

        String graphDATypeStr = args.get("graph.dataaccess", "RAM_STORE");
        dataAccessType = DAType.fromString(graphDATypeStr);

        sortGraph = args.getBool("graph.doSort", sortGraph);
        removeZipped = args.getBool("graph.removeZipped", removeZipped);
        int bytesForFlags = args.getInt("graph.bytesForFlags", 4);
        String flagEncoders = args.get("graph.flagEncoders", "");
        if (!flagEncoders.isEmpty())
            setEncodingManager(new EncodingManager(flagEncoders, bytesForFlags));

        if (args.get("graph.locktype", "native").equals("simple"))
            lockFactory = new SimpleFSLockFactory();
        else
            lockFactory = new NativeFSLockFactory();

        // elevation
        String eleProviderStr = args.get("graph.elevation.provider", "noop").toLowerCase();
        boolean eleCalcMean = args.getBool("graph.elevation.calcmean", false);
        String cacheDirStr = args.get("graph.elevation.cachedir", "");
        String baseURL = args.get("graph.elevation.baseurl", "");
        DAType elevationDAType = DAType.fromString(args.get("graph.elevation.dataaccess", "MMAP"));
        ElevationProvider tmpProvider = ElevationProvider.NOOP;
        if (eleProviderStr.equalsIgnoreCase("srtm"))
        {
            tmpProvider = new SRTMProvider();
        } else if (eleProviderStr.equalsIgnoreCase("cgiar"))
        {
            CGIARProvider cgiarProvider = new CGIARProvider();
            cgiarProvider.setAutoRemoveTemporaryFiles(args.getBool("graph.elevation.cgiar.clear", true));
            tmpProvider = cgiarProvider;
        }

        tmpProvider.setCalcMean(eleCalcMean);
        tmpProvider.setCacheDir(new File(cacheDirStr));
        if (!baseURL.isEmpty())
            tmpProvider.setBaseURL(baseURL);
        tmpProvider.setDAType(elevationDAType);
        setElevationProvider(tmpProvider);

        // optimizable prepare
        minNetworkSize = args.getInt("prepare.minNetworkSize", minNetworkSize);
        minOneWayNetworkSize = args.getInt("prepare.minOneWayNetworkSize", minOneWayNetworkSize);

        // prepare CH
        chFactoryDecorator.init(args);

        // osm import
        osmReaderWayPointMaxDistance = args.getDouble("osmreader.wayPointMaxDistance", osmReaderWayPointMaxDistance);

        workerThreads = args.getInt("osmreader.workerThreads", workerThreads);
        enableInstructions = args.getBool("osmreader.instructions", enableInstructions);
        preferredLanguage = args.get("osmreader.preferred-language", preferredLanguage);

        // index
        preciseIndexResolution = args.getInt("index.highResolution", preciseIndexResolution);
        maxRegionSearch = args.getInt("index.maxRegionSearch", maxRegionSearch);

        // routing
        maxVisitedNodes = args.getInt("routing.maxVisitedNodes", Integer.MAX_VALUE);

        return this;
    }

    private void printInfo()
    {
        logger.info("version " + Constants.VERSION + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")");
        if (ghStorage != null)
            logger.info("graph " + ghStorage.toString() + ", details:" + ghStorage.toDetailsString());
    }

    /**
     * Imports provided data from disc and creates graph. Depending on the settings the resulting
     * graph will be stored to disc so on a second call this method will only load the graph from
     * disc which is usually a lot faster.
     */
    public GraphHopper importOrLoad()
    {
        if (!initializeStorage(ghLocation))
        {
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
        Lock lock = null;
        try
        {
            if (ghStorage.getDirectory().getDefaultType().isStoring())
            {
                lockFactory.setLockDir(new File(graphHopperLocation));
                lock = lockFactory.create(fileLockName, true);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid multiple writers we need to obtain a write lock but it failed. In " + graphHopperLocation, lock.getObtainFailedReason());
            }

            try
            {
                DataReader reader = importData();
                DateFormat f = Helper.createFormatter();
                ghStorage.getProperties().put("osmreader.import.date", f.format(new Date()));
                if (reader.getDataDate() != null)
                    ghStorage.getProperties().put("osmreader.data.date", f.format(reader.getDataDate()));
            } catch (IOException ex)
            {
                throw new RuntimeException("Cannot parse OSM file " + getOSMFile(), ex);
            }
            cleanUp();
            postProcessing();
            flush();
        } finally
        {
            if (lock != null)
                lock.release();
        }
        return this;
    }

    protected DataReader importData() throws IOException
    {
        ensureWriteAccess();
        if (ghStorage == null)
            throw new IllegalStateException("Load graph before importing OSM data");

        if (osmFile == null)
            throw new IllegalStateException("Couldn't load from existing folder: " + ghLocation
                    + " but also cannot import from OSM file as it wasn't specified!");

        encodingManager.setEnableInstructions(enableInstructions);
        encodingManager.setPreferredLanguage(preferredLanguage);
        DataReader reader = createReader(ghStorage);
        logger.info("using " + ghStorage.toString() + ", memory:" + Helper.getMemInfo());
        reader.readGraph();
        return reader;
    }

    protected DataReader createReader( GraphHopperStorage ghStorage )
    {
        return initOSMReader(new OSMReader(ghStorage));
    }

    protected OSMReader initOSMReader( OSMReader reader )
    {
        if (osmFile == null)
            throw new IllegalArgumentException("No OSM file specified");

        logger.info("start creating graph from " + osmFile);
        File osmTmpFile = new File(osmFile);
        return reader.setOSMFile(osmTmpFile).
                setElevationProvider(eleProvider).
                setWorkerThreads(workerThreads).
                setEncodingManager(encodingManager).
                setWayPointMaxDistance(osmReaderWayPointMaxDistance);
    }

    /**
     * Opens existing graph.
     *
     * @param graphHopperFolder is the folder containing graphhopper files (which can be compressed
     * too)
     */
    @Override
    public boolean load( String graphHopperFolder )
    {
        if (!(new File(graphHopperFolder).exists()))
            throw new IllegalStateException("Path \"" + graphHopperFolder + "\" does not exist");

        return initializeStorage(graphHopperFolder);
    }

    private boolean initializeStorage( String graphHopperFolder )
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
        } else if (!graphHopperFolder.contains("."))
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

        if (encodingManager == null)
            setEncodingManager(EncodingManager.create(ghLocation));

        if (!allowWrites && dataAccessType.isMMap())
            dataAccessType = DAType.MMAP_RO;

        GHDirectory dir = new GHDirectory(ghLocation, dataAccessType);
        GraphExtension ext = encodingManager.needsTurnCostsSupport()
                ? new TurnCostExtension() : new GraphExtension.NoOpExtension();

        if (chFactoryDecorator.isEnabled())
        {
            initCHAlgoFactoryDecorator();
            ghStorage = new GraphHopperStorage(chFactoryDecorator.getWeightings(), dir, encodingManager, hasElevation(), ext);
        } else
        {
            ghStorage = new GraphHopperStorage(dir, encodingManager, hasElevation(), ext);
        }

        ghStorage.setSegmentSize(defaultSegmentSize);

        Lock lock = null;
        try
        {
            // create locks only if writes are allowed, if they are not allowed a lock cannot be created 
            // (e.g. on a read only filesystem locks would fail)
            if (ghStorage.getDirectory().getDefaultType().isStoring() && isAllowWrites())
            {
                lockFactory.setLockDir(new File(ghLocation));
                lock = lockFactory.create(fileLockName, false);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid reading partial data we need to obtain the read lock but it failed. In " + ghLocation, lock.getObtainFailedReason());
            }

            if (!ghStorage.loadExisting())
                return false;

            postProcessing();
            fullyLoaded = true;
            return true;
        } finally
        {
            if (lock != null)
                lock.release();
        }
    }

    public RoutingAlgorithmFactory getAlgorithmFactory( HintsMap map )
    {
        RoutingAlgorithmFactory routingAlgorithmFactory = new RoutingAlgorithmFactorySimple();
        for (RoutingAlgorithmFactoryDecorator decorator : algoDecorators)
        {
            if (decorator.isEnabled())
                routingAlgorithmFactory = decorator.getDecoratedAlgorithmFactory(routingAlgorithmFactory, map);
        }

        return routingAlgorithmFactory;
    }

    public GraphHopper addAlgorithmFactoryDecorator( RoutingAlgorithmFactoryDecorator algoFactoryDecorator )
    {
        if (!algoDecorators.add(algoFactoryDecorator))
            throw new IllegalArgumentException("Decorator was already added " + algoFactoryDecorator.getClass());

        return this;
    }

    public final CHAlgoFactoryDecorator getCHFactoryDecorator()
    {
        return chFactoryDecorator;
    }

    private void initCHAlgoFactoryDecorator()
    {
        if (!chFactoryDecorator.hasWeightings())
            for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders())
            {
                for (String chWeightingStr : chFactoryDecorator.getWeightingsAsStrings())
                {
                    Weighting weighting = createWeighting(new HintsMap(chWeightingStr), encoder);
                    chFactoryDecorator.addWeighting(weighting);
                }
            }
    }

    /**
     * This method creates prepations.
     *
     * @deprecated use getCHFactoryDecorator().createPreparations() instead. Will be removed in 0.8.
     */
    protected void createCHPreparations()
    {
        chFactoryDecorator.createPreparations(ghStorage, traversalMode);
    }

    /**
     * Sets EncodingManager, does the preparation and creates the locationIndex
     */
    protected void postProcessing()
    {
        // Later: move this into the GraphStorage.optimize method
        // Or: Doing it after preparation to optimize shortcuts too. But not possible yet #12
        if (sortGraph)
        {
            if (ghStorage.isCHPossible() && isPrepared())
                throw new IllegalArgumentException("Sorting a prepared CHGraph is not possible yet. See #12");

            GraphHopperStorage newGraph = GHUtility.newStorage(ghStorage);
            GHUtility.sortDFS(ghStorage, newGraph);
            logger.info("graph sorted (" + Helper.getMemInfo() + ")");
            ghStorage = newGraph;
        }

        initLocationIndex();
        if (chFactoryDecorator.isEnabled())
            createCHPreparations();

        if (!isPrepared())
            prepare();
    }

    private boolean isPrepared()
    {
        return "true".equals(ghStorage.getProperties().get("prepare.done"));
    }

    /**
     * Based on the weightingParameters and the specified vehicle a Weighting instance can be
     * created. Note that all URL parameters are available in the weightingParameters as String if
     * you use the GraphHopper Web module.
     *
     * @param weightingMap all parameters influencing the weighting. E.g. parameters coming via
     * GHRequest.getHints or directly via "&amp;api.xy=" from the URL of the web UI
     * @param encoder the required vehicle
     * @return the weighting to be used for route calculation
     * @see HintsMap
     */
    public Weighting createWeighting( HintsMap weightingMap, FlagEncoder encoder )
    {
        String weighting = weightingMap.getWeighting().toLowerCase();

        if ("shortest".equalsIgnoreCase(weighting))
        {
            return new ShortestWeighting(encoder);
        } else if ("fastest".equalsIgnoreCase(weighting) || weighting.isEmpty())
        {
            if (encoder.supports(PriorityWeighting.class))
                return new PriorityWeighting(encoder, weightingMap);
            else
                return new FastestWeighting(encoder, weightingMap);
        } else if ("curvature".equalsIgnoreCase(weighting))
        {
            if (encoder.supports(CurvatureWeighting.class))
                return new CurvatureWeighting(encoder, weightingMap);
            else
                return new FastestWeighting(encoder, weightingMap);
        }

        throw new UnsupportedOperationException("weighting " + weighting + " not supported");
    }

    /**
     * Potentially wraps the specified weighting into a TurnWeighting instance.
     */
    public Weighting createTurnWeighting( Weighting weighting, Graph graph, FlagEncoder encoder )
    {
        if (encoder.supports(TurnWeighting.class))
            return new TurnWeighting(weighting, encoder, (TurnCostExtension) graph.getExtension());
        return weighting;
    }

    @Override
    public GHResponse route( GHRequest request )
    {
        GHResponse response = new GHResponse();
        calcPaths(request, response);
        return response;
    }

    protected List<Path> calcPaths( GHRequest request, GHResponse ghRsp )
    {
        if (ghStorage == null || !fullyLoaded)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");

        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");

        // default handling
        String vehicle = request.getVehicle();
        if (vehicle.isEmpty())
        {
            vehicle = getDefaultVehicle().toString();
            request.setVehicle(vehicle);
        }

        if (!encodingManager.supports(vehicle))
        {
            ghRsp.addError(new IllegalArgumentException("Vehicle " + vehicle + " unsupported. "
                    + "Supported are: " + getEncodingManager()));
            return Collections.emptyList();
        }

        TraversalMode tMode;
        String tModeStr = request.getHints().get("traversal_mode", traversalMode.toString());
        try
        {
            tMode = TraversalMode.fromString(tModeStr);
        } catch (Exception ex)
        {
            ghRsp.addError(ex);
            return Collections.emptyList();
        }

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        List<GHPoint> points = request.getPoints();

        StopWatch sw = new StopWatch().start();
        List<QueryResult> qResults = lookup(points, encoder, ghRsp);
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
        if (ghRsp.hasErrors())
            return Collections.emptyList();

        RoutingAlgorithmFactory tmpAlgoFactory = getAlgorithmFactory(request.getHints());
        Weighting weighting;
        Graph routingGraph = ghStorage;

        boolean forceFlexibleMode = request.getHints().getBool(CHAlgoFactoryDecorator.FORCE_FLEXIBLE_ROUTING, false);
        if (!chFactoryDecorator.isForcingFlexibleModeAllowed() && forceFlexibleMode)
        {
            ghRsp.addError(new IllegalStateException("Flexible mode not enabled on the server-side"));
            return Collections.emptyList();
        }

        if (chFactoryDecorator.isEnabled() && !forceFlexibleMode)
        {
            boolean forceCHHeading = request.getHints().getBool(CHAlgoFactoryDecorator.FORCE_HEADING, false);
            if (!forceCHHeading && request.hasFavoredHeading(0))
                throw new IllegalStateException("Heading is not (fully) supported for CHGraph. See issue #483");

            if (!(tmpAlgoFactory instanceof PrepareContractionHierarchies))
                throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + tmpAlgoFactory);

            weighting = ((PrepareContractionHierarchies) tmpAlgoFactory).getWeighting();
            routingGraph = ghStorage.getGraph(CHGraph.class, weighting);
        } else
        {
            weighting = createWeighting(request.getHints(), encoder);
        }

        QueryGraph queryGraph = new QueryGraph(routingGraph);
        queryGraph.lookup(qResults);
        weighting = createTurnWeighting(weighting, queryGraph, encoder);

        int maxVisistedNodesForRequest = request.getHints().getInt("routing.maxVisitedNodes", maxVisitedNodes);
        if (maxVisistedNodesForRequest > maxVisitedNodes)
        {
            ghRsp.addError(new IllegalStateException("The routing.maxVisitedNodes parameter has to be below or equal to:" + maxVisitedNodes));
            return Collections.emptyList();
        }

        String algoStr = request.getAlgorithm().isEmpty() ? AlgorithmOptions.DIJKSTRA_BI : request.getAlgorithm();
        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(algoStr).traversalMode(tMode).flagEncoder(encoder).weighting(weighting).
                maxVisitedNodes(maxVisistedNodesForRequest).hints(request.getHints()).
                build();

        boolean viaTurnPenalty = request.getHints().getBool("pass_through", false);
        long visitedNodesSum = 0;

        boolean tmpEnableInstructions = request.getHints().getBool("instructions", enableInstructions);
        boolean tmpCalcPoints = request.getHints().getBool("calcPoints", calcPoints);
        double wayPointMaxDistance = request.getHints().getDouble("wayPointMaxDistance", 1d);
        DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
        PathMerger pathMerger = new PathMerger().
                setCalcPoints(tmpCalcPoints).
                setDouglasPeucker(peucker).
                setEnableInstructions(tmpEnableInstructions).
                setSimplifyResponse(simplifyResponse && wayPointMaxDistance > 0);

        Locale locale = request.getLocale();
        Translation tr = trMap.getWithFallBack(locale);
        List<Path> altPaths = new ArrayList<Path>(points.size() - 1);
        QueryResult fromQResult = qResults.get(0);
        // Every alternative path makes one AltResponse BUT if via points exists then reuse the altResponse object
        PathWrapper altResponse = new PathWrapper();
        ghRsp.add(altResponse);
        boolean isRoundTrip = AlgorithmOptions.ROUND_TRIP_ALT.equalsIgnoreCase(algoOpts.getAlgorithm());
        boolean isAlternativeRoute = AlgorithmOptions.ALT_ROUTE.equalsIgnoreCase(algoOpts.getAlgorithm());

        if ((isAlternativeRoute || isRoundTrip) && points.size() > 2)
        {
            ghRsp.addError(new RuntimeException("Via points are not yet supported when alternative paths or round trips are requested. The returned paths would just need an additional identification for the via point index."));
            return Collections.emptyList();
        }

        for (int placeIndex = 1; placeIndex < points.size(); placeIndex++)
        {
            if (placeIndex == 1)
            {
                // enforce start direction
                queryGraph.enforceHeading(fromQResult.getClosestNode(), request.getFavoredHeading(0), false);
            } else if (viaTurnPenalty)
            {
                if (isAlternativeRoute)
                    throw new IllegalStateException("Alternative paths and a viaTurnPenalty at the same time is currently not supported");

                // enforce straight start after via stop
                Path prevRoute = altPaths.get(placeIndex - 2);
                EdgeIteratorState incomingVirtualEdge = prevRoute.getFinalEdge();
                queryGraph.enforceHeadingByEdgeId(fromQResult.getClosestNode(), incomingVirtualEdge.getEdge(), false);
            }

            QueryResult toQResult = qResults.get(placeIndex);

            // enforce end direction
            queryGraph.enforceHeading(toQResult.getClosestNode(), request.getFavoredHeading(placeIndex), true);

            sw = new StopWatch().start();
            RoutingAlgorithm algo = tmpAlgoFactory.createAlgo(queryGraph, algoOpts);
            String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

            sw = new StopWatch().start();
            List<Path> pathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode());
            debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
            if (pathList.isEmpty())
                throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

            for (Path path : pathList)
            {
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative. Please report as bug and include:" + request);

                altPaths.add(path);
                debug += ", " + path.getDebugInfo();
            }

            altResponse.addDebugInfo(debug);

            // reset all direction enforcements in queryGraph to avoid influencing next path
            queryGraph.clearUnfavoredStatus();

            visitedNodesSum += algo.getVisitedNodes();
            fromQResult = toQResult;
        }

        if (isAlternativeRoute)
        {
            if (altPaths.isEmpty())
                throw new RuntimeException("Empty paths for alternative route calculation not expected");

            // if alternative route calculation was done then create the responses from single paths
            pathMerger.doWork(altResponse, Collections.singletonList(altPaths.get(0)), tr);
            for (int index = 1; index < altPaths.size(); index++)
            {
                altResponse = new PathWrapper();
                ghRsp.add(altResponse);
                pathMerger.doWork(altResponse, Collections.singletonList(altPaths.get(index)), tr);
            }
        } else if (isRoundTrip)
        {
            if (points.size() != altPaths.size())
                throw new RuntimeException("There should be the same number of points as paths. points:" + points.size() + ", paths:" + altPaths.size());

            pathMerger.doWork(altResponse, altPaths, tr);
        } else
        {
            if (points.size() - 1 != altPaths.size())
                throw new RuntimeException("There should be exactly one more points than paths. points:" + points.size() + ", paths:" + altPaths.size());

            pathMerger.doWork(altResponse, altPaths, tr);
        }
        ghRsp.getHints().put("visited_nodes.sum", visitedNodesSum);
        ghRsp.getHints().put("visited_nodes.average", (float) visitedNodesSum / (points.size() - 1));
        return altPaths;
    }

    List<QueryResult> lookup( List<GHPoint> points, FlagEncoder encoder, GHResponse rsp )
    {
        if (points.size() < 2)
        {
            rsp.addError(new IllegalStateException("At least 2 points have to be specified, but was:" + points.size()));
            return Collections.emptyList();
        }

        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        List<QueryResult> qResults = new ArrayList<QueryResult>(points.size());
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++)
        {
            GHPoint point = points.get(placeIndex);
            QueryResult res = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!res.isValid())
                rsp.addError(new IllegalArgumentException("Cannot find point " + placeIndex + ": " + point));

            qResults.add(res);
        }

        return qResults;
    }

    protected LocationIndex createLocationIndex( Directory dir )
    {
        LocationIndexTree tmpIndex = new LocationIndexTree(ghStorage, dir);
        tmpIndex.setResolution(preciseIndexResolution);
        tmpIndex.setMaxRegionSearch(maxRegionSearch);
        if (!tmpIndex.loadExisting())
        {
            ensureWriteAccess();
            tmpIndex.prepareIndex();
        }

        return tmpIndex;
    }

    /**
     * Initializes the location index after the import is done.
     */
    protected void initLocationIndex()
    {
        if (locationIndex != null)
            throw new IllegalStateException("Cannot initialize locationIndex twice!");

        locationIndex = createLocationIndex(ghStorage.getDirectory());
    }

    protected void prepare()
    {
        boolean tmpPrepare = chFactoryDecorator.isEnabled();
        if (tmpPrepare)
        {
            ensureWriteAccess();

            if (chFactoryDecorator.getPreparationThreads() > 1 && dataAccessType.isMMap() && !dataAccessType.isSynched())
                throw new IllegalStateException("You cannot execute CH preparation in parallel for MMAP without synching! Specify MMAP_SYNC or use 1 thread only");

            ghStorage.freeze();
            chFactoryDecorator.prepare(ghStorage.getProperties());
        }
        ghStorage.getProperties().put("prepare.done", tmpPrepare);
    }

    /**
     * Internal method to clean up the graph.
     */
    protected void cleanUp()
    {
        int prevNodeCount = ghStorage.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(ghStorage, encodingManager.fetchEdgeEncoders());
        preparation.setMinNetworkSize(minNetworkSize);
        preparation.setMinOneWayNetworkSize(minOneWayNetworkSize);
        logger.info("start finding subnetworks, " + Helper.getMemInfo());
        preparation.doWork();
        int currNodeCount = ghStorage.getNodes();
        logger.info("edges: " + ghStorage.getAllEdges().getMaxId() + ", nodes " + currNodeCount
                + ", there were " + preparation.getMaxSubnetworks()
                + " subnetworks. removed them => " + (prevNodeCount - currNodeCount)
                + " less nodes");
    }

    protected void flush()
    {
        logger.info("flushing graph " + ghStorage.toString() + ", details:" + ghStorage.toDetailsString() + ", "
                + Helper.getMemInfo() + ")");
        ghStorage.flush();
        fullyLoaded = true;
    }

    /**
     * Releases all associated resources like memory or files. But it does not remove them. To
     * remove the files created in graphhopperLocation you have to call clean().
     */
    public void close()
    {
        if (ghStorage != null)
            ghStorage.close();

        if (locationIndex != null)
            locationIndex.close();

        try
        {
            lockFactory.forceRemove(fileLockName, true);
        } catch (Exception ex)
        {
            // silently fail e.g. on Windows where we cannot remove an unreleased native lock
        }
    }

    /**
     * Removes the on-disc routing files. Call only after calling close or before importOrLoad or
     * load
     */
    public void clean()
    {
        if (getGraphHopperLocation().isEmpty())
            throw new IllegalStateException("Cannot clean GraphHopper without specified graphHopperLocation");

        File folder = new File(getGraphHopperLocation());
        Helper.removeDir(folder);
    }

    protected void ensureNotLoaded()
    {
        if (fullyLoaded)
            throw new IllegalStateException("No configuration changes are possible after loading the graph");
    }

    protected void ensureWriteAccess()
    {
        if (!allowWrites)
            throw new IllegalStateException("Writes are not allowed!");
    }
}
