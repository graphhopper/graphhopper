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

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.dem.*;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LMPreparationHandler;
import com.graphhopper.routing.lm.LMProfile;
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
import com.graphhopper.routing.weighting.custom.CustomProfileConfig;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.*;
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

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;
import static com.graphhopper.util.Parameters.Routing.POINT_HINT;

/**
 * Easy to use access point to configure import and (offline) routing.
 *
 * @author Peter Karich
 * @see GraphHopperAPI
 */
public class GraphHopper implements GraphHopperAPI {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, ProfileConfig> profilesByName = new LinkedHashMap<>();
    private final String fileLockName = "gh.lock";
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
    private final RoutingConfig routingConfig = new RoutingConfig();
    // for index
    private LocationIndex locationIndex;
    private int preciseIndexResolution = 300;
    private int maxRegionSearch = 4;
    // for prepare
    private int minNetworkSize = 200;
    private int minOneWayNetworkSize = 0;

    // preparation handlers
    private final LMPreparationHandler lmPreparationHandler = new LMPreparationHandler();
    private final CHPreparationHandler chPreparationHandler = new CHPreparationHandler();

    // for data reader
    private String dataReaderFile;
    private double dataReaderWayPointMaxDistance = 1;
    private int dataReaderWorkerThreads = 2;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private FlagEncoderFactory flagEncoderFactory = new DefaultFlagEncoderFactory();
    private EncodedValueFactory encodedValueFactory = new DefaultEncodedValueFactory();
    private TagParserFactory tagParserFactory = new DefaultTagParserFactory();
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();

    public GraphHopper() {
    }

    /**
     * For testing only
     */
    protected GraphHopper loadGraph(GraphHopperStorage g) {
        this.ghStorage = g;
        setFullyLoaded();
        initLocationIndex();
        return this;
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
     * Sets the routing profiles that can be used for CH/LM preparation. So far adding these profiles is only required
     * so we can refer to them when configuring the CH/LM preparations, later it will be required to specify all
     * routing profiles that shall be supported by this GraphHopper instance here.
     * <p>
     * Here is an example how to setup two CH profiles and one LM profile (via the Java API)
     *
     * <pre>
     * {@code
     *   // make sure the encoding manager contains a "car" and a "bike" flag encoder
     *   hopper.setProfiles(
     *     new ProfileConfig("my_car").setVehicle("car").setWeighting("shortest"),
     *     new ProfileConfig("your_bike").setVehicle("bike").setWeighting("fastest")
     *   );
     *   hopper.getCHPreparationHandler().setCHProfileConfigs(
     *     new CHProfileConfig("my_car"),
     *     new CHProfileConfig("your_bike")
     *   );
     *   hopper.getLMPreparationHandler().setLMProfileConfigs(
     *     new LMProfileConfig("your_bike")
     *   );
     * }
     * </pre>
     * <p>
     * See also https://github.com/graphhopper/graphhopper/pull/1922.
     *
     * @see CHPreparationHandler#setCHProfileConfigs
     * @see LMPreparationHandler#setLMProfileConfigs
     */
    public GraphHopper setProfiles(ProfileConfig... profiles) {
        return setProfiles(Arrays.asList(profiles));
    }

    public GraphHopper setProfiles(List<ProfileConfig> profiles) {
        profilesByName.clear();
        for (ProfileConfig profile : profiles) {
            ProfileConfig previous = this.profilesByName.put(profile.getName(), profile);
            if (previous != null)
                throw new IllegalArgumentException("Profile names must be unique. Duplicate name: '" + profile.getName() + "'");
        }
        return this;
    }

    public List<ProfileConfig> getProfiles() {
        return new ArrayList<>(profilesByName.values());
    }

    /**
     * Returns the profile for the given profile name, or null if it does not exist
     */
    public ProfileConfig getProfile(String profileName) {
        return profilesByName.get(profileName);
    }

    public int getMaxVisitedNodes() {
        return routingConfig.getMaxVisitedNodes();
    }

    /**
     * This methods stops the algorithm from searching further if the resulting path would go over
     * the specified node count, important if none-CH routing is used.
     */
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        routingConfig.setMaxVisitedNodes(maxVisitedNodes);
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
        routingConfig.setCalcPoints(b);
        return this;
    }

