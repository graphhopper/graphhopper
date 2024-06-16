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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.*;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.LMPreparationHandler;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks.PrepareJob;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.GHUtility.readCountries;
import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.RoundTrip;

/**
 * Easy to use access point to configure import and (offline) routing.
 *
 * @author Peter Karich
 */
public class GraphHopper {
    private static final Logger logger = LoggerFactory.getLogger(GraphHopper.class);
    private final Map<String, Profile> profilesByName = new LinkedHashMap<>();
    private final String fileLockName = "gh.lock";
    // utils
    private final TranslationMap trMap = new TranslationMap().doImport();
    boolean removeZipped = true;
    // for country rules:
    private CountryRuleFactory countryRuleFactory = null;
    // for custom areas:
    private String customAreasDirectory = "";
    // for graph:
    private GraphHopperStorage ghStorage;
    private final TagParserManager.Builder emBuilder = new TagParserManager.Builder();
    private TagParserManager tagParserManager;
    private int defaultSegmentSize = -1;
    private String ghLocation = "";
    private DAType dataAccessDefaultType = DAType.RAM_STORE;
    private final LinkedHashMap<String, String> dataAccessConfig = new LinkedHashMap<>();
    private boolean sortGraph = false;
    private boolean elevation = false;
    private LockFactory lockFactory = new NativeFSLockFactory();
    private boolean allowWrites = true;
    private boolean fullyLoaded = false;
    private final OSMReaderConfig osmReaderConfig = new OSMReaderConfig();
    // for routing
    private final RouterConfig routerConfig = new RouterConfig();
    // for index
    private LocationIndex locationIndex;
    private int preciseIndexResolution = 300;
    private int maxRegionSearch = 4;
    // for prepare
    private int minNetworkSize = 200;

    // preparation handlers
    private final LMPreparationHandler lmPreparationHandler = new LMPreparationHandler();
    private final CHPreparationHandler chPreparationHandler = new CHPreparationHandler();
    private Map<String, RoutingCHGraph> chGraphs = Collections.emptyMap();
    private Map<String, LandmarkStorage> landmarks = Collections.emptyMap();

    // for data reader
    private String osmFile;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private FlagEncoderFactory flagEncoderFactory = new DefaultFlagEncoderFactory();
    private EncodedValueFactory encodedValueFactory = new DefaultEncodedValueFactory();
    private TagParserFactory tagParserFactory = new DefaultTagParserFactory();
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();

    public TagParserManager.Builder getTagParserManagerBuilder() {
        return emBuilder;
    }

    public TagParserManager getTagParserManager() {
        if (tagParserManager == null)
            throw new IllegalStateException("EncodingManager not yet built");
        return tagParserManager;
    }

