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
package com.graphhopper;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.*;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LMAlgoFactoryDecorator;
import com.graphhopper.routing.profiles.DefaultEncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.template.AlternativeRoutingTemplate;
import com.graphhopper.routing.template.RoundTripRoutingTemplate;
import com.graphhopper.routing.template.RoutingTemplate;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.change.ChangeGraphHelper;
import com.graphhopper.storage.change.ChangeGraphResponse;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.graphhopper.routing.ch.CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE;
import static com.graphhopper.routing.ch.CHAlgoFactoryDecorator.EdgeBasedCHMode.OFF;
import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

/**
 * Easy to use access point to configure import and (offline) routing.
 *
 * @author Peter Karich
 * @see GraphHopperAPI
 */
public class GraphHopper implements GraphHopperAPI {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String fileLockName = "gh.lock";
    private final Set<RoutingAlgorithmFactoryDecorator> algoDecorators = new LinkedHashSet<>();
    // utils
    private final TranslationMap trMap = new TranslationMap().doImport();
    boolean removeZipped = true;
    // for graph:
    private GraphHopperStorage ghStorage;
    private EncodingManager encodingManager;
    private int defaultSegmentSize = -1;
    private String ghLocation = "";
    private DAType dataAccessType = DAType.RAM_STORE;
    private boolean sortGraph = false;
    private boolean elevation = false;
    private LockFactory lockFactory = new NativeFSLockFactory();
    private boolean allowWrites = true;
    private boolean fullyLoaded = false;
    private boolean smoothElevation = false;
    // for routing
    private int maxRoundTripRetries = 3;
    private boolean simplifyResponse = true;
    private int maxVisitedNodes = Integer.MAX_VALUE;

    private int nonChMaxWaypointDistance = Integer.MAX_VALUE;
    // for index
    private LocationIndex locationIndex;
    private int preciseIndexResolution = 300;
    private int maxRegionSearch = 4;
    // for prepare
    private int minNetworkSize = 200;
    private int minOneWayNetworkSize = 0;

    // for LM prepare
    private final LMAlgoFactoryDecorator lmFactoryDecorator = new LMAlgoFactoryDecorator();

    // for CH prepare
    private final CHAlgoFactoryDecorator chFactoryDecorator = new CHAlgoFactoryDecorator();

    // for data reader
    private String dataReaderFile;
    private double dataReaderWayPointMaxDistance = 1;
    private int dataReaderWorkerThreads = 2;
    private boolean calcPoints = true;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private FlagEncoderFactory flagEncoderFactory = new DefaultFlagEncoderFactory();
    private EncodedValueFactory encodedValueFactory = new DefaultEncodedValueFactory();
    private TagParserFactory tagParserFactory = new DefaultTagParserFactory();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();

    public GraphHopper() {
        chFactoryDecorator.setEnabled(true);
        lmFactoryDecorator.setEnabled(false);

        // order is important to use CH as base algo and set the approximation in the followed lm factory decorator
        algoDecorators.add(chFactoryDecorator);
        algoDecorators.add(lmFactoryDecorator);
    }

    /**
     * For testing only
     */
    protected GraphHopper loadGraph(GraphHopperStorage g) {
        this.ghStorage = g;
        fullyLoaded = true;
        initLocationIndex();
        return this;
    }