    /**
     * This method specifies if the returned path should be simplified or not, via douglas-peucker
     * or similar algorithm.
     */
    private GraphHopper setSimplifyResponse(boolean doSimplify) {
        routingConfig.setSimplifyResponse(doSimplify);
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
        setFullyLoaded();
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
     * Reads the configuration from a {@link GraphHopperConfig} object which can be manually filled, or more typically
     * is read from `config.yml`.
     */
    public GraphHopper init(GraphHopperConfig ghConfig) {
        if (ghConfig.has("osmreader.osm"))
            throw new IllegalArgumentException("Instead osmreader.osm use datareader.file, for other changes see core/files/changelog.txt");

        String tmpOsmFile = ghConfig.getString("datareader.file", "");
        if (!isEmpty(tmpOsmFile))
            dataReaderFile = tmpOsmFile;

        String graphHopperFolder = ghConfig.getString("graph.location", "");
        if (isEmpty(graphHopperFolder) && isEmpty(ghLocation)) {
            if (isEmpty(dataReaderFile))
                throw new IllegalArgumentException("If no graph.location is provided you need to specify an OSM file.");

            graphHopperFolder = pruneFileEnd(dataReaderFile) + "-gh";
        }

        // graph
        setGraphHopperLocation(graphHopperFolder);
        defaultSegmentSize = ghConfig.getInt("graph.dataaccess.segment_size", defaultSegmentSize);

        String graphDATypeStr = ghConfig.getString("graph.dataaccess", "RAM_STORE");
        dataAccessType = DAType.fromString(graphDATypeStr);

        sortGraph = ghConfig.getBool("graph.do_sort", sortGraph);
        removeZipped = ghConfig.getBool("graph.remove_zipped", removeZipped);
        EncodingManager encodingManager = createEncodingManager(ghConfig);
        if (encodingManager != null) {
            // overwrite EncodingManager object from configuration file
            setEncodingManager(encodingManager);
        }

        if (ghConfig.getString("graph.locktype", "native").equals("simple"))
            lockFactory = new SimpleFSLockFactory();
        else
            lockFactory = new NativeFSLockFactory();

        // elevation
        this.smoothElevation = ghConfig.getBool("graph.elevation.smoothing", false);
        ElevationProvider elevationProvider = createElevationProvider(ghConfig);
        setElevationProvider(elevationProvider);

        // optimizable prepare
        minNetworkSize = ghConfig.getInt("prepare.min_network_size", minNetworkSize);
        minOneWayNetworkSize = ghConfig.getInt("prepare.min_one_way_network_size", minOneWayNetworkSize);

        // profiles
        setProfiles(ghConfig.getProfiles());

        // prepare CH&LM
        chPreparationHandler.init(ghConfig);
        lmPreparationHandler.init(ghConfig);

        // osm import
        dataReaderWayPointMaxDistance = ghConfig.getDouble(Routing.INIT_WAY_POINT_MAX_DISTANCE, dataReaderWayPointMaxDistance);

        dataReaderWorkerThreads = ghConfig.getInt("datareader.worker_threads", dataReaderWorkerThreads);

        // index
        preciseIndexResolution = ghConfig.getInt("index.high_resolution", preciseIndexResolution);
        maxRegionSearch = ghConfig.getInt("index.max_region_search", maxRegionSearch);

        // routing
        routingConfig.setMaxVisitedNodes(ghConfig.getInt(Routing.INIT_MAX_VISITED_NODES, routingConfig.getMaxVisitedNodes()));
        routingConfig.setMaxRoundTripRetries(ghConfig.getInt(RoundTrip.INIT_MAX_RETRIES, routingConfig.getMaxRoundTripRetries()));
        routingConfig.setNonChMaxWaypointDistance(ghConfig.getInt(Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE, routingConfig.getNonChMaxWaypointDistance()));

        return this;
    }

    private EncodingManager createEncodingManager(GraphHopperConfig ghConfig) {
        String flagEncodersStr = ghConfig.getString("graph.flag_encoders", "");
        String encodedValueStr = ghConfig.getString("graph.encoded_values", "");
        if (flagEncodersStr.isEmpty() && encodedValueStr.isEmpty()) {
            return null;
        } else {
            EncodingManager.Builder emBuilder = new EncodingManager.Builder();
            if (!encodedValueStr.isEmpty())
                emBuilder.addAll(tagParserFactory, encodedValueStr);
            registerCustomEncodedValues(emBuilder);
            if (!flagEncodersStr.isEmpty())
                emBuilder.addAll(flagEncoderFactory, flagEncodersStr);
            emBuilder.setEnableInstructions(ghConfig.getBool("datareader.instructions", true));
            emBuilder.setPreferredLanguage(ghConfig.getString("datareader.preferred_language", ""));
            emBuilder.setDateRangeParser(DateRangeParser.createInstance(ghConfig.getString("datareader.date_range_parser_day", "")));
            return emBuilder.build();
        }
    }

    private static ElevationProvider createElevationProvider(GraphHopperConfig ghConfig) {
        String eleProviderStr = toLowerCase(ghConfig.getString("graph.elevation.provider", "noop"));

        if (ghConfig.has("graph.elevation.calcmean"))
            throw new IllegalArgumentException("graph.elevation.calcmean is deprecated, use graph.elevation.interpolate");

        boolean interpolate = ghConfig.has("graph.elevation.interpolate")
                ? "bilinear".equals(ghConfig.getString("graph.elevation.interpolate", "none"))
                : ghConfig.getBool("graph.elevation.calc_mean", false);

        String cacheDirStr = ghConfig.getString("graph.elevation.cache_dir", "");
        if (cacheDirStr.isEmpty())
            cacheDirStr = ghConfig.getString("graph.elevation.cachedir", "");

        String baseURL = ghConfig.getString("graph.elevation.base_url", "");
        if (baseURL.isEmpty())
            ghConfig.getString("graph.elevation.baseurl", "");

        boolean removeTempElevationFiles = ghConfig.getBool("graph.elevation.cgiar.clear", true);
        removeTempElevationFiles = ghConfig.getBool("graph.elevation.clear", removeTempElevationFiles);

        DAType elevationDAType = DAType.fromString(ghConfig.getString("graph.elevation.dataaccess", "MMAP"));
        ElevationProvider elevationProvider = ElevationProvider.NOOP;
        if (eleProviderStr.equalsIgnoreCase("srtm")) {
            elevationProvider = new SRTMProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("cgiar")) {
            elevationProvider = new CGIARProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("gmted")) {
            elevationProvider = new GMTEDProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("srtmgl1")) {
            elevationProvider = new SRTMGL1Provider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("multi")) {
            elevationProvider = new MultiSourceElevationProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("skadi")) {
            elevationProvider = new SkadiProvider(cacheDirStr);
        }

        elevationProvider.setAutoRemoveTemporaryFiles(removeTempElevationFiles);
        elevationProvider.setInterpolate(interpolate);
        if (!baseURL.isEmpty())
            elevationProvider.setBaseURL(baseURL);
        elevationProvider.setDAType(elevationDAType);
        return elevationProvider;
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
        ghStorage = new GraphHopperStorage(dir, encodingManager, hasElevation(), encodingManager.needsTurnCostsSupport(), defaultSegmentSize);

        checkProfilesConsistency();

        if (lmPreparationHandler.isEnabled())
            initLMPreparationHandler();

        List<CHConfig> chConfigs;
        if (chPreparationHandler.isEnabled()) {
            initCHPreparationHandler();
            chConfigs = chPreparationHandler.getCHConfigs();
        } else {
            chConfigs = Collections.emptyList();
        }

        ghStorage.addCHGraphs(chConfigs);

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

            postProcessing(false);
            setFullyLoaded();
            return true;
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    private void checkProfilesConsistency() {
        for (ProfileConfig profile : profilesByName.values()) {
            if (!encodingManager.hasEncoder(profile.getVehicle())) {
                throw new IllegalArgumentException("Unknown vehicle '" + profile.getVehicle() + "' in profile: " + profile + ". Make sure all vehicles used in 'profiles' exist in 'graph.flag_encoders'");
            }
            FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
            if (profile.isTurnCosts() && !encoder.supportsTurnCosts()) {
                throw new IllegalArgumentException("The profile '" + profile.getName() + "' was configured with " +
                        "'turn_costs=true', but the corresponding vehicle '" + profile.getVehicle() + "' does not support turn costs." +
                        "\nYou need to add `|turn_costs=true` to the vehicle in `graph.flag_encoders`");
            }
            try {
                createWeighting(profile, new PMap());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not create weighting for profile: '" + profile.getName() + "'.\n" +
                        "Profile: " + profile + "\n" +
                        "Error: " + e.getMessage());
            }

            if (profile instanceof CustomProfileConfig) {
                CustomModel customModel = ((CustomProfileConfig) profile).getCustomModel();
                if (customModel == null)
                    throw new IllegalArgumentException("custom model for profile '" + profile.getName() + "' was empty");
                if (!CustomWeighting.NAME.equals(profile.getWeighting()))
                    throw new IllegalArgumentException("profile '" + profile.getName() + "' has a custom model but " +
                            "weighting=" + profile.getWeighting() + " was defined");
            }
        }

        Set<String> chConfigSet = new LinkedHashSet<>(chPreparationHandler.getCHProfileConfigs().size());
        for (CHProfileConfig chConfig : chPreparationHandler.getCHProfileConfigs()) {
            boolean added = chConfigSet.add(chConfig.getProfile());
            if (!added) {
                throw new IllegalArgumentException("Duplicate CH reference to profile '" + chConfig.getProfile() + "'");
            }
            if (!profilesByName.containsKey(chConfig.getProfile())) {
                throw new IllegalArgumentException("CH profile references unknown profile '" + chConfig.getProfile() + "'");
            }
        }
        Map<String, LMProfileConfig> lmProfileMap = new LinkedHashMap<>(lmPreparationHandler.getLMProfileConfigs().size());
        for (LMProfileConfig lmConfig : lmPreparationHandler.getLMProfileConfigs()) {
            LMProfileConfig previous = lmProfileMap.put(lmConfig.getProfile(), lmConfig);
            if (previous != null) {
                throw new IllegalArgumentException("Multiple LM profiles are using the same profile '" + lmConfig.getProfile() + "'");
            }
            if (!profilesByName.containsKey(lmConfig.getProfile())) {
                throw new IllegalArgumentException("LM profile references unknown profile '" + lmConfig.getProfile() + "'");
            }
            if (lmConfig.usesOtherPreparation() && !profilesByName.containsKey(lmConfig.getPreparationProfile())) {
                throw new IllegalArgumentException("LM profile references unknown preparation profile '" + lmConfig.getPreparationProfile() + "'");
            }
        }
        for (LMProfileConfig lmConfig : lmPreparationHandler.getLMProfileConfigs()) {
            if (lmConfig.usesOtherPreparation() && !lmProfileMap.containsKey(lmConfig.getPreparationProfile())) {
                throw new IllegalArgumentException("Unknown LM preparation profile '" + lmConfig.getPreparationProfile() + "' in LM profile '" + lmConfig.getProfile() + "' cannot be used as preparation_profile");
            }
            if (lmConfig.usesOtherPreparation() && lmProfileMap.get(lmConfig.getPreparationProfile()).usesOtherPreparation()) {
                throw new IllegalArgumentException("Cannot use '" + lmConfig.getPreparationProfile() + "' as preparation_profile for LM profile '" + lmConfig.getProfile() + "', because it uses another profile for preparation itself.");
            }
        }
    }

    public RoutingAlgorithmFactory getAlgorithmFactory(String profile, boolean disableCH, boolean disableLM) {
        if (chPreparationHandler.isEnabled() && disableCH && !chPreparationHandler.isDisablingAllowed()) {
            throw new IllegalArgumentException("Disabling CH is not allowed on the server side");
        }
        if (lmPreparationHandler.isEnabled() && disableLM && !lmPreparationHandler.isDisablingAllowed()) {
            throw new IllegalArgumentException("Disabling LM is not allowed on the server side");
        }

        // for now do not allow mixing CH&LM #1082,#1889
        if (chPreparationHandler.isEnabled() && !disableCH) {
            return chPreparationHandler.getAlgorithmFactory(profile);
        } else if (lmPreparationHandler.isEnabled() && !disableLM) {
            for (LMProfileConfig lmp : lmPreparationHandler.getLMProfileConfigs()) {
                if (lmp.getProfile().equals(profile)) {
                    return lmp.usesOtherPreparation()
                            // cross-querying
                            ? lmPreparationHandler.getAlgorithmFactory(lmp.getPreparationProfile())
                            : lmPreparationHandler.getAlgorithmFactory(lmp.getProfile());
                }
            }
            throw new IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile + "'");
        } else {
            return new RoutingAlgorithmFactorySimple();
        }
    }

