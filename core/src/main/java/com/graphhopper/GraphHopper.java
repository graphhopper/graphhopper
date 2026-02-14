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
import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.sorting.IndirectSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.dem.*;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.RestrictionTagParser;
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
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMFootNetworkTagParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.routing.weighting.custom.NameValidator;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    private MaxSpeedCalculator maxSpeedCalculator;
    private final Map<String, Profile> profilesByName = new LinkedHashMap<>();
    private final String fileLockName = "gh.lock";
    // utils
    private final TranslationMap trMap = new TranslationMap().doImport();
    boolean removeZipped = true;
    boolean calcChecksums = false;
    // for custom areas:
    private String customAreasDirectory = "";
    // for graph:
    private BaseGraph baseGraph;
    private StorableProperties properties;
    protected EncodingManager encodingManager;
    private OSMParsers osmParsers;
    private int defaultSegmentSize = AbstractDataAccess.SEGMENT_SIZE_DEFAULT;
    private String ghLocation = "";
    private DAType dataAccessDefaultType = DAType.RAM_STORE;
    private final LinkedHashMap<String, String> dataAccessConfig = new LinkedHashMap<>();
    private boolean sortGraph = true;
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
    // subnetworks
    private int minNetworkSize = 200;
    private int subnetworksThreads = 1;
    // residential areas
    private double residentialAreaRadius = 400;
    private double residentialAreaSensitivity = 6000;
    private double cityAreaRadius = 1500;
    private double cityAreaSensitivity = 1000;
    private int urbanDensityCalculationThreads = 0;

    // preparation handlers
    private final LMPreparationHandler lmPreparationHandler = new LMPreparationHandler();
    private final CHPreparationHandler chPreparationHandler = new CHPreparationHandler();
    private Map<String, RoutingCHGraph> chGraphs = Collections.emptyMap();
    private Map<String, LandmarkStorage> landmarks = Collections.emptyMap();

    // for data reader
    private String osmFile;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private ImportRegistry importRegistry = new DefaultImportRegistry();
    private PathDetailsBuilderFactory pathBuilderFactory = new PathDetailsBuilderFactory();

    private String dateRangeParserString = "";
    private String encodedValuesString = "";

    public GraphHopper setEncodedValuesString(String encodedValuesString) {
        this.encodedValuesString = encodedValuesString;
        return this;
    }

    public String getEncodedValuesString() {
        return encodedValuesString;
    }

    public EncodingManager getEncodingManager() {
        if (encodingManager == null)
            throw new IllegalStateException("EncodingManager not yet built");
        return encodingManager;
    }

    public OSMParsers getOSMParsers() {
        if (osmParsers == null)
            throw new IllegalStateException("OSMParsers not yet built");
        return osmParsers;
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
     * Configures the urban density classification. Each edge will be classified as 'rural','residential' or 'city', {@link UrbanDensity}
     *
     * @param residentialAreaRadius      in meters. The higher this value the longer the calculation will take and the bigger the area for
     *                                   which the road density used to identify residential areas is calculated.
     * @param residentialAreaSensitivity Use this to find a trade-off between too many roads being classified as residential (too high
     *                                   values) and not enough roads being classified as residential (too small values)
     * @param cityAreaRadius             in meters. The higher this value the longer the calculation will take and the bigger the area for
     *                                   which the road density used to identify city areas is calculated. Set this to zero
     *                                   to skip the city classification.
     * @param cityAreaSensitivity        Use this to find a trade-off between too many roads being classified as city (too high values)
     *                                   and not enough roads being classified as city (too small values)
     * @param threads                    the number of threads used for the calculation. If this is zero the urban density
     *                                   calculation is skipped entirely
     */
    public GraphHopper setUrbanDensityCalculation(double residentialAreaRadius, double residentialAreaSensitivity,
                                                  double cityAreaRadius, double cityAreaSensitivity, int threads) {
        ensureNotLoaded();
        this.residentialAreaRadius = residentialAreaRadius;
        this.residentialAreaSensitivity = residentialAreaSensitivity;
        this.cityAreaRadius = cityAreaRadius;
        this.cityAreaSensitivity = cityAreaSensitivity;
        this.urbanDensityCalculationThreads = threads;
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
     *     new Profile("my_car"),
     *     new Profile("your_bike")
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
        if (encodingManager != null)
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

    public TransportationMode getNavigationMode(String profileName) {
        Profile profile = profilesByName.get(profileName);
        if (profile == null) return TransportationMode.CAR;
        try {
            return TransportationMode.valueOf(profile.getHints().getString("navigation_mode", profileName).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TransportationMode.CAR;
        }
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

    public GraphHopper setMaxSpeedCalculator(MaxSpeedCalculator maxSpeedCalculator) {
        this.maxSpeedCalculator = maxSpeedCalculator;
        return this;
    }

    public GraphHopper setSortGraph(boolean sortGraph) {
        this.sortGraph = sortGraph;
        return this;
    }

    /**
     * The underlying graph used in algorithms.
     *
     * @throws IllegalStateException if graph is not instantiated.
     */
    public BaseGraph getBaseGraph() {
        if (baseGraph == null)
            throw new IllegalStateException("GraphHopper storage not initialized");

        return baseGraph;
    }

    public void setBaseGraph(BaseGraph baseGraph) {
        this.baseGraph = baseGraph;
        setFullyLoaded();
    }

    public StorableProperties getProperties() {
        return properties;
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

    public GraphHopper setImportRegistry(ImportRegistry importRegistry) {
        this.importRegistry = importRegistry;
        return this;
    }

    public ImportRegistry getImportRegistry() {
        return importRegistry;
    }

    public GraphHopper setCustomAreasDirectory(String customAreasDirectory) {
        this.customAreasDirectory = customAreasDirectory;
        return this;
    }

    public String getCustomAreasDirectory() {
        return this.customAreasDirectory;
    }

    /**
     * Reads the configuration from a {@link GraphHopperConfig} object which can be manually filled, or more typically
     * is read from `config.yml`.
     * <p>
     * Important note: Calling this method overwrites the configuration done in some of the setter methods of this class,
     * so generally it is advised to either use this method to configure GraphHopper or the different setter methods,
     * but not both. Unfortunately, this still does not cover all cases and sometimes you have to use both, but then you
     * should make sure there are no conflicts. If you need both it might also help to call the init before calling the
     * setters, because this way the init method won't apply defaults to configuration options you already chose using
     * the setters.
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

        sortGraph = ghConfig.getBool("graph.sort", sortGraph);
        if (ghConfig.getBool("max_speed_calculator.enabled", false))
            maxSpeedCalculator = new MaxSpeedCalculator(MaxSpeedCalculator.createLegalDefaultSpeeds());

        removeZipped = ghConfig.getBool("graph.remove_zipped", removeZipped);

        if (!ghConfig.getString("spatial_rules.location", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.location has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.");
        if (!ghConfig.getString("spatial_rules.borders_directory", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.borders_directory has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.");
        // todo: maybe introduce custom_areas.max_bbox if this is needed later
        if (!ghConfig.getString("spatial_rules.max_bbox", "").isEmpty())
            throw new IllegalArgumentException("spatial_rules.max_bbox has been deprecated. There is no replacement, all custom areas will be considered.");

        String customAreasDirectory = ghConfig.getString("custom_areas.directory", "");
        JsonFeatureCollection globalAreas = GraphHopper.resolveCustomAreas(customAreasDirectory);
        String customModelFolder = ghConfig.getString("custom_models.directory", ghConfig.getString("custom_model_folder", ""));
        setProfiles(GraphHopper.resolveCustomModelFiles(customModelFolder, ghConfig.getProfiles(), globalAreas));

        if (ghConfig.has("graph.vehicles"))
            throw new IllegalArgumentException("The option graph.vehicles is no longer supported. Use the appropriate turn_costs and custom_model instead, see docs/migration/config-migration-08-09.md");
        if (ghConfig.has("graph.flag_encoders"))
            throw new IllegalArgumentException("The option graph.flag_encoders is no longer supported.");

        encodedValuesString = ghConfig.getString("graph.encoded_values", encodedValuesString);
        dateRangeParserString = ghConfig.getString("datareader.date_range_parser_day", dateRangeParserString);

        if (ghConfig.getString("graph.locktype", "native").equals("simple"))
            lockFactory = new SimpleFSLockFactory();
        else
            lockFactory = new NativeFSLockFactory();

        // elevation
        if (ghConfig.has("graph.elevation.smoothing"))
            throw new IllegalArgumentException("Use 'graph.elevation.edge_smoothing: moving_average' or the new 'graph.elevation.edge_smoothing: ramer'. See #2634.");
        osmReaderConfig.setElevationSmoothing(ghConfig.getString("graph.elevation.edge_smoothing", osmReaderConfig.getElevationSmoothing()));
        osmReaderConfig.setSmoothElevationAverageWindowSize(ghConfig.getDouble("graph.elevation.edge_smoothing.moving_average.window_size", osmReaderConfig.getSmoothElevationAverageWindowSize()));
        osmReaderConfig.setElevationSmoothingRamerMax(ghConfig.getInt("graph.elevation.edge_smoothing.ramer.max_elevation", osmReaderConfig.getElevationSmoothingRamerMax()));
        osmReaderConfig.setLongEdgeSamplingDistance(ghConfig.getDouble("graph.elevation.long_edge_sampling_distance", osmReaderConfig.getLongEdgeSamplingDistance()));
        osmReaderConfig.setElevationMaxWayPointDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", osmReaderConfig.getElevationMaxWayPointDistance()));
        routerConfig.setElevationWayPointMaxDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", routerConfig.getElevationWayPointMaxDistance()));
        ElevationProvider elevationProvider = createElevationProvider(ghConfig);
        setElevationProvider(elevationProvider);

        if (osmReaderConfig.getLongEdgeSamplingDistance() < Double.MAX_VALUE && !elevationProvider.canInterpolate())
            logger.warn("Long edge sampling enabled, but bilinear interpolation disabled. See #1953");

        // optimizable prepare
        minNetworkSize = ghConfig.getInt("prepare.min_network_size", minNetworkSize);
        subnetworksThreads = ghConfig.getInt("prepare.subnetworks.threads", subnetworksThreads);

        // prepare CH&LM
        chPreparationHandler.init(ghConfig);
        lmPreparationHandler.init(ghConfig);

        // osm import
        // We do a few checks for import.osm.ignored_highways to prevent configuration errors when migrating from an older
        // GH version.
        if (!ghConfig.has("import.osm.ignored_highways"))
            throw new IllegalArgumentException("Missing 'import.osm.ignored_highways'. Not using this parameter can decrease performance, see config-example.yml for more details");
        String ignoredHighwaysString = ghConfig.getString("import.osm.ignored_highways", "");
        if ((ignoredHighwaysString.contains("footway") || ignoredHighwaysString.contains("path")) && ghConfig.getProfiles().stream().map(Profile::getName).anyMatch(p -> p.contains("foot") || p.contains("hike")))
            throw new IllegalArgumentException("You should not use import.osm.ignored_highways=footway or =path in conjunction with pedestrian profiles. This is probably an error in your configuration.");
        if ((ignoredHighwaysString.contains("cycleway") || ignoredHighwaysString.contains("path")) && ghConfig.getProfiles().stream().map(Profile::getName).anyMatch(p -> p.contains("mtb") || p.contains("bike")))
            throw new IllegalArgumentException("You should not use import.osm.ignored_highways=cycleway or =path in conjunction with bicycle profiles. This is probably an error in your configuration");

        osmReaderConfig.setIgnoredHighways(Arrays.stream(ghConfig.getString("import.osm.ignored_highways", String.join(",", osmReaderConfig.getIgnoredHighways()))
                .split(",")).map(String::trim).collect(Collectors.toList()));
        osmReaderConfig.setParseWayNames(ghConfig.getBool("datareader.instructions", osmReaderConfig.isParseWayNames()));
        osmReaderConfig.setPreferredLanguage(ghConfig.getString("datareader.preferred_language", osmReaderConfig.getPreferredLanguage()));
        osmReaderConfig.setMaxWayPointDistance(ghConfig.getDouble(Routing.INIT_WAY_POINT_MAX_DISTANCE, osmReaderConfig.getMaxWayPointDistance()));
        osmReaderConfig.setWorkerThreads(ghConfig.getInt("datareader.worker_threads", osmReaderConfig.getWorkerThreads()));
        String storedTagsStr = ghConfig.getString("graph.stored_tags", "");
        if (!storedTagsStr.isEmpty())
            osmReaderConfig.setStoredTags(new LinkedHashSet<>(Arrays.asList(storedTagsStr.split(","))));

        // index
        preciseIndexResolution = ghConfig.getInt("index.high_resolution", preciseIndexResolution);
        maxRegionSearch = ghConfig.getInt("index.max_region_search", maxRegionSearch);

        // urban density calculation
        residentialAreaRadius = ghConfig.getDouble("graph.urban_density.residential_radius", residentialAreaRadius);
        residentialAreaSensitivity = ghConfig.getDouble("graph.urban_density.residential_sensitivity", residentialAreaSensitivity);
        cityAreaRadius = ghConfig.getDouble("graph.urban_density.city_radius", cityAreaRadius);
        cityAreaSensitivity = ghConfig.getDouble("graph.urban_density.city_sensitivity", cityAreaSensitivity);
        urbanDensityCalculationThreads = ghConfig.getInt("graph.urban_density.threads", urbanDensityCalculationThreads);

        // routing
        routerConfig.setMaxVisitedNodes(ghConfig.getInt(Routing.INIT_MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes()));
        routerConfig.setTimeoutMillis(ghConfig.getLong(Routing.INIT_TIMEOUT_MS, routerConfig.getTimeoutMillis()));
        routerConfig.setMaxRoundTripRetries(ghConfig.getInt(RoundTrip.INIT_MAX_RETRIES, routerConfig.getMaxRoundTripRetries()));
        routerConfig.setNonChMaxWaypointDistance(ghConfig.getInt(Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE, routerConfig.getNonChMaxWaypointDistance()));
        routerConfig.setInstructionsEnabled(ghConfig.getBool(Routing.INIT_INSTRUCTIONS, routerConfig.isInstructionsEnabled()));
        int activeLandmarkCount = ghConfig.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.min(8, lmPreparationHandler.getLandmarks()));
        if (activeLandmarkCount > lmPreparationHandler.getLandmarks())
            throw new IllegalArgumentException("Default value for active landmarks " + activeLandmarkCount
                    + " should be less or equal to landmark count of " + lmPreparationHandler.getLandmarks());
        routerConfig.setActiveLandmarkCount(activeLandmarkCount);

        calcChecksums = ghConfig.getBool("graph.calc_checksums", false);

        return this;
    }

    protected EncodingManager buildEncodingManager(Map<String, PMap> encodedValuesWithProps,
                                                   Map<String, ImportUnit> activeImportUnits,
                                                   Map<String, List<String>> restrictionVehicleTypesByProfile) {
        List<EncodedValue> encodedValues = new ArrayList<>(activeImportUnits.entrySet().stream()
                .map(e -> {
                    Function<PMap, EncodedValue> f = e.getValue().getCreateEncodedValue();
                    return f == null ? null : f.apply(encodedValuesWithProps.getOrDefault(e.getKey(), new PMap()));
                })
                .filter(Objects::nonNull)
                .toList());

        encodedValues.addAll(createSubnetworkEncodedValues());

        List<String> sortedEVs = getEVSortIndex(profilesByName);
        encodedValues.sort(Comparator.comparingInt(ev -> sortedEVs.indexOf(ev.getName())));

        EncodingManager.Builder emBuilder = new EncodingManager.Builder();
        encodedValues.forEach(emBuilder::add);
        for (String tag : osmReaderConfig.getStoredTags()) {
            emBuilder.add(new KVStorageEncodedValue(tag));
        }
        // TODO NOW: better integrate with existing data in KVStorage
        if (!osmReaderConfig.getStoredTags().isEmpty()) {
            // probably we should also change street_name into name to make it easier to use
            emBuilder.add(new KVStorageEncodedValue(Parameters.Details.STREET_NAME));
            emBuilder.add(new KVStorageEncodedValue(Parameters.Details.STREET_REF));
        }

        restrictionVehicleTypesByProfile.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> emBuilder.addTurnCostEncodedValue(TurnRestriction.create(e.getKey())));
        return emBuilder.build();
    }

    protected List<BooleanEncodedValue> createSubnetworkEncodedValues() {
        return profilesByName.values().stream().map(profile -> Subnetwork.create(profile.getName())).toList();
    }

    protected List<String> getEVSortIndex(Map<String, Profile> profilesByName) {
        return Collections.emptyList();
    }

    protected OSMParsers buildOSMParsers(Map<String, PMap> encodedValuesWithProps,
                                         Map<String, ImportUnit> activeImportUnits,
                                         Map<String, List<String>> restrictionVehicleTypesByProfile,
                                         List<String> ignoredHighways) {
        ImportUnitSorter sorter = new ImportUnitSorter(activeImportUnits);
        Map<String, ImportUnit> sortedImportUnits = new LinkedHashMap<>();
        sorter.sort().forEach(name -> sortedImportUnits.put(name, activeImportUnits.get(name)));
        List<TagParser> sortedParsers = new ArrayList<>();
        sortedImportUnits.forEach((name, importUnit) -> {
            BiFunction<EncodedValueLookup, PMap, TagParser> createTagParser = importUnit.getCreateTagParser();
            if (createTagParser != null) {
                PMap pmap = encodedValuesWithProps.getOrDefault(name, new PMap());
                if (!pmap.has("date_range_parser_day"))
                    pmap.putObject("date_range_parser_day", dateRangeParserString);
                sortedParsers.add(createTagParser.apply(encodingManager, pmap));
            }
        });

        OSMParsers osmParsers = new OSMParsers();
        ignoredHighways.forEach(osmParsers::addIgnoredHighway);
        sortedParsers.forEach(osmParsers::addWayTagParser);

        if (maxSpeedCalculator != null) {
            maxSpeedCalculator.checkEncodedValues(encodingManager);
            osmParsers.addWayTagParser(maxSpeedCalculator.getParser());
        }

        if (encodingManager.hasEncodedValue(BikeNetwork.KEY))
            osmParsers.addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(encodingManager.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), relConfig, "bicycle"));
        if (encodingManager.hasEncodedValue(MtbNetwork.KEY))
            osmParsers.addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(encodingManager.getEnumEncodedValue(MtbNetwork.KEY, RouteNetwork.class), relConfig, "mtb"));
        if (encodingManager.hasEncodedValue(FootNetwork.KEY))
            osmParsers.addRelationTagParser(relConfig -> new OSMFootNetworkTagParser(encodingManager.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class), relConfig));

        restrictionVehicleTypesByProfile.forEach((profile, restrictionVehicleTypes) -> {
            osmParsers.addRestrictionTagParser(new RestrictionTagParser(
                    restrictionVehicleTypes, encodingManager.getTurnBooleanEncodedValue(TurnRestriction.key(profile))));
        });
        return osmParsers;
    }

    public static Map<String, PMap> parseEncodedValueString(String encodedValuesStr) {
        Map<String, PMap> encodedValuesWithProps = new LinkedHashMap<>();
        Arrays.stream(encodedValuesStr.split(","))
                .filter(evStr -> !evStr.isBlank())
                .forEach(evStr -> {
                    String key = evStr.trim().split("\\|")[0];
                    if (encodedValuesWithProps.put(key, new PMap(evStr)) != null)
                        throw new IllegalArgumentException("duplicate encoded value in config graph.encoded_values: " + key);
                });
        return encodedValuesWithProps;
    }

    private static Map<String, List<String>> getRestrictionVehicleTypesByProfile(Collection<Profile> profiles) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Profile profile : profiles)
            if (profile.hasTurnCosts())
                result.put(profile.getName(), profile.getTurnCostsConfig().getVehicleTypes());
        return result;
    }

    private static ElevationProvider createElevationProvider(GraphHopperConfig ghConfig) {
        String eleProviderStr = toLowerCase(ghConfig.getString("graph.elevation.provider", "noop"));

        if (ghConfig.has("graph.elevation.calcmean"))
            throw new IllegalArgumentException("graph.elevation.calcmean is deprecated, use graph.elevation.interpolate");

        String cacheDirStr = ghConfig.getString("graph.elevation.cache_dir", "");
        if (cacheDirStr.isEmpty() && ghConfig.has("graph.elevation.cachedir"))
            throw new IllegalArgumentException("use graph.elevation.cache_dir not cachedir in configuration");

        ElevationProvider elevationProvider = ElevationProvider.NOOP;
        if (eleProviderStr.equalsIgnoreCase("hgt")) {
            elevationProvider = new HGTProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("srtm")) {
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
        } else if (eleProviderStr.equalsIgnoreCase("sonny")) {
            elevationProvider = new SonnyProvider(cacheDirStr);
        } else if (eleProviderStr.equalsIgnoreCase("multi3")) {
            elevationProvider = new MultiSource3ElevationProvider(cacheDirStr);
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
        if (baseGraph != null)
            logger.info("graph " + getBaseGraphString() + ", details:" + baseGraph.toDetailsString());
    }

    private String getBaseGraphString() {
        return encodingManager
                + "|" + baseGraph.getDirectory().getDefaultType()
                + "|" + baseGraph.getNodeAccess().getDimension() + "D"
                + "|" + (baseGraph.getTurnCostStorage() != null ? baseGraph.getTurnCostStorage() : "no_turn_cost")
                + "|" + getVersionsString();
    }

    private String getVersionsString() {
        return "nodes:" + Constants.VERSION_NODE +
                ",edges:" + Constants.VERSION_EDGE +
                ",geometry:" + Constants.VERSION_GEOMETRY +
                ",location_index:" + Constants.VERSION_LOCATION_IDX +
                ",string_index:" + Constants.VERSION_KV_STORAGE +
                ",nodesCH:" + Constants.VERSION_NODE_CH +
                ",shortcuts:" + Constants.VERSION_SHORTCUT;
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
    protected void process(boolean closeEarly) {
        prepareImport();
        if (encodingManager == null)
            throw new IllegalStateException("The EncodingManager must be created in `prepareImport()`");
        GHDirectory directory = new GHDirectory(ghLocation, dataAccessDefaultType, defaultSegmentSize);
        directory.configure(dataAccessConfig);
        baseGraph = new BaseGraph.Builder(getEncodingManager())
                .setDir(directory)
                .set3D(hasElevation())
                .withTurnCosts(encodingManager.needsTurnCostsSupport())
                .build();
        properties = new StorableProperties(directory);
        checkProfilesConsistency();

        GHLock lock = null;
        try {
            if (directory.getDefaultType().isStoring()) {
                lockFactory.setLockDir(new File(ghLocation));
                lock = lockFactory.create(fileLockName, true);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid multiple writers we need to obtain a write lock but it failed. In " + ghLocation, lock.getObtainFailedReason());
            }
            ensureWriteAccess();

            importOSM();
            postImportOSM();
            cleanUp();

            properties.put("profiles", getProfilesString());
            writeEncodingManagerToProperties();

            postProcessing(closeEarly);
            flush();
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    protected void prepareImport() {
        Map<String, PMap> encodedValuesWithProps = parseEncodedValueString(encodedValuesString);
        NameValidator nameValidator = s -> importRegistry.createImportUnit(s) != null;
        Set<String> missing = new LinkedHashSet<>();
        profilesByName.values().
                forEach(profile -> CustomModelParser.findVariablesForEncodedValuesString(profile.getCustomModel(), nameValidator, s -> "").
                        forEach(var -> {
                            if (!encodedValuesWithProps.containsKey(var)) missing.add(var);
                            encodedValuesWithProps.putIfAbsent(var, new PMap());
                        }));
        if (!missing.isEmpty()) {
            String encodedValuesString = encodedValuesWithProps.entrySet().stream()
                    .map(e -> e.getKey() + (e.getValue().isEmpty() ? "" : ("|" + e.getValue().toMap().entrySet().stream().map(p -> p.getKey() + "=" + p.getValue()).collect(Collectors.joining("|")))))
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Encoded values missing: " + String.join(", ", missing) + ".\n" +
                    "To avoid that certain encoded values are automatically removed when you change the custom model later, you need to set the encoded values manually:\n" +
                    "graph.encoded_values: " + encodedValuesString);
        }

        // following encoded values are used by instructions and in the snap prevention filter (avoid motorway, tunnel, etc.)
        encodedValuesWithProps.putIfAbsent(RoadClass.KEY, new PMap());
        encodedValuesWithProps.putIfAbsent(RoadEnvironment.KEY, new PMap());
        // now only used by instructions:
        encodedValuesWithProps.putIfAbsent(Roundabout.KEY, new PMap());
        encodedValuesWithProps.putIfAbsent(VehicleAccess.key("car"), new PMap());
        encodedValuesWithProps.putIfAbsent(RoadClassLink.KEY, new PMap());
        encodedValuesWithProps.putIfAbsent(MaxSpeed.KEY, new PMap());

        Map<String, List<String>> restrictionVehicleTypesByProfile = getRestrictionVehicleTypesByProfile(profilesByName.values());

        if (urbanDensityCalculationThreads > 0)
            encodedValuesWithProps.put(UrbanDensity.KEY, new PMap());
        if (maxSpeedCalculator != null) {
            if (urbanDensityCalculationThreads <= 0)
                throw new IllegalArgumentException("For max_speed_calculator the urban density calculation needs to be enabled (e.g. graph.urban_density.threads: 1)");
            encodedValuesWithProps.put(MaxSpeedEstimated.KEY, new PMap());
        }

        Map<String, ImportUnit> activeImportUnits = new LinkedHashMap<>();
        ArrayDeque<String> deque = new ArrayDeque<>(encodedValuesWithProps.keySet());
        while (!deque.isEmpty()) {
            String ev = deque.removeFirst();
            ImportUnit importUnit = importRegistry.createImportUnit(ev);
            if (importUnit == null)
                throw new IllegalArgumentException("Unknown encoded value: " + ev);
            if (activeImportUnits.put(ev, importUnit) == null)
                deque.addAll(importUnit.getRequiredImportUnits());
        }
        encodingManager = buildEncodingManager(encodedValuesWithProps, activeImportUnits, restrictionVehicleTypesByProfile);
        osmParsers = buildOSMParsers(encodedValuesWithProps, activeImportUnits, restrictionVehicleTypesByProfile, osmReaderConfig.getIgnoredHighways());
    }

    protected void postImportOSM() {
        // Important note: To deal with via-way turn restrictions we introduce artificial edges in OSMReader (#2689).
        // These are simply copies of real edges. Any further modifications of the graph edges must take care of keeping
        // the artificial edges in sync with their real counterparts. So if an edge attribute shall be changed this change
        // must also be applied to the corresponding artificial edge.
        calculateUrbanDensity();

        if (maxSpeedCalculator != null) {
            maxSpeedCalculator.fillMaxSpeed(getBaseGraph(), encodingManager);
            maxSpeedCalculator.close();
        }

        if (hasElevation())
            interpolateBridgesTunnelsAndFerries();

        if (sortGraph)
            sortGraphAlongHilbertCurve(baseGraph);
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
        OSMReader reader = new OSMReader(baseGraph.getBaseGraph(), osmParsers, osmReaderConfig).setFile(_getOSMFile()).
                setAreaIndex(areaIndex).
                setElevationProvider(eleProvider);
        logger.info("using " + getBaseGraphString() + ", memory:" + getMemInfo());

        createBaseGraphAndProperties();
        initKVStorageEncodedValues(true);

        try {
            reader.readGraph();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read file " + getOSMFile(), ex);
        }
        DateFormat f = createFormatter();
        properties.put("datareader.import.date", f.format(new Date()));
        if (reader.getDataDate() != null)
            properties.put("datareader.data.date", f.format(reader.getDataDate()));
    }

    protected void createBaseGraphAndProperties() {
        baseGraph.getDirectory().create();
        baseGraph.create(100);
        properties.create(100);
        if (maxSpeedCalculator != null)
            maxSpeedCalculator.createDataAccessForParser(baseGraph.getDirectory());
    }

    private void initKVStorageEncodedValues(boolean create) {
        KVStorage kvStorage = baseGraph.getEdgeKVStorage();
        for (EncodedValue ev : encodingManager.getEncodedValues()) {
            if (ev instanceof KVStorageEncodedValue kvEnc) {
                String rawTag = kvEnc.getRawTagName();
                if (create) {
                    kvEnc.setKeyIndex(kvStorage.reserveKey(rawTag, String.class));
                } else {
                    int index = kvStorage.getKeyIndex(rawTag);
                    if (index < 0)
                        throw new IllegalArgumentException("KVStorage key not found for " + rawTag);
                    if (kvEnc.getKeyIndex() != index)
                        throw new IllegalStateException("Stored keyIndex " + kvEnc.getKeyIndex()
                                + " for " + rawTag + " does not match currently configured index: " + index);
                }
            }
        }
    }

    public static void sortGraphAlongHilbertCurve(BaseGraph graph) {
        logger.info("sorting graph along Hilbert curve...");
        StopWatch sw = StopWatch.started();
        NodeAccess na = graph.getNodeAccess();
        final int order = 31; // using 15 would allow us to use ints for sortIndices, but this would result in (marginally) slower routing
        LongArrayList sortIndices = new LongArrayList();
        for (int node = 0; node < graph.getNodes(); node++)
            sortIndices.add(latLonToHilbertIndex(na.getLat(node), na.getLon(node), order));
        int[] nodeOrder = IndirectSort.mergesort(0, graph.getNodes(), (nodeA, nodeB) -> Long.compare(sortIndices.get(nodeA), sortIndices.get(nodeB)));
        EdgeExplorer explorer = graph.createEdgeExplorer();
        int edges = graph.getEdges();
        IntArrayList edgeOrder = new IntArrayList();
        com.carrotsearch.hppc.BitSet edgesFound = new BitSet(edges);
        for (int node : nodeOrder) {
            EdgeIterator iter = explorer.setBaseNode(node);
            while (iter.next()) {
                if (!edgesFound.get(iter.getEdge())) {
                    edgeOrder.add(iter.getEdge());
                    edgesFound.set(iter.getEdge());
                }
            }
        }
        IntArrayList newEdgesByOldEdges = ArrayUtil.invert(edgeOrder);
        IntArrayList newNodesByOldNodes = IntArrayList.from(ArrayUtil.invert(nodeOrder));
        logger.info("calculating sort order took: " + sw.stop().getTimeString());
        sortGraphForGivenOrdering(graph, newNodesByOldNodes, newEdgesByOldEdges);
    }

    public static void sortGraphForGivenOrdering(BaseGraph baseGraph, IntArrayList newNodesByOldNodes, IntArrayList newEdgesByOldEdges) {
        if (!ArrayUtil.isPermutation(newEdgesByOldEdges))
            throw new IllegalStateException("New edges: not a permutation");
        if (!ArrayUtil.isPermutation(newNodesByOldNodes))
            throw new IllegalStateException("New nodes: not a permutation");
        logger.info("sort graph for fixed ordering...");
        StopWatch sw = new StopWatch().start();
        baseGraph.sortEdges(newEdgesByOldEdges::get);
        logger.info("sorting {} edges took: {}", Helper.nf(newEdgesByOldEdges.size()), sw.stop().getTimeString());
        sw = new StopWatch().start();
        baseGraph.relabelNodes(newNodesByOldNodes::get);
        logger.info("sorting {} nodes took: {}", Helper.nf(newNodesByOldNodes.size()), sw.stop().getTimeString());
    }

    public static long latLonToHilbertIndex(double lat, double lon, int order) {
        double nx = (lon + 180) / 360;
        double ny = (90 - lat) / 180;
        long size = 1L << order;
        long x = (long) (nx * size);
        long y = (long) (ny * size);
        x = Math.max(0, Math.min(size - 1, x));
        y = Math.max(0, Math.min(size - 1, y));
        return xy2d(order, x, y);
    }

    public static long xy2d(int n, long x, long y) {
        long d = 0;
        for (long s = 1L << (n - 1); s > 0; s >>= 1) {
            int rx = (x & s) > 0 ? 1 : 0;
            int ry = (y & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            // rotate
            if (ry == 0) {
                if (rx == 1) {
                    x = s - 1 - x;
                    y = s - 1 - y;
                }
                long tmp = x;
                x = y;
                y = tmp;
            }
        }
        return d;
    }

    private void calculateUrbanDensity() {
        if (encodingManager.hasEncodedValue(UrbanDensity.KEY)) {
            EnumEncodedValue<UrbanDensity> urbanDensityEnc = encodingManager.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity.class);
            if (!encodingManager.hasEncodedValue(RoadClass.KEY))
                throw new IllegalArgumentException("Urban density calculation requires " + RoadClass.KEY);
            if (!encodingManager.hasEncodedValue(RoadClassLink.KEY))
                throw new IllegalArgumentException("Urban density calculation requires " + RoadClassLink.KEY);
            EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
            BooleanEncodedValue roadClassLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
            UrbanDensityCalculator.calcUrbanDensity(baseGraph, urbanDensityEnc, roadClassEnc,
                    roadClassLinkEnc, residentialAreaRadius, residentialAreaSensitivity, cityAreaRadius, cityAreaSensitivity, urbanDensityCalculationThreads);
        }
    }

    private void writeEncodingManagerToProperties() {
        EncodingManager.putEncodingManagerIntoProperties(encodingManager, properties);
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

        // todo: this does not really belong here, we abuse the load method to derive the dataAccessDefaultType setting from others
        if (!allowWrites && dataAccessDefaultType.isMMap())
            dataAccessDefaultType = DAType.MMAP_RO;

        if (!new File(ghLocation).exists())
            // there is just nothing to load
            return false;

        GHDirectory directory = new GHDirectory(ghLocation, dataAccessDefaultType);
        directory.configure(dataAccessConfig);
        GHLock lock = null;
        try {
            // create locks only if writes are allowed, if they are not allowed a lock cannot be created
            // (e.g. on a read only filesystem locks would fail)
            if (directory.getDefaultType().isStoring() && isAllowWrites()) {
                lockFactory.setLockDir(new File(ghLocation));
                lock = lockFactory.create(fileLockName, false);
                if (!lock.tryLock())
                    throw new RuntimeException("To avoid reading partial data we need to obtain the read lock but it failed. In " + ghLocation, lock.getObtainFailedReason());
            }
            properties = new StorableProperties(directory);
            if (!properties.loadExisting())
                // the -gh folder exists, but there is no properties file. it might be just empty, so let's act as if
                // the import did not run yet or is not complete for some reason
                return false;
            encodingManager = EncodingManager.fromProperties(properties);
            baseGraph = new BaseGraph.Builder(encodingManager)
                    .setDir(directory)
                    .set3D(hasElevation())
                    .withTurnCosts(encodingManager.needsTurnCostsSupport())
                    .build();
            checkProfilesConsistency();
            baseGraph.loadExisting();
            initKVStorageEncodedValues(false);
            String storedProfilesString = properties.get("profiles");
            Map<String, Integer> storedProfileHashes = Arrays.stream(storedProfilesString.split(",")).map(s -> s.split("\\|", 2)).collect((Collectors.toMap(kv -> kv[0], kv -> Integer.parseInt(kv[1]))));
            Map<String, Integer> configuredProfileHashes = getProfileHashes();
            configuredProfileHashes.forEach((profile, hash) -> {
                Integer storedHash = storedProfileHashes.get(profile);
                if (storedHash == null)
                    throw new IllegalStateException("You cannot add new profiles to the loaded graph. Profile '" + profile + "' is new."
                            + "\nExisting profiles: " + String.join(",", storedProfileHashes.keySet())
                            + "\nChange your configuration to match the graph or delete " + baseGraph.getDirectory().getLocation());
                if (!hash.equals(storedHash))
                    throw new IllegalStateException("Profile '" + profile + "' does not match."
                            + "\nStored: " + storedHash
                            + "\nConfigured: " + hash
                            + "\nChange this profile to match the stored one or delete " + baseGraph.getDirectory().getLocation());
            });
            postProcessing(false);
            directory.loadMMap();
            setFullyLoaded();
            return true;
        } finally {
            if (lock != null)
                lock.release();
        }
    }

    protected int getProfileHash(Profile profile) {
        return profile.getVersion();
    }

    private String getProfilesString() {
        return getProfileHashes().entrySet().stream().map(e -> e.getKey() + "|" + e.getValue()).collect(Collectors.joining(","));
    }

    private Map<String, Integer> getProfileHashes() {
        return profilesByName.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> getProfileHash(e.getValue())));
    }

    public void checkProfilesConsistency() {
        if (profilesByName.isEmpty())
            throw new IllegalArgumentException("There has to be at least one profile");
        for (Profile profile : profilesByName.values()) {
            try {
                createWeighting(profile, new PMap());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not create weighting for profile: '" + profile.getName() + "'.\n" +
                        "Profile: " + profile + "\n" +
                        "Error: " + e.getMessage());
            }

            if (CustomWeighting.NAME.equals(profile.getWeighting()) && profile.getCustomModel() == null)
                throw new IllegalArgumentException("custom model for profile '" + profile.getName() + "' was empty");
            if (!CustomWeighting.NAME.equals(profile.getWeighting()) && profile.getCustomModel() != null)
                throw new IllegalArgumentException("profile '" + profile.getName() + "' has a custom model but " +
                        "weighting=" + profile.getWeighting() + " was defined");
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
            if (profile.hasTurnCosts()) {
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
        calcChecksums();
        initLocationIndex();
        importPublicTransit();

        if (closeEarly) {
            boolean includesCustomProfiles = profilesByName.values().stream().anyMatch(p -> CustomWeighting.NAME.equals(p.getWeighting()));
            if (!includesCustomProfiles)
                // when there are custom profiles we must not close way geometry or KVStorage, because
                // they might be needed to evaluate the custom weightings for the following preparations
                baseGraph.flushAndCloseGeometryAndNameStorage();
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
        if (encodingManager.hasEncodedValue(RoadEnvironment.KEY)) {
            EnumEncodedValue<RoadEnvironment> roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
            StopWatch sw = new StopWatch().start();
            new EdgeElevationInterpolator(baseGraph.getBaseGraph(), roadEnvEnc, RoadEnvironment.TUNNEL).execute();
            float tunnel = sw.stop().getSeconds();
            sw = new StopWatch().start();
            new EdgeElevationInterpolator(baseGraph.getBaseGraph(), roadEnvEnc, RoadEnvironment.BRIDGE).execute();
            float bridge = sw.stop().getSeconds();
            // The SkadiProvider contains bathymetric data. For ferries this can result in bigger elevation changes
            // See #2098 for mor information
            sw = new StopWatch().start();
            new EdgeElevationInterpolator(baseGraph.getBaseGraph(), roadEnvEnc, RoadEnvironment.FERRY).execute();
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
        return new DefaultWeightingFactory(baseGraph.getBaseGraph(), getEncodingManager());
    }

    public GHResponse route(GHRequest request) {
        return createRouter().route(request);
    }

    private Router createRouter() {
        if (baseGraph == null || !fullyLoaded)
            throw new IllegalStateException("Do a successful call to load or importOrLoad before routing");
        if (baseGraph.isClosed())
            throw new IllegalStateException("You need to create a new GraphHopper instance as it is already closed");
        if (locationIndex == null)
            throw new IllegalStateException("Location index not initialized");

        return doCreateRouter(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, createWeightingFactory(), chGraphs, landmarks);
    }

    protected Router doCreateRouter(BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, Map<String, Profile> profilesByName,
                                    PathDetailsBuilderFactory pathBuilderFactory, TranslationMap trMap, RouterConfig routerConfig,
                                    WeightingFactory weightingFactory, Map<String, RoutingCHGraph> chGraphs, Map<String, LandmarkStorage> landmarks) {
        return new Router(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks
        );
    }

    protected LocationIndex createLocationIndex(Directory dir) {
        LocationIndexTree tmpIndex = new LocationIndexTree(baseGraph, dir);
        tmpIndex.setResolution(preciseIndexResolution);
        tmpIndex.setMaxRegionSearch(maxRegionSearch);
        if (!tmpIndex.loadExisting()) {
            ensureWriteAccess();
            tmpIndex.prepareIndex();
        }

        return tmpIndex;
    }

    private void calcChecksums() {
        if (!calcChecksums) return;
        logger.info("Calculating checksums for {} profiles", profilesByName.size());
        StopWatch sw = StopWatch.started();
        double[] checksums_fwd = new double[profilesByName.size()];
        double[] checksums_bwd = new double[profilesByName.size()];
        List<Weighting> weightings = profilesByName.values().stream().map(profile -> createWeighting(profile, new PMap())).toList();
        AllEdgesIterator edge = baseGraph.getAllEdges();
        while (edge.next()) {
            for (int i = 0; i < profilesByName.size(); i++) {
                double weightFwd = weightings.get(i).calcEdgeWeight(edge, false);
                if (Double.isInfinite(weightFwd)) weightFwd = -1;
                weightFwd *= (i % 2 == 0) ? -1 : 1;
                double weightBwd = weightings.get(i).calcEdgeWeight(edge, true);
                if (Double.isInfinite(weightBwd)) weightBwd = -1;
                weightBwd *= (i % 2 == 0) ? -1 : 1;
                checksums_fwd[i] += weightFwd;
                checksums_bwd[i] += weightBwd;
            }
        }
        int index = 0;
        for (Profile profile : profilesByName.values()) {
            properties.put("checksum.fwd." + profile.getName(), checksums_fwd[index]);
            properties.put("checksum.bwd." + profile.getName(), checksums_bwd[index]);
            logger.info("checksum.fwd." + profile.getName() + ": " + checksums_fwd[index]);
            logger.info("checksum.bwd." + profile.getName() + ": " + checksums_bwd[index]);
            index++;
        }
        logger.info("Calculating checksums took: " + sw.stop().getTimeString());
    }

    /**
     * Initializes the location index after the import is done.
     */
    protected void initLocationIndex() {
        if (locationIndex != null)
            throw new IllegalStateException("Cannot initialize locationIndex twice!");

        locationIndex = createLocationIndex(baseGraph.getDirectory());
    }

    private String getCHProfileVersion(String profile) {
        return properties.get("graph.profiles.ch." + profile + ".version");
    }

    private void setCHProfileVersion(String profile, int version) {
        properties.put("graph.profiles.ch." + profile + ".version", version);
    }

    private String getLMProfileVersion(String profile) {
        return properties.get("graph.profiles.lm." + profile + ".version");
    }

    private void setLMProfileVersion(String profile, int version) {
        properties.put("graph.profiles.lm." + profile + ".version", version);
    }

    protected void loadOrPrepareCH(boolean closeEarly) {
        for (CHProfile profile : chPreparationHandler.getCHProfiles())
            if (!getCHProfileVersion(profile.getProfile()).isEmpty()
                    && !getCHProfileVersion(profile.getProfile()).equals("" + getProfileHash(profilesByName.get(profile.getProfile()))))
                throw new IllegalArgumentException("CH preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration");

        // we load ch graphs that already exist and prepare the other ones
        List<CHConfig> chConfigs = createCHConfigs(chPreparationHandler.getCHProfiles());
        Map<String, RoutingCHGraph> loaded = chPreparationHandler.load(baseGraph.getBaseGraph(), chConfigs);
        List<CHConfig> configsToPrepare = chConfigs.stream().filter(c -> !loaded.containsKey(c.getName())).collect(Collectors.toList());
        Map<String, PrepareContractionHierarchies.Result> prepared = prepareCH(closeEarly, configsToPrepare);

        // we map all profile names for which there is CH support to the according CH graphs
        chGraphs = new LinkedHashMap<>();
        for (CHProfile profile : chPreparationHandler.getCHProfiles()) {
            if (loaded.containsKey(profile.getProfile()) && prepared.containsKey(profile.getProfile()))
                throw new IllegalStateException("CH graph should be either loaded or prepared, but not both: " + profile.getProfile());
            else if (prepared.containsKey(profile.getProfile())) {
                setCHProfileVersion(profile.getProfile(), getProfileHash(profilesByName.get(profile.getProfile())));
                PrepareContractionHierarchies.Result res = prepared.get(profile.getProfile());
                chGraphs.put(profile.getProfile(), RoutingCHGraphImpl.fromGraph(baseGraph.getBaseGraph(), res.getCHStorage(), res.getCHConfig()));
            } else if (loaded.containsKey(profile.getProfile())) {
                chGraphs.put(profile.getProfile(), loaded.get(profile.getProfile()));
            } else
                throw new IllegalStateException("CH graph should be either loaded or prepared: " + profile.getProfile());
        }
        chGraphs.forEach((name, ch) -> {
            CHStorage store = ((RoutingCHGraphImpl) ch).getCHStorage();
            logger.info("CH available for profile {}, {}MB, {}, ({}MB)", name, Helper.nf(store.getCapacity() / Helper.MB), store.toDetailsString(), store.getMB());
        });
    }

    protected Map<String, PrepareContractionHierarchies.Result> prepareCH(boolean closeEarly, List<CHConfig> configsToPrepare) {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess();
        if (!baseGraph.isFrozen())
            baseGraph.freeze();
        return chPreparationHandler.prepare(baseGraph, properties, configsToPrepare, closeEarly);
    }

    /**
     * For landmarks it is required to always call this method: either it creates the landmark data or it loads it.
     */
    protected void loadOrPrepareLM(boolean closeEarly) {
        for (LMProfile profile : lmPreparationHandler.getLMProfiles())
            if (!getLMProfileVersion(profile.getProfile()).isEmpty()
                    && !getLMProfileVersion(profile.getProfile()).equals("" + getProfileHash(profilesByName.get(profile.getProfile()))))
                throw new IllegalArgumentException("LM preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration");

        // we load landmark storages that already exist and prepare the other ones
        List<LMConfig> lmConfigs = createLMConfigs(lmPreparationHandler.getLMProfiles());
        List<LandmarkStorage> loaded = lmPreparationHandler.load(lmConfigs, baseGraph, encodingManager);
        List<LMConfig> loadedConfigs = loaded.stream().map(LandmarkStorage::getLMConfig).toList();
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
                setLMProfileVersion(lmp.getProfile(), getProfileHash(profilesByName.get(lmp.getProfile())));
                landmarks.put(lmp.getProfile(), preparedLMS.get().getLandmarkStorage());
            } else
                loadedLMS.ifPresent(landmarkStorage -> landmarks.put(lmp.getProfile(), landmarkStorage));
        }
    }

    protected List<PrepareLandmarks> prepareLM(boolean closeEarly, List<LMConfig> configsToPrepare) {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess();
        if (!baseGraph.isFrozen())
            baseGraph.freeze();
        return lmPreparationHandler.prepare(configsToPrepare, baseGraph, encodingManager, properties, locationIndex, closeEarly);
    }

    /**
     * Internal method to clean up the graph.
     */
    protected void cleanUp() {
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(baseGraph.getBaseGraph(), buildSubnetworkRemovalJobs());
        preparation.setMinNetworkSize(minNetworkSize);
        preparation.setThreads(subnetworksThreads);
        preparation.doWork();
        logger.info("nodes: " + Helper.nf(baseGraph.getNodes()) + ", edges: " + Helper.nf(baseGraph.getEdges()));
    }

    private List<PrepareJob> buildSubnetworkRemovalJobs() {
        List<PrepareJob> jobs = new ArrayList<>();
        for (Profile profile : profilesByName.values()) {
            // if turn costs are enabled use u-turn costs of zero as we only want to make sure the graph is fully connected assuming finite u-turn costs
            Weighting weighting = createWeighting(profile, new PMap().putObject(Parameters.Routing.U_TURN_COSTS, 0));
            jobs.add(new PrepareJob(encodingManager.getBooleanEncodedValue(Subnetwork.key(profile.getName())), weighting));
        }
        return jobs;
    }

    protected void flush() {
        logger.info("flushing graph " + getBaseGraphString() + ", details:" + baseGraph.toDetailsString() + ", "
                + getMemInfo() + ")");
        baseGraph.flush();
        properties.flush();
        logger.info("flushed graph " + getMemInfo() + ")");
        setFullyLoaded();
    }

    /**
     * Releases all associated resources like memory or files. But it does not remove them. To
     * remove the files created in graphhopperLocation you have to call clean().
     */
    public void close() {
        if (baseGraph != null)
            baseGraph.close();
        if (properties != null)
            properties.close();

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

    public static JsonFeatureCollection resolveCustomAreas(String customAreasDirectory) {
        JsonFeatureCollection globalAreas = new JsonFeatureCollection();
        if (!customAreasDirectory.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JtsModule());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(customAreasDirectory), "*.{geojson,json}")) {
                StreamSupport.stream(stream.spliterator(), false)
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(customAreaFile -> {
                            try (BufferedReader reader = Files.newBufferedReader(customAreaFile, StandardCharsets.UTF_8)) {
                                globalAreas.getFeatures().addAll(mapper.readValue(reader, JsonFeatureCollection.class).getFeatures());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                logger.info("Will make " + globalAreas.getFeatures().size() + " areas available to all custom profiles. Found in " + customAreasDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return globalAreas;
    }

    public static List<Profile> resolveCustomModelFiles(String customModelFolder, List<Profile> profiles, JsonFeatureCollection globalAreas) {
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : profiles) {
            if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
                newProfiles.add(profile);
                continue;
            }
            Object cm = profile.getHints().getObject(CustomModel.KEY, null);
            CustomModel customModel;
            if (cm != null) {
                if (!profile.getHints().getObject("custom_model_files", Collections.emptyList()).isEmpty())
                    throw new IllegalArgumentException("Do not use custom_model_files and custom_model together");
                try {
                    // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                    customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel.class);
                    newProfiles.add(profile.setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from " + cm + " for profile " + profile.getName()
                            + ". If you are trying to load from a file, use 'custom_model_files' instead.", ex);
                }
            } else {
                if (!profile.getHints().getString("custom_model_file", "").isEmpty())
                    throw new IllegalArgumentException("Since 8.0 you must use a custom_model_files array instead of custom_model_file string");
                List<String> customModelFileNames = profile.getHints().getObject("custom_model_files", null);
                if (customModelFileNames == null)
                    throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_files' field in profile '"
                            + profile.getName() + "'. To use default specify custom_model_files: []");
                if (customModelFileNames.isEmpty()) {
                    newProfiles.add(profile.setCustomModel(customModel = new CustomModel()));
                } else {
                    customModel = new CustomModel();
                    for (String file : customModelFileNames) {
                        if (file.contains(File.separator))
                            throw new IllegalArgumentException("Use custom_models.directory for the custom_model_files parent");
                        if (!file.endsWith(".json"))
                            throw new IllegalArgumentException("Yaml is no longer supported, see #2672. Use JSON with optional comments //");

                        try {
                            String string;
                            // 1. try to load custom model from jar
                            InputStream is = GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + file);
                            // dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
                            Path customModelFile = Paths.get(customModelFolder).resolve(file);
                            if (is != null) {
                                if (Files.exists(customModelFile))
                                    throw new RuntimeException("Custom model file name '" + file + "' is already used for built-in profiles. Use another name");
                                string = readJSONFileWithoutComments(new InputStreamReader(is));
                            } else {
                                // 2. try to load custom model file from external location
                                string = readJSONFileWithoutComments(customModelFile.toFile().getAbsolutePath());
                            }
                            customModel = CustomModel.merge(customModel, jsonOM.readValue(string, CustomModel.class));
                        } catch (IOException ex) {
                            throw new RuntimeException("Cannot load custom_model from location " + file + ", profile:" + profile.getName(), ex);
                        }
                    }

                    newProfiles.add(profile.setCustomModel(customModel));
                }
            }

            // we can fill in all areas here as in the created template we include only the areas that are used in
            // statements (see CustomModelParser)
            customModel.addAreas(globalAreas);
        }
        return newProfiles;
    }
}