    /**
     * @return the first flag encoder of the encoding manager
     */
    FlagEncoder getDefaultVehicle() {
        if (encodingManager == null)
            throw new IllegalStateException("No encoding manager specified or loaded");

        return encodingManager.fetchEdgeEncoders().get(0);
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    /**
     * Specify which vehicles can be read by this GraphHopper instance. An encoding manager defines
     * how data from every vehicle is written (und read) into edges of the graph.
     */
    public GraphHopper setEncodingManager(EncodingManager em) {
        ensureNotLoaded();
        this.encodingManager = em;
        return this;
    }

    public ElevationProvider getElevationProvider() {
        return eleProvider;
    }

    public GraphHopper setElevationProvider(ElevationProvider eleProvider) {
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
    protected int getWorkerThreads() {
        return dataReaderWorkerThreads;
    }

    /**
     * Return maximum distance (in meter) to reduce points via douglas peucker while OSM import.
     */
    protected double getWayPointMaxDistance() {
        return dataReaderWayPointMaxDistance;
    }

    /**
     * This parameter specifies how to reduce points via douglas peucker while OSM import. Higher
     * value means more details, unit is meter. Default is 1. Disable via 0.
     */
    public GraphHopper setWayPointMaxDistance(double wayPointMaxDistance) {
        this.dataReaderWayPointMaxDistance = wayPointMaxDistance;
        return this;
    }

    public GraphHopper setPathDetailsBuilderFactory(PathDetailsBuilderFactory pathBuilderFactory) {
        this.pathBuilderFactory = pathBuilderFactory;
        return this;
    }

    public PathDetailsBuilderFactory getPathDetailsBuilderFactory() {
        return pathBuilderFactory;
    }

    /**
     * Configures the underlying storage and response to be used on a well equipped server. Result
     * also optimized for usage in the web module i.e. try reduce network IO.
     */
    public GraphHopper forServer() {
        setSimplifyResponse(true);
        return setInMemory();
    }

    /**
     * Configures the underlying storage to be used on a Desktop computer or within another Java
     * application with enough RAM but no network latency.
     */
    public GraphHopper forDesktop() {
        setSimplifyResponse(false);
        return setInMemory();
    }

    /**
     * Configures the underlying storage to be used on a less powerful machine like Android or
     * Raspberry Pi with only few MB of RAM.
     */
    public GraphHopper forMobile() {
        setSimplifyResponse(false);
        return setMemoryMapped();
    }

    /**
     * Precise location resolution index means also more space (disc/RAM) could be consumed and
     * probably slower query times, which would be e.g. not suitable for Android. The resolution
     * specifies the tile width (in meter).
     */
    public GraphHopper setPreciseIndexResolution(int precision) {
        ensureNotLoaded();
        preciseIndexResolution = precision;
        return this;
    }

    public GraphHopper setMinNetworkSize(int minNetworkSize, int minOneWayNetworkSize) {
        this.minNetworkSize = minNetworkSize;
        this.minOneWayNetworkSize = minOneWayNetworkSize;
        return this;
    }

    /**
     * This method call results in an in-memory graph.
     */
    public GraphHopper setInMemory() {
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
    public GraphHopper setStoreOnFlush(boolean storeOnFlush) {
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
    public GraphHopper setMemoryMapped() {
        ensureNotLoaded();
        dataAccessType = DAType.MMAP;
        return this;
    }

    /**
     * Not yet stable enough to offer it for everyone
     */
    private GraphHopper setUnsafeMemory() {
        ensureNotLoaded();
        dataAccessType = DAType.UNSAFE_STORE;
        return this;
    }

    /**
     * This method enabled or disables the speed mode (Contraction Hierarchies)
     *
     * @deprecated use {@link #setCHEnabled(boolean)} instead
     */
    public GraphHopper setCHEnable(boolean enable) {
        return setCHEnabled(enable);
    }

    public final boolean isCHEnabled() {
        return chFactoryDecorator.isEnabled();
    }

    /**
     * Enables or disables contraction hierarchies (CH). This speed-up mode is enabled by default.
     */
    public GraphHopper setCHEnabled(boolean enable) {
        ensureNotLoaded();
        chFactoryDecorator.setEnabled(enable);
        return this;
    }

    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    /**
     * This methods stops the algorithm from searching further if the resulting path would go over
     * the specified node count, important if none-CH routing is used.
     */
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    /**
     * @return true if storing and fetching elevation data is enabled. Default is false
     */
    public boolean hasElevation() {
        return elevation;
    }

    /**
     * Enable storing and fetching elevation data. Default is false
     */
    public GraphHopper setElevation(boolean includeElevation) {
        this.elevation = includeElevation;
        return this;
    }

    /**
     * This methods enables gps point calculation. If disabled only distance will be calculated.
     */
    public GraphHopper setEnableCalcPoints(boolean b) {
        calcPoints = b;
        return this;
    }

    /**
     * This method specifies if the returned path should be simplified or not, via douglas-peucker
     * or similar algorithm.
     */
    private GraphHopper setSimplifyResponse(boolean doSimplify) {
        this.simplifyResponse = doSimplify;
        return this;
    }

    public String getGraphHopperLocation() {
        return ghLocation;
    }

    /**
     * Sets the graphhopper folder.
     */
    public GraphHopper setGraphHopperLocation(String ghLocation) {
        ensureNotLoaded();
        if (ghLocation == null)
            throw new IllegalArgumentException("graphhopper location cannot be null");

        this.ghLocation = ghLocation;
        return this;
    }

    public String getDataReaderFile() {
        return dataReaderFile;
    }

    /**
     * This file can be any file type supported by the DataReader. E.g. for the OSMReader it is the
     * OSM xml (.osm), a compressed xml (.osm.zip or .osm.gz) or a protobuf file (.pbf)
     */
    public GraphHopper setDataReaderFile(String dataReaderFileStr) {
        ensureNotLoaded();
        if (isEmpty(dataReaderFileStr))
            throw new IllegalArgumentException("Data reader file cannot be empty.");

        dataReaderFile = dataReaderFileStr;
        return this;
    }

    /**
     * The underlying graph used in algorithms.
     *
     * @throws IllegalStateException if graph is not instantiated.
     */
    public GraphHopperStorage getGraphHopperStorage() {
        if (ghStorage == null)
            throw new IllegalStateException("GraphHopper storage not initialized");

        return ghStorage;
    }

    public void setGraphHopperStorage(GraphHopperStorage ghStorage) {
        this.ghStorage = ghStorage;
        fullyLoaded = true;
    }

    /**
     * The location index created from the graph.
     *
     * @throws IllegalStateException if index is not initialized
     */
    public LocationIndex getLocationIndex() {
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return locationIndex;
    }

    protected void setLocationIndex(LocationIndex locationIndex) {
        this.locationIndex = locationIndex;
    }

    /**
     * Sorts the graph which requires more RAM while import. See #12
     */
    public GraphHopper setSortGraph(boolean sortGraph) {
        ensureNotLoaded();
        this.sortGraph = sortGraph;
        return this;
    }

    public boolean isAllowWrites() {
        return allowWrites;
    }

    /**
     * Specifies if it is allowed for GraphHopper to write. E.g. for read only filesystems it is not
     * possible to create a lock file and so we can avoid write locks.
     */
    public GraphHopper setAllowWrites(boolean allowWrites) {
        this.allowWrites = allowWrites;
        return this;
    }

    public TranslationMap getTranslationMap() {
        return trMap;
    }

    public GraphHopper setFlagEncoderFactory(FlagEncoderFactory factory) {
        this.flagEncoderFactory = factory;
        return this;
    }

    public EncodedValueFactory getEncodedValueFactory() {
        return this.encodedValueFactory;
    }

    public GraphHopper setEncodedValueFactory(EncodedValueFactory factory) {
        this.encodedValueFactory = factory;
        return this;
    }

    public TagParserFactory getTagParserFactory() {
        return this.tagParserFactory;
    }

    public GraphHopper setTagParserFactory(TagParserFactory factory) {
        this.tagParserFactory = factory;
        return this;
    }

    /**
     * Reads the configuration from a CmdArgs object which can be manually filled, or via
     * CmdArgs.read(String[] args)
     */
    public GraphHopper init(CmdArgs args) {
        args.merge(CmdArgs.readFromSystemProperties());
        if (args.has("osmreader.osm"))
            throw new IllegalArgumentException("Instead osmreader.osm use datareader.file, for other changes see core/files/changelog.txt");

        String tmpOsmFile = args.get("datareader.file", "");
        if (!isEmpty(tmpOsmFile))
            dataReaderFile = tmpOsmFile;

        String graphHopperFolder = args.get("graph.location", "");
        if (isEmpty(graphHopperFolder) && isEmpty(ghLocation)) {
            if (isEmpty(dataReaderFile))
                throw new IllegalArgumentException("If no graph.location is provided you need to specify an OSM file.");

            graphHopperFolder = pruneFileEnd(dataReaderFile) + "-gh";
        }

        // graph
        setGraphHopperLocation(graphHopperFolder);
        defaultSegmentSize = args.getInt("graph.dataaccess.segment_size", defaultSegmentSize);

        String graphDATypeStr = args.get("graph.dataaccess", "RAM_STORE");
        dataAccessType = DAType.fromString(graphDATypeStr);

        sortGraph = args.getBool("graph.do_sort", sortGraph);
        removeZipped = args.getBool("graph.remove_zipped", removeZipped);
        EncodingManager.Builder emBuilder = new EncodingManager.Builder();
        String flagEncodersStr = args.get("graph.flag_encoders", "");
        String encodedValueStr = args.get("graph.encoded_values", "");
        if (!flagEncodersStr.isEmpty() || !encodedValueStr.isEmpty()) {
            if (!encodedValueStr.isEmpty())
                emBuilder.addAll(tagParserFactory, encodedValueStr);
            if (!flagEncodersStr.isEmpty())
                emBuilder.addAll(flagEncoderFactory, flagEncodersStr);
            emBuilder.setEnableInstructions(args.getBool("datareader.instructions", true));
            emBuilder.setPreferredLanguage(args.get("datareader.preferred_language", ""));
            // overwrite EncodingManager object from configuration file
            setEncodingManager(emBuilder.build());
        }

        if (args.get("graph.locktype", "native").equals("simple"))
            lockFactory = new SimpleFSLockFactory();
        else
            lockFactory = new NativeFSLockFactory();

        // elevation
        String eleProviderStr = toLowerCase(args.get("graph.elevation.provider", "noop"));
        this.smoothElevation = args.getBool("graph.elevation.smoothing", false);

        // keep fallback until 0.8
        boolean eleCalcMean = args.has("graph.elevation.calcmean")
                ? args.getBool("graph.elevation.calcmean", false)
                : args.getBool("graph.elevation.calc_mean", false);

        String cacheDirStr = args.get("graph.elevation.cache_dir", "");
        if (cacheDirStr.isEmpty())
            cacheDirStr = args.get("graph.elevation.cachedir", "");

        String baseURL = args.get("graph.elevation.base_url", "");
        if (baseURL.isEmpty())
            args.get("graph.elevation.baseurl", "");

        boolean removeTempElevationFiles = args.getBool("graph.elevation.cgiar.clear", true);
        removeTempElevationFiles = args.getBool("graph.elevation.clear", removeTempElevationFiles);

        DAType elevationDAType = DAType.fromString(args.get("graph.elevation.dataaccess", "MMAP"));
        ElevationProvider tmpProvider = ElevationProvider.NOOP;
        if (eleProviderStr.equalsIgnoreCase("srtm")) {
            tmpProvider = new SRTMProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("cgiar")) {
            tmpProvider = new CGIARProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("gmted")) {
            tmpProvider = new GMTEDProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("srtmgl1")) {
            tmpProvider = new SRTMGL1Provider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("multi")) {
            tmpProvider = new MultiSourceElevationProvider(cacheDirStr);
        }

        tmpProvider.setAutoRemoveTemporaryFiles(removeTempElevationFiles);
        tmpProvider.setCalcMean(eleCalcMean);
        if (!baseURL.isEmpty())
            tmpProvider.setBaseURL(baseURL);
        tmpProvider.setDAType(elevationDAType);
        setElevationProvider(tmpProvider);

        // optimizable prepare
        minNetworkSize = args.getInt("prepare.min_network_size", minNetworkSize);
        minOneWayNetworkSize = args.getInt("prepare.min_one_way_network_size", minOneWayNetworkSize);

        // prepare CH, LM, ...
        for (RoutingAlgorithmFactoryDecorator decorator : algoDecorators) {
            decorator.init(args);
        }

        // osm import
        dataReaderWayPointMaxDistance = args.getDouble(Routing.INIT_WAY_POINT_MAX_DISTANCE, dataReaderWayPointMaxDistance);

        dataReaderWorkerThreads = args.getInt("datareader.worker_threads", dataReaderWorkerThreads);

        // index
        preciseIndexResolution = args.getInt("index.high_resolution", preciseIndexResolution);
        maxRegionSearch = args.getInt("index.max_region_search", maxRegionSearch);

        // routing
        maxVisitedNodes = args.getInt(Routing.INIT_MAX_VISITED_NODES, Integer.MAX_VALUE);
        maxRoundTripRetries = args.getInt(RoundTrip.INIT_MAX_RETRIES, maxRoundTripRetries);
        nonChMaxWaypointDistance = args.getInt(Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE, Integer.MAX_VALUE);

        return this;
    }

    private void printInfo() {
        logger.info("version " + Constants.VERSION + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")");
        if (ghStorage != null)
            logger.info("graph " + ghStorage.toString() + ", details:" + ghStorage.toDetailsString());
    }

    /**
     * Imports provided data from disc and creates graph. Depending on the settings the resulting
     * graph will be stored to disc so on a second call this method will only load the graph from
     * disc which is usually a lot faster.
     */
    public GraphHopper importOrLoad() {
        if (!load(ghLocation)) {
            printInfo();
            process(ghLocation, false);
        } else {
            printInfo();
        }
        return this;
    }

    /**
     * Imports and processes data, storing it to disk when complete.
     */
    public void importAndClose() {
        if (!load(ghLocation)) {
            printInfo();
            process(ghLocation, true);
        } else {
            printInfo();
            logger.info("Graph already imported into " + ghLocation);
        }
        close();
    }

    /**
     * Creates the graph from OSM data.
     */
    private GraphHopper process(String graphHopperLocation, boolean closeEarly) {
        setGraphHopperLocation(graphHopperLocation);
        GHLock lock = null;
        try {
            if (ghStorage.getDirectory().getDefaultType().isStoring()) {
                lockFactory.setLockDir(new File(graphHopperLocation));
                lock = lockFactory.create(fileLockName, true);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid multiple writers we need to obtain a write lock but it failed. In " + graphHopperLocation, lock.getObtainFailedReason());
            }

            readData();
            cleanUp();
            postProcessing(closeEarly);
            flush();
        } finally {
            if (lock != null)
                lock.release();
        }
        return this;
    }

    private void readData() {
        try {
            DataReader reader = importData();
            DateFormat f = createFormatter();
            ghStorage.getProperties().put("datareader.import.date", f.format(new Date()));
            if (reader.getDataDate() != null)
                ghStorage.getProperties().put("datareader.data.date", f.format(reader.getDataDate()));
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read file " + getDataReaderFile(), ex);
        }
    }

    protected DataReader importData() throws IOException {
        ensureWriteAccess();
        if (ghStorage == null)
            throw new IllegalStateException("Load graph before importing OSM data");

        if (dataReaderFile == null)
            throw new IllegalStateException("Couldn't load from existing folder: " + ghLocation
                    + " but also cannot use file for DataReader as it wasn't specified!");

        DataReader reader = createReader(ghStorage);
        logger.info("using " + ghStorage.toString() + ", memory:" + getMemInfo());
        reader.readGraph();
        return reader;
    }

    protected DataReader createReader(GraphHopperStorage ghStorage) {
        throw new UnsupportedOperationException("Cannot create DataReader. Solutions: avoid import via calling load directly, "
                + "provide a DataReader or use e.g. GraphHopperOSM or a different subclass");
    }

    protected DataReader initDataReader(DataReader reader) {
        if (dataReaderFile == null)
            throw new IllegalArgumentException("No file for DataReader specified");

        logger.info("start creating graph from " + dataReaderFile);
        return reader.setFile(new File(dataReaderFile)).
                setElevationProvider(eleProvider).
                setWorkerThreads(dataReaderWorkerThreads).
                setWayPointMaxDistance(dataReaderWayPointMaxDistance).
                setSmoothElevation(this.smoothElevation);
    }

    /**
     * Opens existing graph folder.
     *
     * @param graphHopperFolder is the folder containing graphhopper files. Can be a compressed file
     *                          too ala folder-content.ghz.
     */
    @Override
    public boolean load(String graphHopperFolder) {
        if (isEmpty(graphHopperFolder))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        if (fullyLoaded)
            throw new IllegalStateException("graph is already successfully loaded");

        File tmpFileOrFolder = new File(graphHopperFolder);

        if (!tmpFileOrFolder.isDirectory() && tmpFileOrFolder.exists()) {
            throw new IllegalArgumentException("GraphHopperLocation cannot be an existing file. Has to be either non-existing or a folder.");
        } else {
            File compressed = new File(graphHopperFolder + ".ghz");
            if (compressed.exists() && !compressed.isDirectory()) {
                try {
                    new Unzipper().unzip(compressed.getAbsolutePath(), graphHopperFolder, removeZipped);
                } catch (IOException ex) {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath()
                            + " to " + graphHopperFolder, ex);
                }
            }
        }

        setGraphHopperLocation(graphHopperFolder);

        if (encodingManager == null)
            setEncodingManager(EncodingManager.create(encodedValueFactory, flagEncoderFactory, ghLocation));

        if (!allowWrites && dataAccessType.isMMap())
            dataAccessType = DAType.MMAP_RO;

        GHDirectory dir = new GHDirectory(ghLocation, dataAccessType);
        GraphExtension ext = encodingManager.needsTurnCostsSupport()
                ? new TurnCostExtension() : new GraphExtension.NoOpExtension();

        if (lmFactoryDecorator.isEnabled())
            initLMAlgoFactoryDecorator();

        if (chFactoryDecorator.isEnabled()) {
            initCHAlgoFactoryDecorator();
            ghStorage = new GraphHopperStorage(chFactoryDecorator.getCHProfiles(), dir, encodingManager, hasElevation(), ext);
        } else {
            ghStorage = new GraphHopperStorage(dir, encodingManager, hasElevation(), ext);
        }

        ghStorage.setSegmentSize(defaultSegmentSize);

        if (!new File(graphHopperFolder).exists())
            return false;

        GHLock lock = null;
        try {
            // create locks only if writes are allowed, if they are not allowed a lock cannot be created 
            // (e.g. on a read only filesystem locks would fail)
            if (ghStorage.getDirectory().getDefaultType().isStoring() && isAllowWrites()) {
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
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        RoutingAlgorithmFactory routingAlgorithmFactory = new RoutingAlgorithmFactorySimple();
        for (RoutingAlgorithmFactoryDecorator decorator : algoDecorators) {
            if (decorator.isEnabled())
                routingAlgorithmFactory = decorator.getDecoratedAlgorithmFactory(routingAlgorithmFactory, map);
        }

        return routingAlgorithmFactory;
    }

    public GraphHopper addAlgorithmFactoryDecorator(RoutingAlgorithmFactoryDecorator algoFactoryDecorator) {
        if (!algoDecorators.add(algoFactoryDecorator))
            throw new IllegalArgumentException("Decorator was already added " + algoFactoryDecorator.getClass());

        return this;
    }

    public final CHAlgoFactoryDecorator getCHFactoryDecorator() {
        return chFactoryDecorator;
    }

    private void initCHAlgoFactoryDecorator() {
        if (!chFactoryDecorator.hasCHProfiles()) {
            for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
                for (String chWeightingStr : chFactoryDecorator.getCHProfileStrings()) {
                    // ghStorage is null at this point

                    // extract weighting string and u-turn-costs
                    String configStr = "";
                    if (chWeightingStr.contains("|")) {
                        configStr = chWeightingStr;
                        chWeightingStr = chWeightingStr.split("\\|")[0];
                    }
                    PMap config = new PMap(configStr);
                    int uTurnCosts = config.getInt(Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);

                    CHAlgoFactoryDecorator.EdgeBasedCHMode edgeBasedCHMode = chFactoryDecorator.getEdgeBasedCHMode();
                    if (!(edgeBasedCHMode == EDGE_OR_NODE && encoder.supports(TurnWeighting.class))) {
                        chFactoryDecorator.addCHProfile(CHProfile.nodeBased(createWeighting(new HintsMap(chWeightingStr), encoder, null)));
                    }
                    if (edgeBasedCHMode != OFF && encoder.supports(TurnWeighting.class)) {
                        chFactoryDecorator.addCHProfile(CHProfile.edgeBased(createWeighting(new HintsMap(chWeightingStr), encoder, null), uTurnCosts));
                    }
                }
            }
        }
    }

    public final LMAlgoFactoryDecorator getLMFactoryDecorator() {
        return lmFactoryDecorator;
    }

    private void initLMAlgoFactoryDecorator() {
        if (lmFactoryDecorator.hasWeightings())
            return;

        for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
            for (String lmWeightingStr : lmFactoryDecorator.getWeightingsAsStrings()) {
                Weighting weighting = createWeighting(new HintsMap(lmWeightingStr), encoder, null);
                lmFactoryDecorator.addWeighting(weighting);
            }
        }
    }

    /**
     * Does the preparation and creates the location index
     */
    public void postProcessing() {
        postProcessing(false);
    }

    /**
     * Does the preparation and creates the location index
     *
     * @param closeEarly release resources as early as possible
     */
    public void postProcessing(boolean closeEarly) {
        // Later: move this into the GraphStorage.optimize method
        // Or: Doing it after preparation to optimize shortcuts too. But not possible yet #12

        if (sortGraph) {
            if (ghStorage.isCHPossible() && isCHPrepared())
                throw new IllegalArgumentException("Sorting a prepared CHGraph is not possible yet. See #12");

            GraphHopperStorage newGraph = GHUtility.newStorage(ghStorage);
            GHUtility.sortDFS(ghStorage, newGraph);
            logger.info("graph sorted (" + getMemInfo() + ")");
            ghStorage = newGraph;
        }

        if (!hasInterpolated() && hasElevation()) {
            interpolateBridgesAndOrTunnels();
        }

        initLocationIndex();

        if (lmFactoryDecorator.isEnabled())
            lmFactoryDecorator.createPreparations(ghStorage, locationIndex);
        loadOrPrepareLM(closeEarly);

        if (chFactoryDecorator.isEnabled())
            chFactoryDecorator.createPreparations(ghStorage);
        if (!isCHPrepared())
            prepareCH(closeEarly);
    }

    private static final String INTERPOLATION_KEY = "prepare.elevation_interpolation.done";

    private boolean hasInterpolated() {
        return "true".equals(ghStorage.getProperties().get(INTERPOLATION_KEY));
    }

    void interpolateBridgesAndOrTunnels() {
        if (ghStorage.getEncodingManager().hasEncodedValue(RoadEnvironment.KEY)) {
            EnumEncodedValue<RoadEnvironment> roadEnvEnc = ghStorage.getEncodingManager().getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
            StopWatch sw = new StopWatch().start();
            new EdgeElevationInterpolator(ghStorage, roadEnvEnc, RoadEnvironment.TUNNEL).execute();
            float tunnel = sw.stop().getSeconds();
            sw = new StopWatch().start();
            new EdgeElevationInterpolator(ghStorage, roadEnvEnc, RoadEnvironment.BRIDGE).execute();
            ghStorage.getProperties().put(INTERPOLATION_KEY, true);
            logger.info("Bridge interpolation " + (int) sw.stop().getSeconds() + "s, " + "tunnel interpolation " + (int) tunnel + "s");
        }
    }

    /**
     * Based on the hintsMap and the specified encoder a Weighting instance can be
     * created. Note that all URL parameters are available in the hintsMap as String if
     * you use the web module.
     *
     * @param hintsMap all parameters influencing the weighting. E.g. parameters coming via
     *                 GHRequest.getHints or directly via "&amp;api.xy=" from the URL of the web UI
     * @param encoder  the required vehicle
     * @param graph    The Graph enables the Weighting for NodeAccess and more
     * @return the weighting to be used for route calculation
     * @see HintsMap
     */
    public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder, Graph graph) {
        String weightingStr = toLowerCase(hintsMap.getWeighting());
        Weighting weighting = null;

        if (encoder.supports(GenericWeighting.class)) {
            weighting = new GenericWeighting((DataFlagEncoder) encoder, hintsMap);
        } else if ("shortest".equalsIgnoreCase(weightingStr)) {
            weighting = new ShortestWeighting(encoder);
        } else if ("fastest".equalsIgnoreCase(weightingStr) || weightingStr.isEmpty()) {
            if (encoder.supports(PriorityWeighting.class))
                weighting = new PriorityWeighting(encoder, hintsMap);
            else
                weighting = new FastestWeighting(encoder, hintsMap);
        } else if ("curvature".equalsIgnoreCase(weightingStr)) {
            if (encoder.supports(CurvatureWeighting.class))
                weighting = new CurvatureWeighting(encoder, hintsMap);

        } else if ("short_fastest".equalsIgnoreCase(weightingStr)) {
            weighting = new ShortFastestWeighting(encoder, hintsMap);
        }

        if (weighting == null)
            throw new IllegalArgumentException("weighting " + weightingStr + " not supported");

        if (hintsMap.has(Routing.BLOCK_AREA)) {
            String blockAreaStr = hintsMap.get(Parameters.Routing.BLOCK_AREA, "");
            GraphEdgeIdFinder.BlockArea blockArea = new GraphEdgeIdFinder(graph, locationIndex).
                    parseBlockArea(blockAreaStr, DefaultEdgeFilter.allEdges(encoder), hintsMap.getDouble("block_area.edge_id_max_area", 1000 * 1000));
            return new BlockAreaWeighting(weighting, blockArea);
        }

        return weighting;
    }

    /**
     * Potentially wraps the specified weighting into a TurnWeighting instance.
     */
    public Weighting createTurnWeighting(Graph graph, Weighting weighting, TraversalMode tMode, double uTurnCosts) {
        FlagEncoder encoder = weighting.getFlagEncoder();
        if (encoder.supports(TurnWeighting.class) && tMode.isEdgeBased())
            return new TurnWeighting(weighting, (TurnCostExtension) graph.getExtension(), uTurnCosts);
        return weighting;
    }

    @Override
    public GHResponse route(GHRequest request) {
        GHResponse response = new GHResponse();
        calcPaths(request, response);
        return response;
    }

    /**
     * This method calculates the alternative path list using the low level Path objects.
     */
    public List<Path> calcPaths(GHRequest request, GHResponse ghRsp) {
        if (ghStorage == null || !fullyLoaded)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");

        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");

        // default handling
        String vehicle = request.getVehicle();
        if (vehicle.isEmpty()) {
            vehicle = getDefaultVehicle().toString();
            request.setVehicle(vehicle);
        }

        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            if (!encodingManager.hasEncoder(vehicle))
                throw new IllegalArgumentException("Vehicle not supported: " + vehicle + ". Supported are: " + encodingManager.toString());

            FlagEncoder encoder = encodingManager.getEncoder(vehicle);
            HintsMap hints = request.getHints();

            // we use edge-based routing if the encoder supports turn-costs *unless* the edge_based parameter is set
            // explicitly.
            TraversalMode tMode = encoder.supports(TurnWeighting.class) ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            if (hints.has(Routing.EDGE_BASED))
                tMode = hints.getBool(Routing.EDGE_BASED, false) ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;

            if (tMode.isEdgeBased() && !encoder.supports(TurnWeighting.class)) {
                throw new IllegalArgumentException("You need a turn cost extension to make use of edge_based=true, e.g. use car|turn_costs=true");
            }

            if (!tMode.isEdgeBased() && !request.getCurbsides().isEmpty()) {
                throw new IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to set " + Routing.EDGE_BASED + " to true");
            }

            boolean disableCH = hints.getBool(CH.DISABLE, false);
            if (!chFactoryDecorator.isDisablingAllowed() && disableCH)
                throw new IllegalArgumentException("Disabling CH not allowed on the server-side");

            boolean disableLM = hints.getBool(Landmark.DISABLE, false);
            if (!lmFactoryDecorator.isDisablingAllowed() && disableLM)
                throw new IllegalArgumentException("Disabling LM not allowed on the server-side");

            String algoStr = request.getAlgorithm();
            if (algoStr.isEmpty())
                algoStr = chFactoryDecorator.isEnabled() && !disableCH ? DIJKSTRA_BI : ASTAR_BI;

            List<GHPoint> points = request.getPoints();
            // TODO Maybe we should think about a isRequestValid method that checks all that stuff that we could do to fail fast
            // For example see #734
            checkIfPointsAreInBounds(points);

            RoutingTemplate routingTemplate;
            if (ROUND_TRIP.equalsIgnoreCase(algoStr))
                routingTemplate = new RoundTripRoutingTemplate(request, ghRsp, locationIndex, encodingManager, maxRoundTripRetries);
            else if (ALT_ROUTE.equalsIgnoreCase(algoStr))
                routingTemplate = new AlternativeRoutingTemplate(request, ghRsp, locationIndex, encodingManager);
            else
                routingTemplate = new ViaRoutingTemplate(request, ghRsp, locationIndex, encodingManager);

            List<Path> altPaths = null;
            int maxRetries = routingTemplate.getMaxRetries();
            Locale locale = request.getLocale();
            Translation tr = trMap.getWithFallBack(locale);
            for (int i = 0; i < maxRetries; i++) {
                StopWatch sw = new StopWatch().start();
                List<QueryResult> qResults = routingTemplate.lookup(points, encoder);
                ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
                if (ghRsp.hasErrors())
                    return Collections.emptyList();

                RoutingAlgorithmFactory tmpAlgoFactory = getAlgorithmFactory(hints);
                Weighting weighting;
                QueryGraph queryGraph;

                if (chFactoryDecorator.isEnabled() && !disableCH) {
                    boolean forceCHHeading = hints.getBool(CH.FORCE_HEADING, false);
                    if (!forceCHHeading && request.hasFavoredHeading(0))
                        throw new IllegalArgumentException("Heading is not (fully) supported for CHGraph. See issue #483");

                    // if LM is enabled we have the LMFactory with the CH algo!
                    RoutingAlgorithmFactory chAlgoFactory = tmpAlgoFactory;
                    if (tmpAlgoFactory instanceof LMAlgoFactoryDecorator.LMRAFactory)
                        chAlgoFactory = ((LMAlgoFactoryDecorator.LMRAFactory) tmpAlgoFactory).getDefaultAlgoFactory();

                    if (chAlgoFactory instanceof PrepareContractionHierarchies) {
                        CHProfile chProfile = ((PrepareContractionHierarchies) chAlgoFactory).getCHProfile();
                        queryGraph = QueryGraph.lookup(ghStorage.getCHGraph(chProfile), qResults);
                        weighting = chProfile.getWeighting();
                    } else {
                        throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + tmpAlgoFactory);
                    }
                } else {
                    checkNonChMaxWaypointDistance(points);
                    queryGraph = QueryGraph.lookup(ghStorage, qResults);
                    weighting = createWeighting(hints, encoder, queryGraph);
                }
                ghRsp.addDebugInfo("tmode:" + tMode.toString());

                int maxVisitedNodesForRequest = hints.getInt(Routing.MAX_VISITED_NODES, maxVisitedNodes);
                if (maxVisitedNodesForRequest > maxVisitedNodes)
                    throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + maxVisitedNodes);

                int uTurnCostInt = request.getHints().getInt(Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
                if (uTurnCostInt != INFINITE_U_TURN_COSTS && !tMode.isEdgeBased()) {
                    throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, use `" + Routing.EDGE_BASED + "=true'");
                }
                double uTurnCosts = uTurnCostInt == INFINITE_U_TURN_COSTS ? Double.POSITIVE_INFINITY : uTurnCostInt;
                weighting = createTurnWeighting(queryGraph, weighting, tMode, uTurnCosts);

                AlgorithmOptions algoOpts = AlgorithmOptions.start().
                        algorithm(algoStr).traversalMode(tMode).weighting(weighting).
                        maxVisitedNodes(maxVisitedNodesForRequest).
                        hints(hints).
                        build();

                // do the actual route calculation !
                altPaths = routingTemplate.calcPaths(queryGraph, tmpAlgoFactory, algoOpts, encoder);

                boolean tmpEnableInstructions = hints.getBool(Routing.INSTRUCTIONS, getEncodingManager().isEnableInstructions());
                boolean tmpCalcPoints = hints.getBool(Routing.CALC_POINTS, calcPoints);
                double wayPointMaxDistance = hints.getDouble(Routing.WAY_POINT_MAX_DISTANCE, 1d);

                DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
                PathMerger pathMerger = new PathMerger(queryGraph.getBaseGraph(), weighting).
                        setCalcPoints(tmpCalcPoints).
                        setDouglasPeucker(peucker).
                        setEnableInstructions(tmpEnableInstructions).
                        setPathDetailsBuilders(pathBuilderFactory, request.getPathDetails()).
                        setSimplifyResponse(simplifyResponse && wayPointMaxDistance > 0);

                if (request.hasFavoredHeading(0))
                    pathMerger.setFavoredHeading(request.getFavoredHeading(0));

                if (routingTemplate.isReady(pathMerger, tr))
                    break;
            }

            return altPaths;

        } catch (IllegalArgumentException ex) {
            ghRsp.addError(ex);
            return Collections.emptyList();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * This method applies the changes to the graph specified as feature collection. It does so by locking the routing
     * to avoid concurrent changes which could result in incorrect routing (like when done while a Dijkstra search) or
     * also while just reading one edge row (inconsistent edge properties).
     */
    public ChangeGraphResponse changeGraph(Collection<JsonFeature> collection) {
        // TODO allow calling this method if called before CH preparation
        if (getCHFactoryDecorator().isEnabled())
            throw new IllegalArgumentException("To use the changeGraph API you need to turn off CH");

        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            ChangeGraphHelper overlay = createChangeGraphHelper(ghStorage, locationIndex);
            long updateCount = overlay.applyChanges(encodingManager, collection);
            return new ChangeGraphResponse(updateCount);
        } finally {
            writeLock.unlock();
        }
    }

    protected ChangeGraphHelper createChangeGraphHelper(Graph graph, LocationIndex locationIndex) {
        return new ChangeGraphHelper(graph, locationIndex);
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = getGraphHopperStorage().getBounds();
        for (int i = 0; i < points.size(); i++) {
            GHPoint point = points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point, i);
            }
        }
    }

    private void checkNonChMaxWaypointDistance(List<GHPoint> points) {
        if (nonChMaxWaypointDistance == Integer.MAX_VALUE) {
            return;
        }
        GHPoint lastPoint = points.get(0);
        GHPoint point;
        double dist;
        DistanceCalc calc = DIST_3D;
        for (int i = 1; i < points.size(); i++) {
            point = points.get(i);
            dist = calc.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
            if (dist > nonChMaxWaypointDistance) {
                Map<String, Object> detailMap = new HashMap<>(2);
                detailMap.put("from", i - 1);
                detailMap.put("to", i);
                throw new PointDistanceExceededException("Point " + i + " is too far from Point " + (i - 1) + ": " + point, detailMap);
            }
            lastPoint = point;
        }
    }

    protected LocationIndex createLocationIndex(Directory dir) {
        LocationIndexTree tmpIndex = new LocationIndexTree(ghStorage, dir);
        tmpIndex.setResolution(preciseIndexResolution);
        tmpIndex.setMaxRegionSearch(maxRegionSearch);
        if (!tmpIndex.loadExisting()) {
            ensureWriteAccess();
            tmpIndex.prepareIndex();
        }

        return tmpIndex;
    }

    /**
     * Initializes the location index after the import is done.
     */
    protected void initLocationIndex() {
        if (locationIndex != null)
            throw new IllegalStateException("Cannot initialize locationIndex twice!");

        locationIndex = createLocationIndex(ghStorage.getDirectory());
    }

    private boolean isCHPrepared() {
        return "true".equals(ghStorage.getProperties().get(CH.PREPARE + "done"))
                // remove old property in >0.9
                || "true".equals(ghStorage.getProperties().get("prepare.done"));
    }

    private boolean isLMPrepared() {
        return "true".equals(ghStorage.getProperties().get(Landmark.PREPARE + "done"));
    }

    protected void prepareCH(boolean closeEarly) {
        boolean tmpPrepare = chFactoryDecorator.isEnabled();
        if (tmpPrepare) {
            ensureWriteAccess();

            if (closeEarly) {
                locationIndex.flush();
                locationIndex.close();
                ghStorage.flushAndCloseEarly();
            }

            ghStorage.freeze();
            chFactoryDecorator.prepare(ghStorage.getProperties(), closeEarly);
            ghStorage.getProperties().put(CH.PREPARE + "done", true);
        }
    }

    /**
     * For landmarks it is required to always call this method: either it creates the landmark data or it loads it.
     */
    protected void loadOrPrepareLM(boolean closeEarly) {
        boolean tmpPrepare = lmFactoryDecorator.isEnabled() && !lmFactoryDecorator.getPreparations().isEmpty();
        if (tmpPrepare) {
            ensureWriteAccess();
            ghStorage.freeze();
            if (lmFactoryDecorator.loadOrDoWork(ghStorage.getProperties(), closeEarly))
                ghStorage.getProperties().put(Landmark.PREPARE + "done", true);
        }
    }

    /**
     * Internal method to clean up the graph.
     */
    protected void cleanUp() {
        int prevNodeCount = ghStorage.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(ghStorage, encodingManager.fetchEdgeEncoders());
        preparation.setMinNetworkSize(minNetworkSize);
        preparation.setMinOneWayNetworkSize(minOneWayNetworkSize);
        preparation.doWork();
        int currNodeCount = ghStorage.getNodes();
        logger.info("edges: " + Helper.nf(ghStorage.getAllEdges().length()) + ", nodes " + Helper.nf(currNodeCount)
                + ", there were " + Helper.nf(preparation.getMaxSubnetworks())
                + " subnetworks. removed them => " + Helper.nf(prevNodeCount - currNodeCount)
                + " less nodes");
    }

    protected void flush() {
        logger.info("flushing graph " + ghStorage.toString() + ", details:" + ghStorage.toDetailsString() + ", "
                + getMemInfo() + ")");
        ghStorage.flush();
        logger.info("flushed graph " + getMemInfo() + ")");
        fullyLoaded = true;
    }

    /**
     * Releases all associated resources like memory or files. But it does not remove them. To
     * remove the files created in graphhopperLocation you have to call clean().
     */
    public void close() {
        if (ghStorage != null)
            ghStorage.close();

        if (locationIndex != null)
            locationIndex.close();

        try {
            lockFactory.forceRemove(fileLockName, true);
        } catch (Exception ex) {
            // silently fail e.g. on Windows where we cannot remove an unreleased native lock
        }
    }

    /**
     * Removes the on-disc routing files. Call only after calling close or before importOrLoad or
     * load
     */
    public void clean() {
        if (getGraphHopperLocation().isEmpty())
            throw new IllegalStateException("Cannot clean GraphHopper without specified graphHopperLocation");

        File folder = new File(getGraphHopperLocation());
        removeDir(folder);
    }

    protected void ensureNotLoaded() {
        if (fullyLoaded)
            throw new IllegalStateException("No configuration changes are possible after loading the graph");
    }

    protected void ensureWriteAccess() {
        if (!allowWrites)
            throw new IllegalStateException("Writes are not allowed!");
    }

    public void setNonChMaxWaypointDistance(int nonChMaxWaypointDistance) {
        this.nonChMaxWaypointDistance = nonChMaxWaypointDistance;
    }

}