    public final CHPreparationHandler getCHPreparationHandler() {
        return chPreparationHandler;
    }

    private void initCHPreparationHandler() {
        if (chPreparationHandler.hasCHConfigs()) {
            return;
        }

        for (CHProfileConfig chConfig : chPreparationHandler.getCHProfileConfigs()) {
            ProfileConfig profile = profilesByName.get(chConfig.getProfile());
            if (profile.isTurnCosts()) {
                chPreparationHandler.addCHConfig(CHConfig.edgeBased(profile.getName(), createWeighting(profile, new PMap())));
            } else {
                chPreparationHandler.addCHConfig(CHConfig.nodeBased(profile.getName(), createWeighting(profile, new PMap())));
            }
        }
    }

    public final LMPreparationHandler getLMPreparationHandler() {
        return lmPreparationHandler;
    }

    private void initLMPreparationHandler() {
        if (lmPreparationHandler.hasLMProfiles())
            return;

        for (LMProfileConfig lmConfig : lmPreparationHandler.getLMProfileConfigs()) {
            if (lmConfig.usesOtherPreparation())
                continue;
            ProfileConfig profile = profilesByName.get(lmConfig.getProfile());
            // Note that we have to make sure the weighting used for LM preparation does not include turn costs, because
            // the LM preparation is running node-based and the landmark weights will be wrong if there are non-zero
            // turn costs, see discussion in #1960
            // Running the preparation without turn costs is also useful to allow e.g. changing the u_turn_costs per
            // request (we have to use the minimum weight settings (= no turn costs) for the preparation)
            Weighting weighting = createWeighting(profile, new PMap(), true);
            lmPreparationHandler.addLMProfile(new LMProfile(profile.getName(), weighting));
        }
    }