    public EncodingManager getEncodingManager() {
        return getTagParserManager().getEncodingManager();
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

    public GraphHopper setPathDetailsBuilderFactory(PathDetailsBuilderFactory pathBuilderFactory) {
        this.pathBuilderFactory = pathBuilderFactory;
        return this;
    }

    public PathDetailsBuilderFactory getPathDetailsBuilderFactory() {
        return pathBuilderFactory;
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

    public GraphHopper setMinNetworkSize(int minNetworkSize) {
        ensureNotLoaded();
        this.minNetworkSize = minNetworkSize;
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
            dataAccessDefaultType = DAType.RAM_STORE;
        else
            dataAccessDefaultType = DAType.RAM;
        return this;
    }

    /**
     * Sets the routing profiles that shall be supported by this GraphHopper instance. The (and only the) given profiles
     * can be used for routing without preparation and for CH/LM preparation.
     * <p>
     * Here is an example how to setup two CH profiles and one LM profile (via the Java API)
     *
     * <pre>
     * {@code
     *   hopper.setProfiles(
     *     new Profile("my_car").setVehicle("car").setWeighting("shortest"),
     *     new Profile("your_bike").setVehicle("bike").setWeighting("fastest")
     *   );
     *   hopper.getCHPreparationHandler().setCHProfiles(
     *     new CHProfile("my_car"),
     *     new CHProfile("your_bike")
     *   );
     *   hopper.getLMPreparationHandler().setLMProfiles(
     *     new LMProfile("your_bike")
     *   );
     * }
     * </pre>
     * <p>
     * See also https://github.com/graphhopper/graphhopper/pull/1922.
     *
     * @see CHPreparationHandler#setCHProfiles
     * @see LMPreparationHandler#setLMProfiles
     */
    public GraphHopper setProfiles(Profile... profiles) {
        return setProfiles(Arrays.asList(profiles));
    }

    public GraphHopper setProfiles(List<Profile> profiles) {
        if (!profilesByName.isEmpty())
            throw new IllegalArgumentException("Cannot initialize profiles multiple times");
        if (tagParserManager != null)
            throw new IllegalArgumentException("Cannot set profiles after EncodingManager was built");
        for (Profile profile : profiles) {
            Profile previous = this.profilesByName.put(profile.getName(), profile);
            if (previous != null)
                throw new IllegalArgumentException("Profile names must be unique. Duplicate name: '" + profile.getName() + "'");
        }
        return this;
    }

    public List<Profile> getProfiles() {
        return new ArrayList<>(profilesByName.values());
    }

    /**
     * Returns the profile for the given profile name, or null if it does not exist
     */
    public Profile getProfile(String profileName) {
        return profilesByName.get(profileName);
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

    public String getOSMFile() {
        return osmFile;
    }

    /**
     * This file can be an osm xml (.osm), a compressed xml (.osm.zip or .osm.gz) or a protobuf file
     * (.pbf).
     */
    public GraphHopper setOSMFile(String osmFile) {
        ensureNotLoaded();
        if (isEmpty(osmFile))
            throw new IllegalArgumentException("OSM file cannot be empty.");

        this.osmFile = osmFile;
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
     * @return a mapping between profile names and according CH preparations. The map will be empty before loading
     * or import.
     */
    public Map<String, RoutingCHGraph> getCHGraphs() {
        return chGraphs;
    }

    /**
     * @return a mapping between profile names and according landmark preparations. The map will be empty before loading
     * or import.
     */
    public Map<String, LandmarkStorage> getLandmarks() {
        return landmarks;
    }

    /**
     * The location index created from the graph.
     *
     * @throws IllegalStateException if index is not initialized
     */
    public LocationIndex getLocationIndex() {
        if (locationIndex == null)
            throw new IllegalStateException("LocationIndex not initialized");

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

    public GraphHopper setCustomAreasDirectory(String customAreasDirectory) {
        this.customAreasDirectory = customAreasDirectory;
        return this;
    }

    public String getCustomAreasDirectory() {
        return this.customAreasDirectory;
    }

    /**
     * Sets the factory used to create country rules. Use `null` to disable country rules
     */
    public GraphHopper setCountryRuleFactory(CountryRuleFactory countryRuleFactory) {
        this.countryRuleFactory = countryRuleFactory;
        return this;
    }

    public CountryRuleFactory getCountryRuleFactory() {
        return this.countryRuleFactory;
    }

    /**
     * Reads the configuration from a {@link GraphHopperConfig} object which can be manually filled, or more typically
     * is read from `config.yml`.
     */
    public GraphHopper init(GraphHopperConfig ghConfig) {
        ensureNotLoaded();
        // disabling_allowed config options were removed for GH 3.0
        if (ghConfig.has("routing.ch.disabling_allowed"))
            throw new IllegalArgumentException("The 'routing.ch.disabling_allowed' configuration option is no longer supported");
        if (ghConfig.has("routing.lm.disabling_allowed"))
            throw new IllegalArgumentException("The 'routing.lm.disabling_allowed' configuration option is no longer supported");
        if (ghConfig.has("osmreader.osm"))
            throw new IllegalArgumentException("Instead of osmreader.osm use datareader.file, for other changes see CHANGELOG.md");

        String tmpOsmFile = ghConfig.getString("datareader.file", "");
        if (!isEmpty(tmpOsmFile))
            osmFile = tmpOsmFile;

        String graphHopperFolder = ghConfig.getString("graph.location", "");
        if (isEmpty(graphHopperFolder) && isEmpty(ghLocation)) {
            if (isEmpty(osmFile))
                throw new IllegalArgumentException("If no graph.location is provided you need to specify an OSM file.");

            graphHopperFolder = pruneFileEnd(osmFile) + "-gh";
        }
        ghLocation = graphHopperFolder;

        countryRuleFactory = ghConfig.getBool("country_rules.enabled", false) ? new CountryRuleFactory() : null;
        customAreasDirectory = ghConfig.getString("custom_areas.directory", customAreasDirectory);

        defaultSegmentSize = ghConfig.getInt("graph.dataaccess.segment_size", defaultSegmentSize);

        String daTypeString = ghConfig.getString("graph.dataaccess.default_type", ghConfig.getString("graph.dataaccess", "RAM_STORE"));
        dataAccessDefaultType = DAType.fromString(daTypeString);
        for (Map.Entry<String, Object> entry : ghConfig.asPMap().toMap().entrySet()) {
            if (entry.getKey().startsWith("graph.dataaccess.type."))
                dataAccessConfig.put(entry.getKey().substring("graph.dataaccess.type.".length()), entry.getValue().toString());
            if (entry.getKey().startsWith("graph.dataaccess.mmap.preload."))
                dataAccessConfig.put(entry.getKey().substring("graph.dataaccess.mmap.".length()), entry.getValue().toString());
        }

        sortGraph = ghConfig.getBool("graph.do_sort", sortGraph);
        removeZipped = ghConfig.getBool("graph.remove_zipped", removeZipped);

        if (!ghConfig.getString("spatial_rules.location", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.location has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.");
        if (!ghConfig.getString("spatial_rules.borders_directory", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.borders_directory has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.");
        // todo: maybe introduce custom_areas.max_bbox if this is needed later
        if (!ghConfig.getString("spatial_rules.max_bbox", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.max_bbox has been deprecated. There is no replacement, all custom areas will be considered.");

        if (tagParserManager != null)
            throw new IllegalStateException("Cannot call init twice. EncodingManager was already initialized.");
        emBuilder.setDateRangeParser(DateRangeParser.createInstance(ghConfig.getString("datareader.date_range_parser_day", "")));
        setProfiles(ghConfig.getProfiles());
        tagParserManager = buildEncodingManager(ghConfig);

        if (ghConfig.getString("graph.locktype", "native").equals("simple"))
            lockFactory = new SimpleFSLockFactory();
        else
            lockFactory = new NativeFSLockFactory();

        // elevation
        osmReaderConfig.setSmoothElevation(ghConfig.getBool("graph.elevation.smoothing", osmReaderConfig.isSmoothElevation()));
        osmReaderConfig.setLongEdgeSamplingDistance(ghConfig.getDouble("graph.elevation.long_edge_sampling_distance", osmReaderConfig.getLongEdgeSamplingDistance()));
        osmReaderConfig.setElevationMaxWayPointDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", osmReaderConfig.getElevationMaxWayPointDistance()));
        routerConfig.setElevationWayPointMaxDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", routerConfig.getElevationWayPointMaxDistance()));
        ElevationProvider elevationProvider = createElevationProvider(ghConfig);
        setElevationProvider(elevationProvider);

        if (osmReaderConfig.getLongEdgeSamplingDistance() < Double.MAX_VALUE && !elevationProvider.canInterpolate())
            logger.warn("Long edge sampling enabled, but bilinear interpolation disabled. See #1953");

        // optimizable prepare
        minNetworkSize = ghConfig.getInt("prepare.min_network_size", minNetworkSize);

        // prepare CH&LM
        chPreparationHandler.init(ghConfig);
        lmPreparationHandler.init(ghConfig);

        // osm import
        osmReaderConfig.setParseWayNames(ghConfig.getBool("datareader.instructions", osmReaderConfig.isParseWayNames()));
        osmReaderConfig.setPreferredLanguage(ghConfig.getString("datareader.preferred_language", osmReaderConfig.getPreferredLanguage()));
        osmReaderConfig.setMaxWayPointDistance(ghConfig.getDouble(Routing.INIT_WAY_POINT_MAX_DISTANCE, osmReaderConfig.getMaxWayPointDistance()));
        osmReaderConfig.setWorkerThreads(ghConfig.getInt("datareader.worker_threads", osmReaderConfig.getWorkerThreads()));

        // index
        preciseIndexResolution = ghConfig.getInt("index.high_resolution", preciseIndexResolution);
        maxRegionSearch = ghConfig.getInt("index.max_region_search", maxRegionSearch);

        // routing
        routerConfig.setMaxVisitedNodes(ghConfig.getInt(Routing.INIT_MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes()));
        routerConfig.setMaxRoundTripRetries(ghConfig.getInt(RoundTrip.INIT_MAX_RETRIES, routerConfig.getMaxRoundTripRetries()));
        routerConfig.setNonChMaxWaypointDistance(ghConfig.getInt(Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE, routerConfig.getNonChMaxWaypointDistance()));
        routerConfig.setInstructionsEnabled(ghConfig.getBool(Routing.INIT_INSTRUCTIONS, routerConfig.isInstructionsEnabled()));
        int activeLandmarkCount = ghConfig.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.min(8, lmPreparationHandler.getLandmarks()));
        if (activeLandmarkCount > lmPreparationHandler.getLandmarks())
            throw new IllegalArgumentException("Default value for active landmarks " + activeLandmarkCount
                    + " should be less or equal to landmark count of " + lmPreparationHandler.getLandmarks());
        routerConfig.setActiveLandmarkCount(activeLandmarkCount);

        return this;
    }

    private TagParserManager buildEncodingManager(GraphHopperConfig ghConfig) {
        if (profilesByName.isEmpty())
            throw new IllegalStateException("no profiles exist but assumed to create EncodingManager. E.g. provide them in GraphHopperConfig when calling GraphHopper.init");

        String flagEncodersStr = ghConfig.getString("graph.flag_encoders", "");
        Map<String, String> flagEncoderMap = new LinkedHashMap<>();
        for (String encoderStr : flagEncodersStr.split(",")) {
            String key = encoderStr.split("\\|")[0];
            if (!key.isEmpty()) {
                if (flagEncoderMap.containsKey(key))
                    throw new IllegalArgumentException("FlagEncoder " + key + " needs to be unique");
                flagEncoderMap.put(key, encoderStr);
            }
        }
        Map<String, String> implicitFlagEncoderMap = new HashMap<>();
        for (Profile profile : profilesByName.values()) {
            emBuilder.add(Subnetwork.create(profile.getName()));
            if (!flagEncoderMap.containsKey(profile.getVehicle())
                    // overwrite key in implicit map if turn cost support required
                    && (!implicitFlagEncoderMap.containsKey(profile.getVehicle()) || profile.isTurnCosts()))
                implicitFlagEncoderMap.put(profile.getVehicle(), profile.getVehicle() + (profile.isTurnCosts() ? "|turn_costs=true" : ""));
        }
        flagEncoderMap.putAll(implicitFlagEncoderMap);
        flagEncoderMap.values().forEach(s -> emBuilder.addIfAbsent(flagEncoderFactory, s));

        String encodedValueStr = ghConfig.getString("graph.encoded_values", "");
        for (String tpStr : encodedValueStr.split(",")) {
            if (!tpStr.isEmpty()) emBuilder.addIfAbsent(tagParserFactory, tpStr);
        }

        return emBuilder.build();
    }

    private static ElevationProvider createElevationProvider(GraphHopperConfig ghConfig) {
        String eleProviderStr = toLowerCase(ghConfig.getString("graph.elevation.provider", "noop"));

        if (ghConfig.has("graph.elevation.calcmean"))
            throw new IllegalArgumentException("graph.elevation.calcmean is deprecated, use graph.elevation.interpolate");

        String cacheDirStr = ghConfig.getString("graph.elevation.cache_dir", "");
        if (cacheDirStr.isEmpty() && ghConfig.has("graph.elevation.cachedir"))
            throw new IllegalArgumentException("use graph.elevation.cache_dir not cachedir in configuration");

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
        } else if (eleProviderStr.equalsIgnoreCase("usgs")) {
            elevationProvider = new USGSProvider(cacheDirStr);
        }

        if (elevationProvider instanceof TileBasedElevationProvider) {
            TileBasedElevationProvider provider = (TileBasedElevationProvider) elevationProvider;

            String baseURL = ghConfig.getString("graph.elevation.base_url", "");
            if (baseURL.isEmpty() && ghConfig.has("graph.elevation.baseurl"))
                throw new IllegalArgumentException("use graph.elevation.base_url not baseurl in configuration");

            DAType elevationDAType = DAType.fromString(ghConfig.getString("graph.elevation.dataaccess", "MMAP"));

            boolean interpolate = ghConfig.has("graph.elevation.interpolate")
                    ? "bilinear".equals(ghConfig.getString("graph.elevation.interpolate", "none"))
                    : ghConfig.getBool("graph.elevation.calc_mean", false);

            boolean removeTempElevationFiles = ghConfig.getBool("graph.elevation.cgiar.clear", true);
            removeTempElevationFiles = ghConfig.getBool("graph.elevation.clear", removeTempElevationFiles);

            provider
                    .setAutoRemoveTemporaryFiles(removeTempElevationFiles)
                    .setInterpolate(interpolate)
                    .setDAType(elevationDAType);
            if (!baseURL.isEmpty())
                provider.setBaseURL(baseURL);
        }
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
        if (!load()) {
            printInfo();
            process(false);
        } else {
            printInfo();
        }
        return this;
    }

    /**
     * Imports and processes data, storing it to disk when complete.
     */
    public void importAndClose() {
        if (!load()) {
            printInfo();
            process(true);
        } else {
            printInfo();
            logger.info("Graph already imported into " + ghLocation);
        }
        close();
    }

    /**
     * Creates the graph from OSM data.
     */
    private void process(boolean closeEarly) {
        GHLock lock = null;
        try {
            if (ghStorage == null)
                throw new IllegalStateException("GraphHopperStorage must be initialized before starting the import");
            if (ghStorage.getDirectory().getDefaultType().isStoring()) {
                lockFactory.setLockDir(new File(ghLocation));
                lock = lockFactory.create(fileLockName, true);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid multiple writers we need to obtain a write lock but it failed. In " + ghLocation, lock.getObtainFailedReason());
            }
            ensureWriteAccess();
            importOSM();
            cleanUp();
            postImport();
            postProcessing(closeEarly);
            flush();
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    protected void postImport() {
        if (sortGraph) {
            GraphHopperStorage newGraph = GHUtility.newStorage(ghStorage);
            GHUtility.sortDFS(ghStorage, newGraph);
            logger.info("graph sorted (" + getMemInfo() + ")");
            ghStorage = newGraph;
        }

        if (hasElevation())
            interpolateBridgesTunnelsAndFerries();
    }

    protected void importOSM() {
        if (osmFile == null)
            throw new IllegalStateException("Couldn't load from existing folder: " + ghLocation
                    + " but also cannot use file for DataReader as it wasn't specified!");

        List<CustomArea> customAreas = readCountries();
        if (isEmpty(customAreasDirectory)) {
            logger.info("No custom areas are used, custom_areas.directory not given");
        } else {
            logger.info("Creating custom area index, reading custom areas from: '" + customAreasDirectory + "'");
            customAreas.addAll(readCustomAreas());
        }
        AreaIndex<CustomArea> areaIndex = new AreaIndex<>(customAreas);

        logger.info("start creating graph from " + osmFile);
        OSMReader reader = new OSMReader(ghStorage.getBaseGraph(), tagParserManager, osmReaderConfig).setFile(_getOSMFile()).
                setAreaIndex(areaIndex).
                setElevationProvider(eleProvider).
                setCountryRuleFactory(countryRuleFactory);
        logger.info("using " + ghStorage.toString() + ", memory:" + getMemInfo());
        ghStorage.create(100);
        try {
            reader.readGraph();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read file " + getOSMFile(), ex);
        }
        DateFormat f = createFormatter();
        ghStorage.getProperties().put("datareader.import.date", f.format(new Date()));
        if (reader.getDataDate() != null)
            ghStorage.getProperties().put("datareader.data.date", f.format(reader.getDataDate()));
    }

    private List<CustomArea> readCustomAreas() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        final Path bordersDirectory = Paths.get(customAreasDirectory);
        List<JsonFeatureCollection> jsonFeatureCollections = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bordersDirectory, "*.{geojson,json}")) {
            for (Path borderFile : stream) {
                try (BufferedReader reader = Files.newBufferedReader(borderFile, StandardCharsets.UTF_8)) {
                    JsonFeatureCollection jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
                    jsonFeatureCollections.add(jsonFeatureCollection);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return jsonFeatureCollections.stream().flatMap(j -> j.getFeatures().stream())
                .map(CustomArea::fromJsonFeature)
                .collect(Collectors.toList());
    }

    /**
     * Currently we use this for a few tests where the dataReaderFile is loaded from the classpath
     */
    protected File _getOSMFile() {
        return new File(osmFile);
    }

    /**
     * Load from existing graph folder.
     */
    public boolean load() {
        if (isEmpty(ghLocation))
            throw new IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before");

        if (fullyLoaded)
            throw new IllegalStateException("graph is already successfully loaded");

        File tmpFileOrFolder = new File(ghLocation);
        if (!tmpFileOrFolder.isDirectory() && tmpFileOrFolder.exists()) {
            throw new IllegalArgumentException("GraphHopperLocation cannot be an existing file. Has to be either non-existing or a folder.");
        } else {
            File compressed = new File(ghLocation + ".ghz");
            if (compressed.exists() && !compressed.isDirectory()) {
                try {
                    new Unzipper().unzip(compressed.getAbsolutePath(), ghLocation, removeZipped);
                } catch (IOException ex) {
                    throw new RuntimeException("Couldn't extract file " + compressed.getAbsolutePath()
                            + " to " + ghLocation, ex);
                }
            }
        }

        if (!allowWrites && dataAccessDefaultType.isMMap())
            dataAccessDefaultType = DAType.MMAP_RO;
        if (tagParserManager == null) {
            StorableProperties properties = new StorableProperties(new GHDirectory(ghLocation, dataAccessDefaultType));
            tagParserManager = properties.loadExisting()
                    ? TagParserManager.create(emBuilder, encodedValueFactory, flagEncoderFactory, properties)
                    : buildEncodingManager(new GraphHopperConfig());
        }

        GHDirectory directory = new GHDirectory(ghLocation, dataAccessDefaultType);
        directory.configure(dataAccessConfig);
        ghStorage = new GraphBuilder(tagParserManager)
                .setDir(directory)
                .set3D(hasElevation())
                .withTurnCosts(tagParserManager.needsTurnCostsSupport())
                .setSegmentSize(defaultSegmentSize)
                .build();
        checkProfilesConsistency();

        if (!new File(ghLocation).exists())
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
            directory.loadMMap();
            setFullyLoaded();
            return true;
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    private void checkProfilesConsistency() {
        TagParserManager encodingManager = getTagParserManager();
        for (Profile profile : profilesByName.values()) {
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

            if (profile instanceof CustomProfile) {
                CustomModel customModel = ((CustomProfile) profile).getCustomModel();
                if (customModel == null)
                    throw new IllegalArgumentException("custom model for profile '" + profile.getName() + "' was empty");
                if (!CustomWeighting.NAME.equals(profile.getWeighting()))
                    throw new IllegalArgumentException("profile '" + profile.getName() + "' has a custom model but " +
                            "weighting=" + profile.getWeighting() + " was defined");
            }
        }

        Set<String> chProfileSet = new LinkedHashSet<>(chPreparationHandler.getCHProfiles().size());
        for (CHProfile chProfile : chPreparationHandler.getCHProfiles()) {
            boolean added = chProfileSet.add(chProfile.getProfile());
            if (!added) {
                throw new IllegalArgumentException("Duplicate CH reference to profile '" + chProfile.getProfile() + "'");
            }
            if (!profilesByName.containsKey(chProfile.getProfile())) {
                throw new IllegalArgumentException("CH profile references unknown profile '" + chProfile.getProfile() + "'");
            }
        }
        Map<String, LMProfile> lmProfileMap = new LinkedHashMap<>(lmPreparationHandler.getLMProfiles().size());
        for (LMProfile lmProfile : lmPreparationHandler.getLMProfiles()) {
            LMProfile previous = lmProfileMap.put(lmProfile.getProfile(), lmProfile);
            if (previous != null) {
                throw new IllegalArgumentException("Multiple LM profiles are using the same profile '" + lmProfile.getProfile() + "'");
            }
            if (!profilesByName.containsKey(lmProfile.getProfile())) {
                throw new IllegalArgumentException("LM profile references unknown profile '" + lmProfile.getProfile() + "'");
            }
            if (lmProfile.usesOtherPreparation() && !profilesByName.containsKey(lmProfile.getPreparationProfile())) {
                throw new IllegalArgumentException("LM profile references unknown preparation profile '" + lmProfile.getPreparationProfile() + "'");
            }
        }
        for (LMProfile lmProfile : lmPreparationHandler.getLMProfiles()) {
            if (lmProfile.usesOtherPreparation() && !lmProfileMap.containsKey(lmProfile.getPreparationProfile())) {
                throw new IllegalArgumentException("Unknown LM preparation profile '" + lmProfile.getPreparationProfile() + "' in LM profile '" + lmProfile.getProfile() + "' cannot be used as preparation_profile");
            }
            if (lmProfile.usesOtherPreparation() && lmProfileMap.get(lmProfile.getPreparationProfile()).usesOtherPreparation()) {
                throw new IllegalArgumentException("Cannot use '" + lmProfile.getPreparationProfile() + "' as preparation_profile for LM profile '" + lmProfile.getProfile() + "', because it uses another profile for preparation itself.");
            }
        }
    }

    public final CHPreparationHandler getCHPreparationHandler() {
        return chPreparationHandler;
    }

    private List<CHConfig> createCHConfigs(List<CHProfile> chProfiles) {
        List<CHConfig> chConfigs = new ArrayList<>();
        for (CHProfile chProfile : chProfiles) {
            Profile profile = profilesByName.get(chProfile.getProfile());
            if (profile.isTurnCosts()) {
                chConfigs.add(CHConfig.edgeBased(profile.getName(), createWeighting(profile, new PMap())));
            } else {
                chConfigs.add(CHConfig.nodeBased(profile.getName(), createWeighting(profile, new PMap())));
            }
        }
        return chConfigs;
    }

    public final LMPreparationHandler getLMPreparationHandler() {
        return lmPreparationHandler;
    }

    private List<LMConfig> createLMConfigs(List<LMProfile> lmProfiles) {
        List<LMConfig> lmConfigs = new ArrayList<>();
        for (LMProfile lmProfile : lmProfiles) {
            if (lmProfile.usesOtherPreparation())
                continue;
            Profile profile = profilesByName.get(lmProfile.getProfile());
            // Note that we have to make sure the weighting used for LM preparation does not include turn costs, because
            // the LM preparation is running node-based and the landmark weights will be wrong if there are non-zero
            // turn costs, see discussion in #1960
            // Running the preparation without turn costs is also useful to allow e.g. changing the u_turn_costs per
            // request (we have to use the minimum weight settings (= no turn costs) for the preparation)
            Weighting weighting = createWeighting(profile, new PMap(), true);
            lmConfigs.add(new LMConfig(profile.getName(), weighting));
        }
        return lmConfigs;
    }

    /**
     * Runs both after the import and when loading an existing Graph
     *
     * @param closeEarly release resources as early as possible
     */
    protected void postProcessing(boolean closeEarly) {
        initLocationIndex();
        importPublicTransit();

        if (closeEarly) {
            boolean includesCustomProfiles = profilesByName.values().stream().anyMatch(p -> p instanceof CustomProfile);
            if (!includesCustomProfiles)
                // when there are custom profiles we must not close way geometry or StringIndex, because
                // they might be needed to evaluate the custom weightings for the following preparations
                ghStorage.flushAndCloseGeometryAndNameStorage();
        }

        if (lmPreparationHandler.isEnabled())
            loadOrPrepareLM(closeEarly);

        if (closeEarly)
            // we needed the location index for the LM preparation, but we don't need it for CH
            locationIndex.close();

        if (chPreparationHandler.isEnabled())
            loadOrPrepareCH(closeEarly);
    }

    protected void importPublicTransit() {
    }

    void interpolateBridgesTunnelsAndFerries() {
        if (ghStorage.getEncodingManager().hasEncodedValue(RoadEnvironment.KEY)) {
            EnumEncodedValue<RoadEnvironment> roadEnvEnc = ghStorage.getEncodingManager().getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
            StopWatch sw = new StopWatch().start();
            new EdgeElevationInterpolator(ghStorage.getBaseGraph(), roadEnvEnc, RoadEnvironment.TUNNEL).execute();
            float tunnel = sw.stop().getSeconds();
            sw = new StopWatch().start();
            new EdgeElevationInterpolator(ghStorage.getBaseGraph(), roadEnvEnc, RoadEnvironment.BRIDGE).execute();
            float bridge = sw.stop().getSeconds();
            // The SkadiProvider contains bathymetric data. For ferries this can result in bigger elevation changes
            // See #2098 for mor information
            sw = new StopWatch().start();
            new EdgeElevationInterpolator(ghStorage.getBaseGraph(), roadEnvEnc, RoadEnvironment.FERRY).execute();
            logger.info("Bridge interpolation " + (int) bridge + "s, " + "tunnel interpolation " + (int) tunnel + "s, ferry interpolation " + (int) sw.stop().getSeconds() + "s");
        }
    }

    public final Weighting createWeighting(Profile profile, PMap hints) {
        return createWeighting(profile, hints, false);
    }

    public final Weighting createWeighting(Profile profile, PMap hints, boolean disableTurnCosts) {
        return createWeightingFactory().createWeighting(profile, hints, disableTurnCosts);
    }

    protected WeightingFactory createWeightingFactory() {
        return new DefaultWeightingFactory(ghStorage.getBaseGraph(), getEncodingManager());
    }

    public GHResponse route(GHRequest request) {
        return createRouter().route(request);
    }

    private Router createRouter() {
        if (ghStorage == null || !fullyLoaded)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");
        if (ghStorage.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return doCreateRouter(ghStorage, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, createWeightingFactory(), chGraphs, landmarks);
    }

    protected Router doCreateRouter(GraphHopperStorage ghStorage, LocationIndex locationIndex, Map<String, Profile> profilesByName,
                                    PathDetailsBuilderFactory pathBuilderFactory, TranslationMap trMap, RouterConfig routerConfig,
                                    WeightingFactory weightingFactory, Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        return new Router(ghStorage.getBaseGraph(), ghStorage.getEncodingManager(), locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks
        );
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

    private String getCHProfileVersion(String profile) {
        return ghStorage.getProperties().get("graph.profiles.ch." + profile + ".version");
    }

    private void setCHProfileVersion(String profile, int version) {
        ghStorage.getProperties().put("graph.profiles.ch." + profile + ".version", version);
    }

    private String getLMProfileVersion(String profile) {
        return ghStorage.getProperties().get("graph.profiles.lm." + profile + ".version");
    }

    private void setLMProfileVersion(String profile, int version) {
        ghStorage.getProperties().put("graph.profiles.lm." + profile + ".version", version);
    }

    protected void loadOrPrepareCH(boolean closeEarly) {
        for (CHProfile profile : chPreparationHandler.getCHProfiles())
            if (!getCHProfileVersion(profile.getProfile()).isEmpty()
                    && !getCHProfileVersion(profile.getProfile()).equals("" + profilesByName.get(profile.getProfile()).getVersion()))
                throw new IllegalArgumentException("CH preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration");

        // we load ch graphs that already exist and prepare the other ones
        List<CHConfig> chConfigs = createCHConfigs(chPreparationHandler.getCHProfiles());
        Map<String, RoutingCHGraph> loaded = chPreparationHandler.load(ghStorage.getBaseGraph(), chConfigs);
        List<CHConfig> configsToPrepare = chConfigs.stream().filter(c -> !loaded.containsKey(c.getName())).collect(Collectors.toList());
        Map<String, PrepareContractionHierarchies.Result> prepared = prepareCH(closeEarly, configsToPrepare);

        // we map all profile names for which there is CH support to the according CH graphs
        chGraphs = new LinkedHashMap<>();
        for (CHProfile profile : chPreparationHandler.getCHProfiles()) {
            if (loaded.containsKey(profile.getProfile()) && prepared.containsKey(profile.getProfile()))
                throw new IllegalStateException("CH graph should be either loaded or prepared, but not both: " + profile.getProfile());
            else if (prepared.containsKey(profile.getProfile())) {
                setCHProfileVersion(profile.getProfile(), profilesByName.get(profile.getProfile()).getVersion());
                PrepareContractionHierarchies.Result res = prepared.get(profile.getProfile());
                chGraphs.put(profile.getProfile(), RoutingCHGraphImpl.fromGraph(ghStorage, res.getCHStorage(), res.getCHConfig()));
            } else if (loaded.containsKey(profile.getProfile())) {
                chGraphs.put(profile.getProfile(), loaded.get(profile.getProfile()));
            } else
                throw new IllegalStateException("CH graph should be either loaded or prepared: " + profile.getProfile());
        }
    }

    protected Map<String, PrepareContractionHierarchies.Result> prepareCH(boolean closeEarly, List<CHConfig> configsToPrepare) {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess();
        ghStorage.freeze();
        return chPreparationHandler.prepare(ghStorage, configsToPrepare, closeEarly);
    }

    /**
     * For landmarks it is required to always call this method: either it creates the landmark data or it loads it.
     */
    protected void loadOrPrepareLM(boolean closeEarly) {
        for (LMProfile profile : lmPreparationHandler.getLMProfiles())
            if (!getLMProfileVersion(profile.getProfile()).isEmpty()
                    && !getLMProfileVersion(profile.getProfile()).equals("" + profilesByName.get(profile.getProfile()).getVersion()))
                throw new IllegalArgumentException("LM preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration");

        // we load landmark storages that already exist and prepare the other ones
        List<LMConfig> lmConfigs = createLMConfigs(lmPreparationHandler.getLMProfiles());
        List<LandmarkStorage> loaded = lmPreparationHandler.load(lmConfigs, ghStorage.getBaseGraph());
        List<LMConfig> loadedConfigs = loaded.stream().map(LandmarkStorage::getLMConfig).collect(Collectors.toList());
        List<LMConfig> configsToPrepare = lmConfigs.stream().filter(c -> !loadedConfigs.contains(c)).collect(Collectors.toList());
        List<PrepareLandmarks> prepared = prepareLM(closeEarly, configsToPrepare);

        // we map all profile names for which there is LM support to the according LM storages
        landmarks = new LinkedHashMap<>();
        for (LMProfile lmp : lmPreparationHandler.getLMProfiles()) {
            // cross-querying
            String prepProfile = lmp.usesOtherPreparation() ? lmp.getPreparationProfile() : lmp.getProfile();
            Optional<LandmarkStorage> loadedLMS = loaded.stream().filter(lms -> lms.getLMConfig().getName().equals(prepProfile)).findFirst();
            Optional<PrepareLandmarks> preparedLMS = prepared.stream().filter(pl -> pl.getLandmarkStorage().getLMConfig().getName().equals(prepProfile)).findFirst();
            if (loadedLMS.isPresent() && preparedLMS.isPresent())
                throw new IllegalStateException("LM should be either loaded or prepared, but not both: " + prepProfile);
            else if (preparedLMS.isPresent()) {
                setLMProfileVersion(lmp.getProfile(), profilesByName.get(lmp.getProfile()).getVersion());
                landmarks.put(lmp.getProfile(), preparedLMS.get().getLandmarkStorage());
            } else
                loadedLMS.ifPresent(landmarkStorage -> landmarks.put(lmp.getProfile(), landmarkStorage));
        }
    }

    protected List<PrepareLandmarks> prepareLM(boolean closeEarly, List<LMConfig> configsToPrepare) {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess();
        ghStorage.freeze();
        return lmPreparationHandler.prepare(configsToPrepare, ghStorage, locationIndex, closeEarly);
    }

    /**
     * Internal method to clean up the graph.
     */
    protected void cleanUp() {
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(ghStorage.getBaseGraph(), buildSubnetworkRemovalJobs());
        preparation.setMinNetworkSize(minNetworkSize);
        preparation.doWork();
        logger.info("nodes: " + Helper.nf(ghStorage.getNodes()) + ", edges: " + Helper.nf(ghStorage.getEdges()));
    }

    private List<PrepareJob> buildSubnetworkRemovalJobs() {
        List<PrepareJob> jobs = new ArrayList<>();
        for (Profile profile : profilesByName.values()) {
            // if turn costs are enabled use u-turn costs of zero as we only want to make sure the graph is fully connected assuming finite u-turn costs
            Weighting weighting = createWeighting(profile, new PMap().putObject(Parameters.Routing.U_TURN_COSTS, 0));
            jobs.add(new PrepareJob(tagParserManager.getBooleanEncodedValue(Subnetwork.key(profile.getName())), weighting));
        }
        return jobs;
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

        chGraphs.values().forEach(RoutingCHGraph::close);
        landmarks.values().forEach(LandmarkStorage::close);

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

    private void setFullyLoaded() {
        fullyLoaded = true;
    }

    public boolean getFullyLoaded() {
        return fullyLoaded;
    }

    public RouterConfig getRouterConfig() {
        return routerConfig;
    }

    public OSMReaderConfig getReaderConfig() {
        return osmReaderConfig;
    }
}