    /**
     * Does the preparation and creates the location index
     */
    public final void postProcessing() {
        postProcessing(false);
    }

    /**
     * Does the preparation and creates the location index
     *
     * @param closeEarly release resources as early as possible
     */
    protected void postProcessing(boolean closeEarly) {
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

        importPublicTransit();

        if (lmPreparationHandler.isEnabled())
            lmPreparationHandler.createPreparations(ghStorage, locationIndex);
        loadOrPrepareLM(closeEarly);

        if (chPreparationHandler.isEnabled())
            chPreparationHandler.createPreparations(ghStorage);
        if (!isCHPrepared())
            prepareCH(closeEarly);
    }

    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
    }

    protected void importPublicTransit() {
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

    final public Weighting createWeighting(ProfileConfig profileConfig, PMap hints) {
        return createWeighting(profileConfig, hints, false);
    }

    /**
     * @param profileConfig    The profile for which the weighting shall be created
     * @param hints            Additional hints that can be used to further specify the weighting that shall be created
     * @param disableTurnCosts Can be used to explicitly create the weighting without turn costs. This is sometimes needed when the
     *                         weighting shall be used by some algorithm that can only be run with node-based graph traversal, like
     *                         LM preparation or Isochrones
     */
    public Weighting createWeighting(ProfileConfig profileConfig, PMap hints, boolean disableTurnCosts) {
        return new DefaultWeightingFactory(ghStorage, encodingManager).createWeighting(profileConfig, hints, disableTurnCosts);
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

        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        try {
            validateRequest(request);
            PMap hints = request.getHints();
            final boolean disableCH = getDisableCH(request.getHints());
            final boolean disableLM = getDisableLM(request.getHints());
            String algoStr = request.getAlgorithm();
            if (algoStr.isEmpty())
                algoStr = chPreparationHandler.isEnabled() && !disableCH ? DIJKSTRA_BI : ASTAR_BI;

            ProfileConfig profile = profilesByName.get(request.getProfile());
            if (profile == null) {
                throw new IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + profilesByName.keySet());
            }
            if (!profile.isTurnCosts() && !request.getCurbsides().isEmpty()) {
                List<String> turnCostProfiles = new ArrayList<>();
                for (ProfileConfig p : profilesByName.values()) {
                    if (p.isTurnCosts()) {
                        turnCostProfiles.add(p.getName());
                    }
                }
                throw new IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to use a profile that supports turn costs" +
                        "\nThe following profiles do support turn costs: " + turnCostProfiles);
            }

            // todo later: should we be able to control this using the edge_based parameter?
            TraversalMode tMode = profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            List<GHPoint> points = request.getPoints();
            RoutingAlgorithmFactory algorithmFactory = getAlgorithmFactory(profile.getName(), disableCH, disableLM);
            Weighting weighting;
            Graph graph = ghStorage;
            if (chPreparationHandler.isEnabled() && !disableCH) {
                if (!(algorithmFactory instanceof CHRoutingAlgorithmFactory))
                    throw new IllegalStateException("Although CH was enabled a non-CH algorithm factory was returned " + algorithmFactory);

                if (hints.has(Routing.BLOCK_AREA))
                    throw new IllegalArgumentException("When CH is enabled the " + Parameters.Routing.BLOCK_AREA + " cannot be specified");

                CHConfig chConfig = ((CHRoutingAlgorithmFactory) algorithmFactory).getCHConfig();
                weighting = chConfig.getWeighting();
                graph = ghStorage.getCHGraph(chConfig);
            } else {
                checkNonChMaxWaypointDistance(points);
                final int uTurnCostsInt = hints.getInt(Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
                if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !tMode.isEdgeBased()) {
                    throw new IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, use `" + Routing.EDGE_BASED + "=true'");
                }
                FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
                weighting = createWeighting(profile, hints);
                if (hints.has(Routing.BLOCK_AREA))
                    weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(ghStorage, locationIndex,
                            points, hints, DefaultEdgeFilter.allEdges(encoder)));
            }
            ghRsp.addDebugInfo("tmode:" + tMode.toString());

            RoutingTemplate routingTemplate = createRoutingTemplate(request, ghRsp, algoStr, weighting);

            StopWatch sw = new StopWatch().start();
            List<QueryResult> qResults = routingTemplate.lookup(points);
            ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s");
            if (ghRsp.hasErrors())
                return Collections.emptyList();

            QueryGraph queryGraph = QueryGraph.lookup(graph, qResults);
            int maxVisitedNodesForRequest = hints.getInt(Routing.MAX_VISITED_NODES, routingConfig.getMaxVisitedNodes());
            if (maxVisitedNodesForRequest > routingConfig.getMaxVisitedNodes())
                throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routingConfig.getMaxVisitedNodes());

            AlgorithmOptions algoOpts = AlgorithmOptions.start().
                    algorithm(algoStr).
                    traversalMode(tMode).
                    weighting(weighting).
                    maxVisitedNodes(maxVisitedNodesForRequest).
                    hints(hints).
                    build();

            // do the actual route calculation !
            List<Path> altPaths = routingTemplate.calcPaths(queryGraph, algorithmFactory, algoOpts);

            boolean tmpEnableInstructions = hints.getBool(Routing.INSTRUCTIONS, encodingManager.isEnableInstructions());
            boolean tmpCalcPoints = hints.getBool(Routing.CALC_POINTS, routingConfig.isCalcPoints());
            double wayPointMaxDistance = hints.getDouble(Routing.WAY_POINT_MAX_DISTANCE, 1d);

            DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(wayPointMaxDistance);
            PathMerger pathMerger = new PathMerger(queryGraph.getBaseGraph(), weighting).
                    setCalcPoints(tmpCalcPoints).
                    setDouglasPeucker(peucker).
                    setEnableInstructions(tmpEnableInstructions).
                    setPathDetailsBuilders(pathBuilderFactory, request.getPathDetails()).
                    setSimplifyResponse(routingConfig.isSimplifyResponse() && wayPointMaxDistance > 0);

            if (!request.getHeadings().isEmpty())
                pathMerger.setFavoredHeading(request.getHeadings().get(0));

            routingTemplate.finish(pathMerger, trMap.getWithFallBack(request.getLocale()));
            return altPaths;
        } catch (IllegalArgumentException ex) {
            ghRsp.addError(ex);
            return Collections.emptyList();
        }
    }

    protected void validateRequest(GHRequest request) {
        if (Helper.isEmpty(request.getProfile()))
            throw new IllegalArgumentException("You need to specify a profile to perform a routing request, see core/docs/profiles.md");

        if (request.getHints().has("vehicle"))
            throw new IllegalArgumentException("GHRequest may no longer contain a vehicle, use the profile parameter instead, see core/docs/profiles.md");
        if (request.getHints().has("weighting"))
            throw new IllegalArgumentException("GHRequest may no longer contain a weighting, use the profile parameter instead, see core/docs/profiles.md");
        if (request.getHints().has(Routing.TURN_COSTS))
            throw new IllegalArgumentException("GHRequest may no longer contain the turn_costs=true/false parameter, use the profile parameter instead, see core/docs/profiles.md");
        if (request.getHints().has(Routing.EDGE_BASED))
            throw new IllegalArgumentException("GHRequest may no longer contain the edge_based=true/false parameter, use the profile parameter instead, see core/docs/profiles.md");

        if (request.getPoints().isEmpty())
            throw new IllegalArgumentException("You have to pass at least one point");
        checkIfPointsAreInBounds(request.getPoints());

        if (request.getHeadings().size() > 1 && request.getHeadings().size() != request.getPoints().size())
            throw new IllegalArgumentException("The number of 'heading' parameters must be zero, one "
                    + "or equal to the number of points (" + request.getPoints().size() + ")");
        for (int i = 0; i < request.getHeadings().size(); i++)
            if (!GHRequest.isAzimuthValue(request.getHeadings().get(i)))
                throw new IllegalArgumentException("Heading for point " + i + " must be in range [0,360) or NaN, but was: " + request.getHeadings().get(i));

        if (request.getPointHints().size() > 0 && request.getPointHints().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass exactly one hint for every point, empty hints will be ignored");
        if (request.getCurbsides().size() > 0 && request.getCurbsides().size() != request.getPoints().size())
            throw new IllegalArgumentException("If you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");

        boolean disableCH = getDisableCH(request.getHints());
        if (chPreparationHandler.isEnabled() && !chPreparationHandler.isDisablingAllowed() && disableCH)
            throw new IllegalArgumentException("Disabling CH not allowed on the server-side");

        boolean disableLM = getDisableLM(request.getHints());
        if (lmPreparationHandler.isEnabled() && !lmPreparationHandler.isDisablingAllowed() && disableLM)
            throw new IllegalArgumentException("Disabling LM not allowed on the server-side");

        // todonow: do not allow things like short_fastest.distance_factor or u_turn_costs unless CH is disabled and only under certain conditions for LM
        if (chPreparationHandler.isEnabled() && !disableCH) {
            if (!request.getHeadings().isEmpty())
                throw new IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483");

            if (request.getHints().getBool(Routing.PASS_THROUGH, false))
                throw new IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765");
        }
    }

    private boolean getDisableLM(PMap hints) {
        return hints.getBool(Landmark.DISABLE, false);
    }

    private boolean getDisableCH(PMap hints) {
        return hints.getBool(CH.DISABLE, false);
    }

    protected RoutingTemplate createRoutingTemplate(GHRequest request, GHResponse ghRsp, String algoStr, Weighting weighting) {
        RoutingTemplate routingTemplate;
        if (ROUND_TRIP.equalsIgnoreCase(algoStr))
            routingTemplate = new RoundTripRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting, routingConfig.getMaxRoundTripRetries());
        else if (ALT_ROUTE.equalsIgnoreCase(algoStr))
            routingTemplate = new AlternativeRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        else
            routingTemplate = new ViaRoutingTemplate(request, ghRsp, locationIndex, encodingManager, weighting);
        return routingTemplate;
    }

    private void checkIfPointsAreInBounds(List<GHPoint> points) {
        BBox bounds = ghStorage.getBounds();
        for (int i = 0; i < points.size(); i++) {
            GHPoint point = points.get(i);
            if (!bounds.contains(point.getLat(), point.getLon())) {
                throw new PointOutOfBoundsException("Point " + i + " is out of bounds: " + point, i);
            }
        }
    }

    private void checkNonChMaxWaypointDistance(List<GHPoint> points) {
        if (routingConfig.getNonChMaxWaypointDistance() == Integer.MAX_VALUE) {
            return;
        }
        GHPoint lastPoint = points.get(0);
        GHPoint point;
        double dist;
        DistanceCalc calc = DIST_EARTH;
        for (int i = 1; i < points.size(); i++) {
            point = points.get(i);
            dist = calc.calcDist(lastPoint.getLat(), lastPoint.getLon(), point.getLat(), point.getLon());
            if (dist > routingConfig.getNonChMaxWaypointDistance()) {
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
        boolean tmpPrepare = chPreparationHandler.isEnabled();
        if (tmpPrepare) {
            ensureWriteAccess();

            if (closeEarly) {
                locationIndex.flush();
                locationIndex.close();
                ghStorage.flushAndCloseEarly();
            }

            ghStorage.freeze();
            chPreparationHandler.prepare(ghStorage.getProperties(), closeEarly);
            ghStorage.getProperties().put(CH.PREPARE + "done", true);
        }
    }

    /**
     * For landmarks it is required to always call this method: either it creates the landmark data or it loads it.
     */
    protected void loadOrPrepareLM(boolean closeEarly) {
        boolean tmpPrepare = lmPreparationHandler.isEnabled() && !lmPreparationHandler.getPreparations().isEmpty();
        if (tmpPrepare) {
            ensureWriteAccess();
            ghStorage.freeze();
            if (lmPreparationHandler.loadOrDoWork(ghStorage.getProperties(), closeEarly))
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
        logger.info("edges: " + Helper.nf(ghStorage.getEdges()) + ", nodes " + Helper.nf(currNodeCount)
                + ", there were " + Helper.nf(preparation.getMaxSubnetworks())
                + " subnetworks. removed them => " + Helper.nf(prevNodeCount - currNodeCount)
                + " less nodes");
    }

    protected void flush() {
        logger.info("flushing graph " + ghStorage.toString() + ", details:" + ghStorage.toDetailsString() + ", "
                + getMemInfo() + ")");
        ghStorage.flush();
        logger.info("flushed graph " + getMemInfo() + ")");
        setFullyLoaded();
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
        routingConfig.setNonChMaxWaypointDistance(nonChMaxWaypointDistance);
    }

    private void setFullyLoaded() {
        fullyLoaded = true;
    }

    private static class DefaultWeightingFactory {
        private final GraphHopperStorage ghStorage;
        private final EncodingManager encodingManager;

        public DefaultWeightingFactory(GraphHopperStorage ghStorage, EncodingManager encodingManager) {
            this.ghStorage = ghStorage;
            this.encodingManager = encodingManager;
        }

        public Weighting createWeighting(ProfileConfig profile, PMap requestHints, boolean disableTurnCosts) {
            // Merge profile hints with request hints, the request hints take precedence.
            // Note that so far we do not check if overwriting the profile hints actually works with the preparation
            // for LM/CH. Later we should also limit the number of parameters that can be used to modify the profile.
            // todo: since we are not dealing with block_area here yet we cannot really apply any merging rules
            // for it, see discussion here: https://github.com/graphhopper/graphhopper/pull/1958#discussion_r395462901
            PMap hints = new PMap();
            hints.putAll(profile.getHints());
            hints.putAll(requestHints);

            FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
            TurnCostProvider turnCostProvider;
            if (profile.isTurnCosts() && !disableTurnCosts) {
                if (!encoder.supportsTurnCosts())
                    throw new IllegalArgumentException("Encoder " + encoder + " does not support turn costs");
                int uTurnCosts = hints.getInt(Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
                turnCostProvider = new DefaultTurnCostProvider(encoder, ghStorage.getTurnCostStorage(), uTurnCosts);
            } else {
                turnCostProvider = NO_TURN_COST_PROVIDER;
            }

            String weightingStr = toLowerCase(profile.getWeighting());
            if (weightingStr.isEmpty())
                throw new IllegalArgumentException("You have to specify a weighting");

            Weighting weighting = null;
            if (CustomWeighting.NAME.equalsIgnoreCase(weightingStr)) {
                if (!(profile instanceof CustomProfileConfig))
                    throw new IllegalArgumentException("custom weighting requires a CustomProfileConfig but was profile=" + profile.getName());
                CustomModel queryCustomModel = requestHints.getObject(CustomModel.KEY, null);
                CustomProfileConfig customProfileConfig = (CustomProfileConfig) profile;
                queryCustomModel = queryCustomModel == null ?
                        customProfileConfig.getCustomModel() : CustomModel.merge(customProfileConfig.getCustomModel(), queryCustomModel);
                weighting = new CustomWeighting(encoder, encodingManager, turnCostProvider, queryCustomModel);
            } else if ("shortest".equalsIgnoreCase(weightingStr)) {
                weighting = new ShortestWeighting(encoder, turnCostProvider);
            } else if ("fastest".equalsIgnoreCase(weightingStr)) {
                if (encoder.supports(PriorityWeighting.class))
                    weighting = new PriorityWeighting(encoder, hints, turnCostProvider);
                else
                    weighting = new FastestWeighting(encoder, hints, turnCostProvider);
            } else if ("curvature".equalsIgnoreCase(weightingStr)) {
                if (encoder.supports(CurvatureWeighting.class))
                    weighting = new CurvatureWeighting(encoder, hints, turnCostProvider);

            } else if ("short_fastest".equalsIgnoreCase(weightingStr)) {
                weighting = new ShortFastestWeighting(encoder, hints, turnCostProvider);
            }

            if (weighting == null)
                throw new IllegalArgumentException("Weighting '" + weightingStr + "' not supported");

            return weighting;
        }
    }

    private static class RoutingConfig {
        private int maxVisitedNodes = Integer.MAX_VALUE;
        private int maxRoundTripRetries = 3;
        private int nonChMaxWaypointDistance = Integer.MAX_VALUE;
        private boolean calcPoints = true;
        private boolean simplifyResponse = true;

        public int getMaxVisitedNodes() {
            return maxVisitedNodes;
        }

        public void setMaxVisitedNodes(int maxVisitedNodes) {
            this.maxVisitedNodes = maxVisitedNodes;
        }

        public int getMaxRoundTripRetries() {
            return maxRoundTripRetries;
        }

        public void setMaxRoundTripRetries(int maxRoundTripRetries) {
            this.maxRoundTripRetries = maxRoundTripRetries;
        }

        public int getNonChMaxWaypointDistance() {
            return nonChMaxWaypointDistance;
        }

        public void setNonChMaxWaypointDistance(int nonChMaxWaypointDistance) {
            this.nonChMaxWaypointDistance = nonChMaxWaypointDistance;
        }

        public boolean isCalcPoints() {
            return calcPoints;
        }

        public void setCalcPoints(boolean calcPoints) {
            this.calcPoints = calcPoints;
        }

        public boolean isSimplifyResponse() {
            return simplifyResponse;
        }

        public void setSimplifyResponse(boolean simplifyResponse) {
            this.simplifyResponse = simplifyResponse;
        }
    }
}