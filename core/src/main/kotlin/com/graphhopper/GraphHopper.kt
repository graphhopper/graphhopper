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
package com.graphhopper

import com.bedatadriven.jackson.datatype.jts.JtsModule
import com.graphhopper.coll.GrowableBitSet
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.LongArrayList
import com.carrotsearch.hppc.sorting.IndirectComparator
import com.carrotsearch.hppc.sorting.IndirectSort
import com.fasterxml.jackson.databind.ObjectMapper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.LMProfile
import com.graphhopper.config.Profile
import com.graphhopper.jackson.Jackson
import com.graphhopper.reader.dem.*
import com.graphhopper.reader.osm.OSMReader
import com.graphhopper.reader.osm.RestrictionTagParser
import com.graphhopper.routing.*
import com.graphhopper.routing.ch.CHPreparationHandler
import com.graphhopper.routing.ch.PrepareContractionHierarchies
import com.graphhopper.routing.ev.*
import com.graphhopper.routing.lm.LMConfig
import com.graphhopper.routing.lm.LMPreparationHandler
import com.graphhopper.routing.lm.LandmarkStorage
import com.graphhopper.routing.lm.PrepareLandmarks
import com.graphhopper.routing.subnetwork.EdgeBasedTarjanSCC
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks.PrepareJob
import com.graphhopper.routing.util.*
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser
import com.graphhopper.routing.util.parsers.OSMFootNetworkTagParser
import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.routing.weighting.custom.CustomModelParser
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.routing.weighting.custom.NameValidator
import com.graphhopper.storage.*
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.storage.index.LocationIndexTree
import com.graphhopper.util.*
import com.graphhopper.util.Helper.*
import com.graphhopper.util.Parameters.Landmark
import com.graphhopper.util.Parameters.Routing
import com.graphhopper.util.Parameters.Algorithms.RoundTrip
import com.graphhopper.util.details.PathDetailsBuilderFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.StreamSupport

/**
 * Easy to use access point to configure import and (offline) routing.
 *
 * @author Peter Karich
 */
open class GraphHopper {
    private var maxSpeedCalculator: MaxSpeedCalculator? = null
    private val profilesByName = LinkedHashMap<String, Profile>()
    private val fileLockName = "gh.lock"

    // utils
    private val trMap = TranslationMap().doImport()
    private var removeZipped = true
    private var calcChecksums = false
    private var skipProfileMatchCheck = false

    // for custom areas:
    private var customAreasDirectory = ""

    // for graph:
    private var baseGraph: BaseGraph? = null
    private var properties: StorableProperties? = null

    @JvmField
    protected var encodingManager: EncodingManager? = null
    private var osmParsers: OSMParsers? = null
    private var defaultSegmentSize = AbstractDataAccess.SEGMENT_SIZE_DEFAULT
    private var ghLocation = ""
    private var dataAccessDefaultType = DAType.RAM_STORE
    private val dataAccessConfig = LinkedHashMap<String, String>()
    private var sortGraph = true
    private var elevation = false
    private var lockFactory: LockFactory = NativeFSLockFactory()
    private var allowWrites = true
    private var fullyLoaded = false
    private val osmReaderConfig = OSMReaderConfig()

    // for routing
    private val routerConfig = RouterConfig()

    // for index
    private var locationIndex: LocationIndex? = null
    private var preciseIndexResolution = 300
    private var maxRegionSearch = 4

    // subnetworks
    private var minNetworkSize = 200
    private var subnetworksThreads = 1

    // residential areas
    private var residentialAreaRadius = 400.0
    private var residentialAreaSensitivity = 6000.0
    private var cityAreaRadius = 1500.0
    private var cityAreaSensitivity = 1000.0
    private var urbanDensityCalculationThreads = 0

    // preparation handlers
    private val lmPreparationHandler = LMPreparationHandler()
    private val chPreparationHandler = CHPreparationHandler()
    private var chGraphs: Map<String, RoutingCHGraph> = Collections.emptyMap()
    private var landmarks: Map<String, LandmarkStorage> = Collections.emptyMap()

    // for data reader
    private var osmFile: String? = null
    private var eleProvider: ElevationProvider = ElevationProvider.NOOP
    private var importRegistry: ImportRegistry = DefaultImportRegistry()
    private var pathBuilderFactory = PathDetailsBuilderFactory()

    private var dateRangeParserString = ""
    private var encodedValuesString = ""

    open fun setEncodedValuesString(encodedValuesString: String): GraphHopper {
        this.encodedValuesString = encodedValuesString
        return this
    }

    open fun getEncodedValuesString(): String {
        return encodedValuesString
    }

    open fun getEncodingManager(): EncodingManager {
        return encodingManager
                ?: throw IllegalStateException("EncodingManager not yet built")
    }

    open fun getOSMParsers(): OSMParsers {
        return osmParsers
                ?: throw IllegalStateException("OSMParsers not yet built")
    }

    open fun getElevationProvider(): ElevationProvider {
        return eleProvider
    }

    open fun setElevationProvider(eleProvider: ElevationProvider?): GraphHopper {
        if (eleProvider == null || eleProvider === ElevationProvider.NOOP)
            setElevation(false)
        else
            setElevation(true)
        this.eleProvider = eleProvider ?: ElevationProvider.NOOP
        return this
    }

    open fun setPathDetailsBuilderFactory(pathBuilderFactory: PathDetailsBuilderFactory): GraphHopper {
        this.pathBuilderFactory = pathBuilderFactory
        return this
    }

    open fun getPathDetailsBuilderFactory(): PathDetailsBuilderFactory {
        return pathBuilderFactory
    }

    /**
     * Precise location resolution index means also more space (disc/RAM) could be consumed and
     * probably slower query times, which would be e.g. not suitable for Android. The resolution
     * specifies the tile width (in meter).
     */
    open fun setPreciseIndexResolution(precision: Int): GraphHopper {
        ensureNotLoaded()
        preciseIndexResolution = precision
        return this
    }

    open fun setMinNetworkSize(minNetworkSize: Int): GraphHopper {
        ensureNotLoaded()
        this.minNetworkSize = minNetworkSize
        return this
    }

    /**
     * Configures the urban density classification. Each edge will be classified as 'rural','residential' or 'city', [UrbanDensity]
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
    open fun setUrbanDensityCalculation(residentialAreaRadius: Double, residentialAreaSensitivity: Double,
                                        cityAreaRadius: Double, cityAreaSensitivity: Double, threads: Int): GraphHopper {
        ensureNotLoaded()
        this.residentialAreaRadius = residentialAreaRadius
        this.residentialAreaSensitivity = residentialAreaSensitivity
        this.cityAreaRadius = cityAreaRadius
        this.cityAreaSensitivity = cityAreaSensitivity
        this.urbanDensityCalculationThreads = threads
        return this
    }

    /**
     * Only valid option for in-memory graph and if you e.g. want to disable store on flush for unit
     * tests. Specify storeOnFlush to true if you want that existing data will be loaded FROM disc
     * and all in-memory data will be flushed TO disc after flush is called e.g. while OSM import.
     *
     * @param storeOnFlush true by default
     */
    open fun setStoreOnFlush(storeOnFlush: Boolean): GraphHopper {
        ensureNotLoaded()
        if (storeOnFlush)
            dataAccessDefaultType = DAType.RAM_STORE
        else
            dataAccessDefaultType = DAType.RAM
        return this
    }

    /**
     * Sets the routing profiles that shall be supported by this GraphHopper instance. The (and only the) given profiles
     * can be used for routing without preparation and for CH/LM preparation.
     *
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
     *
     * See also https://github.com/graphhopper/graphhopper/pull/1922.
     *
     * @see CHPreparationHandler.setCHProfiles
     * @see LMPreparationHandler.setLMProfiles
     */
    open fun setProfiles(vararg profiles: Profile): GraphHopper {
        return setProfiles(listOf(*profiles))
    }

    open fun setProfiles(profiles: List<Profile>): GraphHopper {
        if (!profilesByName.isEmpty())
            throw IllegalArgumentException("Cannot initialize profiles multiple times")
        if (encodingManager != null)
            throw IllegalArgumentException("Cannot set profiles after EncodingManager was built")
        for (profile in profiles) {
            val previous = this.profilesByName.put(profile.getName()!!, profile)
            if (previous != null)
                throw IllegalArgumentException("Profile names must be unique. Duplicate name: '" + profile.getName() + "'")
        }
        return this
    }

    open fun getProfiles(): List<Profile> {
        return ArrayList(profilesByName.values)
    }

    /**
     * Returns the profile for the given profile name, or null if it does not exist
     */
    open fun getProfile(profileName: String): Profile? {
        return profilesByName[profileName]
    }

    open fun getNavigationMode(profileName: String): TransportationMode {
        val profile = profilesByName[profileName] ?: return TransportationMode.CAR
        return try {
            TransportationMode.valueOf(profile.getHints().getString("navigation_mode", profileName).uppercase(Locale.ROOT))
        } catch (e: IllegalArgumentException) {
            TransportationMode.CAR
        }
    }

    /**
     * @return true if storing and fetching elevation data is enabled. Default is false
     */
    open fun hasElevation(): Boolean {
        return elevation
    }

    /**
     * Enable storing and fetching elevation data. Default is false
     */
    open fun setElevation(includeElevation: Boolean): GraphHopper {
        this.elevation = includeElevation
        return this
    }

    open fun getGraphHopperLocation(): String {
        return ghLocation
    }

    /**
     * Sets the graphhopper folder.
     */
    open fun setGraphHopperLocation(ghLocation: String?): GraphHopper {
        ensureNotLoaded()
        if (ghLocation == null)
            throw IllegalArgumentException("graphhopper location cannot be null")

        this.ghLocation = ghLocation
        return this
    }

    open fun getOSMFile(): String? {
        return osmFile
    }

    /**
     * This file can be an osm xml (.osm), a compressed xml (.osm.zip or .osm.gz) or a protobuf file
     * (.pbf).
     */
    open fun setOSMFile(osmFile: String?): GraphHopper {
        ensureNotLoaded()
        if (isEmpty(osmFile))
            throw IllegalArgumentException("OSM file cannot be empty.")

        this.osmFile = osmFile
        return this
    }

    open fun setMaxSpeedCalculator(maxSpeedCalculator: MaxSpeedCalculator): GraphHopper {
        this.maxSpeedCalculator = maxSpeedCalculator
        return this
    }

    open fun setSortGraph(sortGraph: Boolean): GraphHopper {
        this.sortGraph = sortGraph
        return this
    }

    /**
     * The underlying graph used in algorithms.
     *
     * @throws IllegalStateException if graph is not instantiated.
     */
    open fun getBaseGraph(): BaseGraph {
        return baseGraph
                ?: throw IllegalStateException("GraphHopper storage not initialized")
    }

    open fun setBaseGraph(baseGraph: BaseGraph) {
        this.baseGraph = baseGraph
        setFullyLoaded()
    }

    open fun getProperties(): StorableProperties? {
        return properties
    }

    /**
     * @return a mapping between profile names and according CH preparations. The map will be empty before loading
     * or import.
     */
    open fun getCHGraphs(): Map<String, RoutingCHGraph> {
        return chGraphs
    }

    /**
     * @return a mapping between profile names and according landmark preparations. The map will be empty before loading
     * or import.
     */
    open fun getLandmarks(): Map<String, LandmarkStorage> {
        return landmarks
    }

    /**
     * The location index created from the graph.
     *
     * @throws IllegalStateException if index is not initialized
     */
    open fun getLocationIndex(): LocationIndex {
        return locationIndex
                ?: throw IllegalStateException("LocationIndex not initialized")
    }

    protected open fun setLocationIndex(locationIndex: LocationIndex) {
        this.locationIndex = locationIndex
    }

    open fun isAllowWrites(): Boolean {
        return allowWrites
    }

    /**
     * Specifies if it is allowed for GraphHopper to write. E.g. for read only filesystems it is not
     * possible to create a lock file and so we can avoid write locks.
     */
    open fun setAllowWrites(allowWrites: Boolean): GraphHopper {
        this.allowWrites = allowWrites
        return this
    }

    open fun getTranslationMap(): TranslationMap {
        return trMap
    }

    open fun setImportRegistry(importRegistry: ImportRegistry): GraphHopper {
        this.importRegistry = importRegistry
        return this
    }

    open fun getImportRegistry(): ImportRegistry {
        return importRegistry
    }

    open fun setCustomAreasDirectory(customAreasDirectory: String): GraphHopper {
        this.customAreasDirectory = customAreasDirectory
        return this
    }

    open fun getCustomAreasDirectory(): String {
        return this.customAreasDirectory
    }

    /**
     * Reads the configuration from a [GraphHopperConfig] object which can be manually filled, or more typically
     * is read from `config.yml`.
     *
     * Important note: Calling this method overwrites the configuration done in some of the setter methods of this class,
     * so generally it is advised to either use this method to configure GraphHopper or the different setter methods,
     * but not both. Unfortunately, this still does not cover all cases and sometimes you have to use both, but then you
     * should make sure there are no conflicts. If you need both it might also help to call the init before calling the
     * setters, because this way the init method won't apply defaults to configuration options you already chose using
     * the setters.
     */
    open fun init(ghConfig: GraphHopperConfig): GraphHopper {
        ensureNotLoaded()
        // disabling_allowed config options were removed for GH 3.0
        if (ghConfig.has("routing.ch.disabling_allowed"))
            throw IllegalArgumentException("The 'routing.ch.disabling_allowed' configuration option is no longer supported")
        if (ghConfig.has("routing.lm.disabling_allowed"))
            throw IllegalArgumentException("The 'routing.lm.disabling_allowed' configuration option is no longer supported")
        if (ghConfig.has("osmreader.osm"))
            throw IllegalArgumentException("Instead of osmreader.osm use datareader.file, for other changes see CHANGELOG.md")

        val tmpOsmFile = ghConfig.getString("datareader.file", "")!!
        if (!isEmpty(tmpOsmFile))
            osmFile = tmpOsmFile

        var graphHopperFolder = ghConfig.getString("graph.location", "")!!
        if (isEmpty(graphHopperFolder) && isEmpty(ghLocation)) {
            if (isEmpty(osmFile))
                throw IllegalArgumentException("If no graph.location is provided you need to specify an OSM file.")

            graphHopperFolder = pruneFileEnd(osmFile) + "-gh"
        }
        ghLocation = graphHopperFolder

        customAreasDirectory = ghConfig.getString("custom_areas.directory", customAreasDirectory)!!

        defaultSegmentSize = ghConfig.getInt("graph.dataaccess.segment_size", defaultSegmentSize)

        val daTypeString = ghConfig.getString("graph.dataaccess.default_type", ghConfig.getString("graph.dataaccess", "RAM_STORE"))!!
        dataAccessDefaultType = DAType.fromString(daTypeString)
        for ((key, value) in ghConfig.asPMap().toMap()) {
            if (key.startsWith("graph.dataaccess.type."))
                dataAccessConfig.put(key.substring("graph.dataaccess.type.".length), value.toString())
            if (key.startsWith("graph.dataaccess.mmap.preload."))
                dataAccessConfig.put(key.substring("graph.dataaccess.mmap.".length), value.toString())
        }

        sortGraph = ghConfig.getBool("graph.sort", sortGraph)
        if (ghConfig.getBool("max_speed_calculator.enabled", false))
            maxSpeedCalculator = MaxSpeedCalculator(MaxSpeedCalculator.createLegalDefaultSpeeds())

        removeZipped = ghConfig.getBool("graph.remove_zipped", removeZipped)

        if (!ghConfig.getString("spatial_rules.location", "")!!.isEmpty())
            throw IllegalArgumentException("spatial_rules.location has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.")
        if (!ghConfig.getString("spatial_rules.borders_directory", "")!!.isEmpty())
            throw IllegalArgumentException("spatial_rules.borders_directory has been deprecated. Please use custom_areas.directory instead and read the documentation for custom areas.")
        // todo: maybe introduce custom_areas.max_bbox if this is needed later
        if (!ghConfig.getString("spatial_rules.max_bbox", "")!!.isEmpty())
            throw IllegalArgumentException("spatial_rules.max_bbox has been deprecated. There is no replacement, all custom areas will be considered.")

        val customAreasDirectory = ghConfig.getString("custom_areas.directory", "")!!
        val globalAreas = resolveCustomAreas(customAreasDirectory)
        val customModelFolder = ghConfig.getString("custom_models.directory", ghConfig.getString("custom_model_folder", ""))!!
        setProfiles(resolveCustomModelFiles(customModelFolder, ghConfig.getProfiles(), globalAreas))

        if (ghConfig.has("graph.vehicles"))
            throw IllegalArgumentException("The option graph.vehicles is no longer supported. Use the appropriate turn_costs and custom_model instead, see docs/migration/config-migration-08-09.md")
        if (ghConfig.has("graph.flag_encoders"))
            throw IllegalArgumentException("The option graph.flag_encoders is no longer supported.")

        encodedValuesString = ghConfig.getString("graph.encoded_values", encodedValuesString)!!
        dateRangeParserString = ghConfig.getString("datareader.date_range_parser_day", dateRangeParserString)!!

        if (ghConfig.getString("graph.locktype", "native") == "simple")
            lockFactory = SimpleFSLockFactory()
        else
            lockFactory = NativeFSLockFactory()

        // elevation
        if (ghConfig.has("graph.elevation.smoothing"))
            throw IllegalArgumentException("Use 'graph.elevation.edge_smoothing: moving_average' or the new 'graph.elevation.edge_smoothing: ramer'. See #2634.")
        osmReaderConfig.setElevationSmoothing(ghConfig.getString("graph.elevation.edge_smoothing", osmReaderConfig.getElevationSmoothing())!!)
        osmReaderConfig.setSmoothElevationAverageWindowSize(ghConfig.getDouble("graph.elevation.edge_smoothing.moving_average.window_size", osmReaderConfig.getSmoothElevationAverageWindowSize()))
        osmReaderConfig.setElevationSmoothingRamerMax(ghConfig.getInt("graph.elevation.edge_smoothing.ramer.max_elevation", osmReaderConfig.getElevationSmoothingRamerMax()))
        osmReaderConfig.setLongEdgeSamplingDistance(ghConfig.getDouble("graph.elevation.long_edge_sampling_distance", osmReaderConfig.getLongEdgeSamplingDistance()))
        osmReaderConfig.setElevationMaxWayPointDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", osmReaderConfig.getElevationMaxWayPointDistance()))
        routerConfig.setElevationWayPointMaxDistance(ghConfig.getDouble("graph.elevation.way_point_max_distance", routerConfig.getElevationWayPointMaxDistance()))
        val elevationProvider = createElevationProvider(ghConfig)
        setElevationProvider(elevationProvider)

        if (osmReaderConfig.getLongEdgeSamplingDistance() < Double.MAX_VALUE && !elevationProvider.canInterpolate())
            logger.warn("Long edge sampling enabled, but bilinear interpolation disabled. See #1953")

        // optimizable prepare
        minNetworkSize = ghConfig.getInt("prepare.min_network_size", minNetworkSize)
        subnetworksThreads = ghConfig.getInt("prepare.subnetworks.threads", subnetworksThreads)

        // prepare CH&LM
        chPreparationHandler.init(ghConfig)
        lmPreparationHandler.init(ghConfig)

        // osm import
        // We do a few checks for import.osm.ignored_highways to prevent configuration errors when migrating from an older
        // GH version.
        if (!ghConfig.has("import.osm.ignored_highways"))
            throw IllegalArgumentException("Missing 'import.osm.ignored_highways'. Not using this parameter can decrease performance, see config-example.yml for more details")
        val ignoredHighwaysString = ghConfig.getString("import.osm.ignored_highways", "")!!
        if ((ignoredHighwaysString.contains("footway") || ignoredHighwaysString.contains("path")) && ghConfig.getProfiles().stream().map { p -> p.getName()!! }.anyMatch { p -> p.contains("foot") || p.contains("hike") })
            throw IllegalArgumentException("You should not use import.osm.ignored_highways=footway or =path in conjunction with pedestrian profiles. This is probably an error in your configuration.")
        if ((ignoredHighwaysString.contains("cycleway") || ignoredHighwaysString.contains("path")) && ghConfig.getProfiles().stream().map { p -> p.getName()!! }.anyMatch { p -> p.contains("mtb") || p.contains("bike") })
            throw IllegalArgumentException("You should not use import.osm.ignored_highways=cycleway or =path in conjunction with bicycle profiles. This is probably an error in your configuration")

        osmReaderConfig.setIgnoredHighways(Arrays.stream(ghConfig.getString("import.osm.ignored_highways", java.lang.String.join(",", osmReaderConfig.getIgnoredHighways()))!!
                .split(",".toRegex()).toTypedArray()).map { obj -> obj.trim() }.collect(Collectors.toList()))
        osmReaderConfig.setParseWayNames(ghConfig.getBool("datareader.instructions", osmReaderConfig.isParseWayNames()))
        osmReaderConfig.setPreferredLanguage(ghConfig.getString("datareader.preferred_language", osmReaderConfig.getPreferredLanguage())!!)
        osmReaderConfig.setMaxWayPointDistance(ghConfig.getDouble(Routing.INIT_WAY_POINT_MAX_DISTANCE, osmReaderConfig.getMaxWayPointDistance()))
        osmReaderConfig.setWorkerThreads(ghConfig.getInt("datareader.worker_threads", osmReaderConfig.getWorkerThreads()))

        // index
        preciseIndexResolution = ghConfig.getInt("index.high_resolution", preciseIndexResolution)
        maxRegionSearch = ghConfig.getInt("index.max_region_search", maxRegionSearch)

        // urban density calculation
        residentialAreaRadius = ghConfig.getDouble("graph.urban_density.residential_radius", residentialAreaRadius)
        residentialAreaSensitivity = ghConfig.getDouble("graph.urban_density.residential_sensitivity", residentialAreaSensitivity)
        cityAreaRadius = ghConfig.getDouble("graph.urban_density.city_radius", cityAreaRadius)
        cityAreaSensitivity = ghConfig.getDouble("graph.urban_density.city_sensitivity", cityAreaSensitivity)
        urbanDensityCalculationThreads = ghConfig.getInt("graph.urban_density.threads", urbanDensityCalculationThreads)

        // routing
        routerConfig.setMaxVisitedNodes(ghConfig.getInt(Routing.INIT_MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes()))
        routerConfig.setTimeoutMillis(ghConfig.getLong(Routing.INIT_TIMEOUT_MS, routerConfig.getTimeoutMillis()))
        routerConfig.setMaxRoundTripRetries(ghConfig.getInt(RoundTrip.INIT_MAX_RETRIES, routerConfig.getMaxRoundTripRetries()))
        routerConfig.setNonChMaxWaypointDistance(ghConfig.getInt(Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE, routerConfig.getNonChMaxWaypointDistance()))
        routerConfig.setInstructionsEnabled(ghConfig.getBool(Routing.INIT_INSTRUCTIONS, routerConfig.isInstructionsEnabled()))
        val activeLandmarkCount = ghConfig.getInt(Landmark.ACTIVE_COUNT_DEFAULT, Math.min(8, lmPreparationHandler.getLandmarks()))
        if (activeLandmarkCount > lmPreparationHandler.getLandmarks())
            throw IllegalArgumentException("Default value for active landmarks " + activeLandmarkCount
                    + " should be less or equal to landmark count of " + lmPreparationHandler.getLandmarks())
        routerConfig.setActiveLandmarkCount(activeLandmarkCount)

        calcChecksums = ghConfig.getBool("graph.calc_checksums", false)
        skipProfileMatchCheck = ghConfig.getBool("graph.skip_profile_match_check", false)

        return this
    }

    protected open fun buildEncodingManager(encodedValuesWithProps: Map<String, PMap>,
                                            activeImportUnits: Map<String, ImportUnit>,
                                            restrictionVehicleTypesByProfile: Map<String, List<String>>): EncodingManager {
        val encodedValues: MutableList<EncodedValue> = ArrayList(activeImportUnits.entries.stream()
                .map { e ->
                    val f = e.value.createEncodedValue
                    if (f == null) null else f.apply(encodedValuesWithProps.getOrDefault(e.key, PMap()))
                }
                .filter { obj -> Objects.nonNull(obj) }
                .map { it!! }
                .toList())

        encodedValues.addAll(createSubnetworkEncodedValues())

        val sortedEVs = getEVSortIndex(profilesByName)
        encodedValues.sortWith(Comparator.comparingInt { ev: EncodedValue -> sortedEVs.indexOf(ev.name) })

        val emBuilder = EncodingManager.Builder()
        encodedValues.forEach { encodedValue -> emBuilder.add(encodedValue) }
        restrictionVehicleTypesByProfile.entries.stream()
                .filter { e -> !e.value.isEmpty() }
                .forEach { e -> emBuilder.addTurnCostEncodedValue(TurnRestriction.create(e.key)) }
        return emBuilder.build()
    }

    protected open fun createSubnetworkEncodedValues(): List<BooleanEncodedValue> {
        return profilesByName.values.stream().map { profile -> Subnetwork.create(profile.getName()!!) }.toList()
    }

    protected open fun getEVSortIndex(profilesByName: Map<String, Profile>): List<String> {
        return Collections.emptyList()
    }

    protected open fun buildOSMParsers(encodedValuesWithProps: Map<String, PMap>,
                                       activeImportUnits: Map<String, ImportUnit>,
                                       restrictionVehicleTypesByProfile: Map<String, List<String>>,
                                       ignoredHighways: List<String>): OSMParsers {
        val sorter = ImportUnitSorter(activeImportUnits)
        val sortedImportUnits = LinkedHashMap<String, ImportUnit>()
        sorter.sort().forEach { name -> sortedImportUnits.put(name, activeImportUnits[name]!!) }
        val sortedParsers: MutableList<TagParser> = ArrayList()
        sortedImportUnits.forEach { (name, importUnit) ->
            val createTagParser = importUnit.createTagParser
            if (createTagParser != null) {
                val pmap = encodedValuesWithProps.getOrDefault(name, PMap())
                if (!pmap.has("date_range_parser_day"))
                    pmap.putObject("date_range_parser_day", dateRangeParserString)
                sortedParsers.add(createTagParser.apply(getEncodingManager(), pmap))
            }
        }

        val osmParsers = OSMParsers()
        ignoredHighways.forEach { ignoredHighway -> osmParsers.addIgnoredHighway(ignoredHighway) }
        sortedParsers.forEach { tagParser -> osmParsers.addWayTagParser(tagParser) }

        val maxSpeedCalculator = this.maxSpeedCalculator
        if (maxSpeedCalculator != null) {
            maxSpeedCalculator.checkEncodedValues(getEncodingManager())
            osmParsers.addWayTagParser(maxSpeedCalculator.getParser())
        }

        if (getEncodingManager().hasEncodedValue(BikeNetwork.KEY))
            osmParsers.addRelationTagParser { relConfig -> OSMBikeNetworkTagParser(getEncodingManager().getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork::class.java), relConfig, "bicycle") }
        if (getEncodingManager().hasEncodedValue(MtbNetwork.KEY))
            osmParsers.addRelationTagParser { relConfig -> OSMBikeNetworkTagParser(getEncodingManager().getEnumEncodedValue(MtbNetwork.KEY, RouteNetwork::class.java), relConfig, "mtb") }
        if (getEncodingManager().hasEncodedValue(FootNetwork.KEY))
            osmParsers.addRelationTagParser { relConfig -> OSMFootNetworkTagParser(getEncodingManager().getEnumEncodedValue(FootNetwork.KEY, RouteNetwork::class.java), relConfig) }

        restrictionVehicleTypesByProfile.forEach { (profile, restrictionVehicleTypes) ->
            osmParsers.addRestrictionTagParser(RestrictionTagParser(
                    restrictionVehicleTypes, getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key(profile))))
        }
        return osmParsers
    }

    private fun printInfo() {
        logger.info("version " + Constants.VERSION + "|" + Constants.BUILD_DATE + " (" + Constants.getVersions() + ")")
        if (baseGraph != null)
            logger.info("graph " + getBaseGraphString() + ", details:" + baseGraph!!.toDetailsString())
    }

    private fun getBaseGraphString(): String {
        return (encodingManager.toString()
                + "|" + baseGraph!!.directory.defaultType
                + "|" + baseGraph!!.nodeAccess.dimension + "D"
                + "|" + (if (baseGraph!!.turnCostStorage != null) baseGraph!!.turnCostStorage else "no_turn_cost")
                + "|" + getVersionsString())
    }

    private fun getVersionsString(): String {
        return "nodes:" + Constants.VERSION_NODE +
                ",edges:" + Constants.VERSION_EDGE +
                ",geometry:" + Constants.VERSION_GEOMETRY +
                ",location_index:" + Constants.VERSION_LOCATION_IDX +
                ",string_index:" + Constants.VERSION_KV_STORAGE +
                ",nodesCH:" + Constants.VERSION_NODE_CH +
                ",shortcuts:" + Constants.VERSION_SHORTCUT
    }

    /**
     * Imports provided data from disc and creates graph. Depending on the settings the resulting
     * graph will be stored to disc so on a second call this method will only load the graph from
     * disc which is usually a lot faster.
     */
    open fun importOrLoad(): GraphHopper {
        if (!load()) {
            printInfo()
            process(false)
        } else {
            printInfo()
        }
        return this
    }

    /**
     * Imports and processes data, storing it to disk when complete.
     */
    open fun importAndClose() {
        if (!load()) {
            printInfo()
            process(true)
        } else {
            printInfo()
            logger.info("Graph already imported into $ghLocation")
        }
        close()
    }

    /**
     * Creates the graph from OSM data.
     */
    protected open fun process(closeEarly: Boolean) {
        prepareImport()
        if (encodingManager == null)
            throw IllegalStateException("The EncodingManager must be created in `prepareImport()`")
        val directory = GHDirectory(ghLocation, dataAccessDefaultType, defaultSegmentSize)
        directory.configure(dataAccessConfig)
        baseGraph = BaseGraph.Builder(getEncodingManager())
                .setDir(directory)
                .set3D(hasElevation())
                .withTurnCosts(getEncodingManager().needsTurnCostsSupport())
                .build()
        properties = StorableProperties(directory)
        checkProfilesConsistency()

        var lock: GHLock? = null
        try {
            if (directory.defaultType.isStoring) {
                lockFactory.setLockDir(File(ghLocation))
                lock = lockFactory.create(fileLockName, true)
                if (!lock.tryLock())
                    throw RuntimeException("To avoid multiple writers we need to obtain a write lock but it failed. In $ghLocation", lock.getObtainFailedReason())
            }
            ensureWriteAccess()

            importOSM()
            postImportOSM()
            cleanUp()

            properties!!.put("profiles", getProfilesString())
            writeEncodingManagerToProperties()

            postProcessing(closeEarly)
            flush()
        } finally {
            lock?.release()
        }
    }

    protected open fun prepareImport() {
        val encodedValuesWithProps = parseEncodedValueString(encodedValuesString)
        val nameValidator = NameValidator { s -> importRegistry.createImportUnit(s) != null }
        val missing = LinkedHashSet<String>()
        profilesByName.values
                .forEach { profile ->
                    CustomModelParser.findVariablesForEncodedValuesString(profile.getCustomModel()!!, nameValidator) { s -> "" }
                            .forEach { variable ->
                                if (!encodedValuesWithProps.containsKey(variable)) missing.add(variable)
                                encodedValuesWithProps.putIfAbsent(variable, PMap())
                            }
                }
        if (!missing.isEmpty()) {
            val encodedValuesString = encodedValuesWithProps.entries.stream()
                    .map { e -> e.key + (if (e.value.isEmpty) "" else ("|" + e.value.toMap().entries.stream().map { p -> p.key + "=" + p.value }.collect(Collectors.joining("|")))) }
                    .collect(Collectors.joining(", "))
            throw IllegalArgumentException("Encoded values missing: " + java.lang.String.join(", ", missing) + ".\n" +
                    "To avoid that certain encoded values are automatically removed when you change the custom model later, you need to set the encoded values manually:\n" +
                    "graph.encoded_values: " + encodedValuesString)
        }

        // following encoded values are used by instructions and in the snap prevention filter (avoid motorway, tunnel, etc.)
        encodedValuesWithProps.putIfAbsent(RoadClass.KEY, PMap())
        encodedValuesWithProps.putIfAbsent(RoadEnvironment.KEY, PMap())
        // now only used by instructions:
        encodedValuesWithProps.putIfAbsent(Roundabout.KEY, PMap())
        encodedValuesWithProps.putIfAbsent(VehicleAccess.key("car"), PMap())
        encodedValuesWithProps.putIfAbsent(RoadClassLink.KEY, PMap())
        encodedValuesWithProps.putIfAbsent(MaxSpeed.KEY, PMap())

        val restrictionVehicleTypesByProfile = getRestrictionVehicleTypesByProfile(profilesByName.values)

        if (urbanDensityCalculationThreads > 0)
            encodedValuesWithProps.put(UrbanDensity.KEY, PMap())
        if (maxSpeedCalculator != null) {
            if (urbanDensityCalculationThreads <= 0)
                throw IllegalArgumentException("For max_speed_calculator the urban density calculation needs to be enabled (e.g. graph.urban_density.threads: 1)")
            encodedValuesWithProps.put(MaxSpeedEstimated.KEY, PMap())
        }

        val activeImportUnits = LinkedHashMap<String, ImportUnit>()
        val deque = java.util.ArrayDeque(encodedValuesWithProps.keys)
        while (!deque.isEmpty()) {
            val ev = deque.removeFirst()
            val importUnit = importRegistry.createImportUnit(ev)
                    ?: throw IllegalArgumentException("Unknown encoded value: $ev")
            if (activeImportUnits.put(ev, importUnit) == null)
                deque.addAll(importUnit.requiredImportUnits)
        }
        encodingManager = buildEncodingManager(encodedValuesWithProps, activeImportUnits, restrictionVehicleTypesByProfile)
        osmParsers = buildOSMParsers(encodedValuesWithProps, activeImportUnits, restrictionVehicleTypesByProfile, osmReaderConfig.getIgnoredHighways())
    }

    protected open fun postImportOSM() {
        // Important note: To deal with via-way turn restrictions we introduce artificial edges in OSMReader (#2689).
        // These are simply copies of real edges. Any further modifications of the graph edges must take care of keeping
        // the artificial edges in sync with their real counterparts. So if an edge attribute shall be changed this change
        // must also be applied to the corresponding artificial edge.
        calculateUrbanDensity()

        calculateSoftblocks()

        val maxSpeedCalculator = this.maxSpeedCalculator
        if (maxSpeedCalculator != null) {
            maxSpeedCalculator.fillMaxSpeed(getBaseGraph(), getEncodingManager())
            maxSpeedCalculator.close()
        }

        if (hasElevation())
            interpolateBridgesTunnelsAndFerries()

        calculateSlope()

        if (getEncodingManager().hasEncodedValue(Curvature.KEY))
            CurvatureCalculator(getEncodingManager().getDecimalEncodedValue(Curvature.KEY)).execute(baseGraph!!.baseGraph)

        if (sortGraph)
            sortGraphAlongHilbertCurve(baseGraph!!)
    }

    private fun calculateSoftblocks() {
        if (getEncodingManager().hasEncodedValue(IsSoftblockedAtEntry.KEY) && getEncodingManager().hasEncodedValue(RoadAccess.KEY) && getEncodingManager().hasEncodedValue(VehicleAccess.key("car"))) {
            calculateSoftblocks1(RoadAccess.DELIVERY)
            calculateSoftblocks1(RoadAccess.DESTINATION)
        }
    }

    private fun calculateSoftblocks1(access: RoadAccess) {
        val isSoftblockedAtEntry = getEncodingManager().getBooleanEncodedValue(IsSoftblockedAtEntry.KEY)
        val roadAccess = getEncodingManager().getEnumEncodedValue(RoadAccess.KEY, RoadAccess::class.java)
        val carAccess = getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"))

        logger.info("Setting {}-captured edges to {}", access, access)
        EdgeBasedTarjanSCC.findComponentsStreaming(baseGraph!!,
                { prevEdge, edgeState -> edgeState.get(roadAccess) != access },
                object : EdgeBasedTarjanSCC.SCCConsumer {
                    var buffer: IntArrayList? = null
                    var overflowed = false
                    var currentSize = 0

                    override fun beginComponent() {
                        buffer = IntArrayList()
                        overflowed = false
                        currentSize = 0
                    }

                    override fun edgeKey(key: Int) {
                        currentSize++
                        if (!overflowed) {
                            buffer!!.add(key)
                            if (buffer!!.size() >= 100) {
                                buffer = null
                                overflowed = true
                            }
                        }
                    }

                    override fun endComponent() {
                        logger.info("Size {} component", currentSize)
                        val buffer = this.buffer
                        if (buffer != null) {
                            for (edgeKey in buffer) {
                                val edge = baseGraph!!.getEdgeIteratorStateForKey((edgeKey.value / 2) * 2)
                                edge.set(roadAccess, access)
                            }
                        }
                        this.buffer = null
                    }

                    override fun singleEdgeComponent(key: Int) {
                        val edge = baseGraph!!.getEdgeIteratorStateForKey((key / 2) * 2)
                        edge.set(roadAccess, access)
                    }
                })

        logger.info("Softblocking {} edges by setting an entry penalty bit", access)
        val allEdges = baseGraph!!.allEdges
        while (allEdges.next()) {
            if (allEdges.get(roadAccess) == access) {
                val edgeExplorer = baseGraph!!.createEdgeExplorer()
                var edgeIterator = edgeExplorer.setBaseNode(allEdges.baseNode)
                val fwdNeedsSoftblockAtEntry = needsSoftblock(edgeIterator, roadAccess, carAccess)
                edgeIterator = edgeExplorer.setBaseNode(allEdges.adjNode)
                val bwdNeedsSoftblockAtEntry = needsSoftblock(edgeIterator, roadAccess, carAccess)
                allEdges.set(isSoftblockedAtEntry, fwdNeedsSoftblockAtEntry, bwdNeedsSoftblockAtEntry)
            }
        }
        logger.info("Done.")
    }

    private fun calculateSlope() {
        if (getEncodingManager().hasEncodedValue(AverageSlope.KEY) || getEncodingManager().hasEncodedValue(MaxSlope.KEY)) {
            if (!hasElevation())
                throw IllegalArgumentException("average_slope and max_slope encoded values require elevation, but no elevation provider is configured")
            val maxSlopeEnc = if (getEncodingManager().hasEncodedValue(MaxSlope.KEY))
                getEncodingManager().getDecimalEncodedValue(MaxSlope.KEY) else null
            val averageSlopeEnc = if (getEncodingManager().hasEncodedValue(AverageSlope.KEY))
                getEncodingManager().getDecimalEncodedValue(AverageSlope.KEY) else null
            SlopeCalculator(maxSlopeEnc, averageSlopeEnc).execute(baseGraph!!.baseGraph)
        }
    }

    protected open fun importOSM() {
        if (osmFile == null)
            throw IllegalStateException("Couldn't load from existing folder: " + ghLocation
                    + " but also cannot use file for DataReader as it wasn't specified!")

        val customAreas = GHUtility.readCountries().toMutableList()
        if (isEmpty(customAreasDirectory)) {
            logger.info("No custom areas are used, custom_areas.directory not given")
        } else {
            logger.info("Creating custom area index, reading custom areas from: '$customAreasDirectory'")
            customAreas.addAll(readCustomAreas())
        }

        val areaIndex = AreaIndex(customAreas)

        eleProvider.init()
        logger.info("start creating graph from $osmFile")
        val reader = OSMReader(baseGraph!!.baseGraph, getOSMParsers(), osmReaderConfig).setFile(_getOSMFile())
                .setAreaIndex(areaIndex)
                .setElevationProvider(eleProvider)
        logger.info("using " + getBaseGraphString() + ", memory:" + getMemInfo())

        createBaseGraphAndProperties()

        try {
            reader.readGraph()
        } catch (ex: IOException) {
            throw RuntimeException("Cannot read file " + getOSMFile(), ex)
        }
        val f = createFormatter()
        properties!!.put("datareader.import.date", f.format(Date()))
        if (reader.getDataDate() != null)
            properties!!.put("datareader.data.date", f.format(reader.getDataDate()!!))
    }

    protected open fun createBaseGraphAndProperties() {
        baseGraph!!.directory.create()
        baseGraph!!.create(100)
        properties!!.create(100)
        if (maxSpeedCalculator != null)
            maxSpeedCalculator!!.createDataAccessForParser(baseGraph!!.directory)
    }

    private fun calculateUrbanDensity() {
        if (getEncodingManager().hasEncodedValue(UrbanDensity.KEY)) {
            val urbanDensityEnc = getEncodingManager().getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity::class.java)
            if (!getEncodingManager().hasEncodedValue(RoadClass.KEY))
                throw IllegalArgumentException("Urban density calculation requires " + RoadClass.KEY)
            if (!getEncodingManager().hasEncodedValue(RoadClassLink.KEY))
                throw IllegalArgumentException("Urban density calculation requires " + RoadClassLink.KEY)
            val roadClassEnc = getEncodingManager().getEnumEncodedValue(RoadClass.KEY, RoadClass::class.java)
            val roadClassLinkEnc = getEncodingManager().getBooleanEncodedValue(RoadClassLink.KEY)
            UrbanDensityCalculator.calcUrbanDensity(baseGraph!!, urbanDensityEnc, roadClassEnc,
                    roadClassLinkEnc, residentialAreaRadius, residentialAreaSensitivity, cityAreaRadius, cityAreaSensitivity, urbanDensityCalculationThreads)
        }
    }

    private fun writeEncodingManagerToProperties() {
        EncodingManager.putEncodingManagerIntoProperties(getEncodingManager(), properties!!)
    }

    private fun readCustomAreas(): List<CustomArea> {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(JtsModule())
        val bordersDirectory = Paths.get(customAreasDirectory)
        val jsonFeatureCollections: MutableList<JsonFeatureCollection> = ArrayList()
        try {
            Files.newDirectoryStream(bordersDirectory, "*.{geojson,json}").use { stream ->
                for (borderFile in stream) {
                    Files.newBufferedReader(borderFile, StandardCharsets.UTF_8).use { reader ->
                        val jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection::class.java)
                        jsonFeatureCollections.add(jsonFeatureCollection)
                    }
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        return jsonFeatureCollections.stream().flatMap { j -> j.features.stream() }
                .map { jsonFeature -> CustomArea.fromJsonFeature(jsonFeature) }
                .collect(Collectors.toList())
    }

    /**
     * Currently we use this for a few tests where the dataReaderFile is loaded from the classpath
     */
    protected open fun _getOSMFile(): File {
        return File(osmFile!!)
    }

    /**
     * Load from existing graph folder.
     */
    open fun load(): Boolean {
        if (isEmpty(ghLocation))
            throw IllegalStateException("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before")

        if (fullyLoaded)
            throw IllegalStateException("graph is already successfully loaded")

        val tmpFileOrFolder = File(ghLocation)
        if (!tmpFileOrFolder.isDirectory && tmpFileOrFolder.exists()) {
            throw IllegalArgumentException("GraphHopperLocation cannot be an existing file. Has to be either non-existing or a folder.")
        } else {
            val compressed = File("$ghLocation.ghz")
            if (compressed.exists() && !compressed.isDirectory) {
                try {
                    Unzipper().unzip(compressed.absolutePath, ghLocation, removeZipped)
                } catch (ex: IOException) {
                    throw RuntimeException("Couldn't extract file " + compressed.absolutePath
                            + " to " + ghLocation, ex)
                }
            }
        }

        // todo: this does not really belong here, we abuse the load method to derive the dataAccessDefaultType setting from others
        if (!allowWrites && dataAccessDefaultType.isMMap)
            dataAccessDefaultType = DAType.MMAP_RO

        if (!File(ghLocation).exists())
            // there is just nothing to load
            return false

        val directory = GHDirectory(ghLocation, dataAccessDefaultType)
        directory.configure(dataAccessConfig)
        var lock: GHLock? = null
        try {
            // create locks only if writes are allowed, if they are not allowed a lock cannot be created
            // (e.g. on a read only filesystem locks would fail)
            if (directory.defaultType.isStoring && isAllowWrites()) {
                lockFactory.setLockDir(File(ghLocation))
                lock = lockFactory.create(fileLockName, false)
                if (!lock.tryLock())
                    throw RuntimeException("To avoid reading partial data we need to obtain the read lock but it failed. In $ghLocation", lock.getObtainFailedReason())
            }
            val properties = StorableProperties(directory)
            this.properties = properties
            if (!properties.loadExisting())
                // the -gh folder exists, but there is no properties file. it might be just empty, so let's act as if
                // the import did not run yet or is not complete for some reason
                return false
            encodingManager = EncodingManager.fromProperties(properties)
            baseGraph = BaseGraph.Builder(getEncodingManager())
                    .setDir(directory)
                    .set3D(hasElevation())
                    .withTurnCosts(getEncodingManager().needsTurnCostsSupport())
                    .build()
            checkProfilesConsistency()
            baseGraph!!.loadExisting()
            if (!skipProfileMatchCheck) {
                val storedProfilesString = properties.get("profiles")
                val storedProfileHashes = Arrays.stream(storedProfilesString.split(",".toRegex()).toTypedArray()).map { s -> s.split("\\|".toRegex(), limit = 2).toTypedArray() }.collect(Collectors.toMap({ kv -> kv[0] }, { kv -> Integer.parseInt(kv[1]) }))
                val configuredProfileHashes = getProfileHashes()
                configuredProfileHashes.forEach { (profile, hash) ->
                    val storedHash = storedProfileHashes[profile]
                            ?: throw IllegalStateException("You cannot add new profiles to the loaded graph. Profile '" + profile + "' is new."
                                    + "\nExisting profiles: " + java.lang.String.join(",", storedProfileHashes.keys)
                                    + "\nChange your configuration to match the graph or delete " + baseGraph!!.directory.location)
                    if (hash != storedHash)
                        throw IllegalStateException("Profile '" + profile + "' does not match."
                                + "\nStored: " + storedHash
                                + "\nConfigured: " + hash
                                + "\nChange this profile to match the stored one or delete " + baseGraph!!.directory.location)
                }
            }
            postProcessing(false)
            directory.loadMMap()
            setFullyLoaded()
            return true
        } finally {
            lock?.release()
        }
    }

    protected open fun getProfileHash(profile: Profile): Int {
        return profile.getVersion()
    }

    private fun getProfilesString(): String {
        return getProfileHashes().entries.stream().map { e -> e.key + "|" + e.value }.collect(Collectors.joining(","))
    }

    private fun getProfileHashes(): Map<String, Int> {
        return profilesByName.entries.stream().collect(Collectors.toMap({ entry -> entry.key }, { e -> getProfileHash(e.value) }))
    }

    open fun checkProfilesConsistency() {
        if (profilesByName.isEmpty())
            throw IllegalArgumentException("There has to be at least one profile")
        for (profile in profilesByName.values) {
            try {
                createWeighting(profile, PMap())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Could not create weighting for profile: '" + profile.getName() + "'.\n" +
                        "Profile: " + profile + "\n" +
                        "Error: " + e.message)
            }

            if (CustomWeighting.NAME == profile.getWeighting() && profile.getCustomModel() == null)
                throw IllegalArgumentException("custom model for profile '" + profile.getName() + "' was empty")
            if (CustomWeighting.NAME != profile.getWeighting() && profile.getCustomModel() != null)
                throw IllegalArgumentException("profile '" + profile.getName() + "' has a custom model but " +
                        "weighting=" + profile.getWeighting() + " was defined")
        }

        val chProfileSet = LinkedHashSet<String>(chPreparationHandler.getCHProfiles().size)
        for (chProfile in chPreparationHandler.getCHProfiles()) {
            val added = chProfileSet.add(chProfile.getProfile())
            if (!added) {
                throw IllegalArgumentException("Duplicate CH reference to profile '" + chProfile.getProfile() + "'")
            }
            if (!profilesByName.containsKey(chProfile.getProfile())) {
                throw IllegalArgumentException("CH profile references unknown profile '" + chProfile.getProfile() + "'")
            }
        }
        val lmProfileMap = LinkedHashMap<String, LMProfile>(lmPreparationHandler.getLMProfiles().size)
        for (lmProfile in lmPreparationHandler.getLMProfiles()) {
            val previous = lmProfileMap.put(lmProfile.getProfile(), lmProfile)
            if (previous != null) {
                throw IllegalArgumentException("Multiple LM profiles are using the same profile '" + lmProfile.getProfile() + "'")
            }
            if (!profilesByName.containsKey(lmProfile.getProfile())) {
                throw IllegalArgumentException("LM profile references unknown profile '" + lmProfile.getProfile() + "'")
            }
            if (lmProfile.usesOtherPreparation() && !profilesByName.containsKey(lmProfile.getPreparationProfile())) {
                throw IllegalArgumentException("LM profile references unknown preparation profile '" + lmProfile.getPreparationProfile() + "'")
            }
        }
        for (lmProfile in lmPreparationHandler.getLMProfiles()) {
            if (lmProfile.usesOtherPreparation() && !lmProfileMap.containsKey(lmProfile.getPreparationProfile())) {
                throw IllegalArgumentException("Unknown LM preparation profile '" + lmProfile.getPreparationProfile() + "' in LM profile '" + lmProfile.getProfile() + "' cannot be used as preparation_profile")
            }
            if (lmProfile.usesOtherPreparation() && lmProfileMap[lmProfile.getPreparationProfile()]!!.usesOtherPreparation()) {
                throw IllegalArgumentException("Cannot use '" + lmProfile.getPreparationProfile() + "' as preparation_profile for LM profile '" + lmProfile.getProfile() + "', because it uses another profile for preparation itself.")
            }
        }
    }

    fun getCHPreparationHandler(): CHPreparationHandler {
        return chPreparationHandler
    }

    private fun createCHConfigs(chProfiles: List<CHProfile>): List<CHConfig> {
        val chConfigs: MutableList<CHConfig> = ArrayList()
        for (chProfile in chProfiles) {
            val profile = profilesByName[chProfile.getProfile()]!!
            if (profile.hasTurnCosts()) {
                chConfigs.add(CHConfig.edgeBased(profile.getName()!!, createWeighting(profile, PMap())))
            } else {
                chConfigs.add(CHConfig.nodeBased(profile.getName()!!, createWeighting(profile, PMap())))
            }
        }
        return chConfigs
    }

    fun getLMPreparationHandler(): LMPreparationHandler {
        return lmPreparationHandler
    }

    private fun createLMConfigs(lmProfiles: List<LMProfile>): List<LMConfig> {
        val lmConfigs: MutableList<LMConfig> = ArrayList()
        for (lmProfile in lmProfiles) {
            if (lmProfile.usesOtherPreparation())
                continue
            val profile = profilesByName[lmProfile.getProfile()]!!
            // Note that we have to make sure the weighting used for LM preparation does not include turn costs, because
            // the LM preparation is running node-based and the landmark weights will be wrong if there are non-zero
            // turn costs, see discussion in #1960
            // Running the preparation without turn costs is also useful to allow e.g. changing the u_turn_costs per
            // request (we have to use the minimum weight settings (= no turn costs) for the preparation)
            val weighting = createWeighting(profile, PMap(), true)
            lmConfigs.add(LMConfig(profile.getName()!!, weighting))
        }
        return lmConfigs
    }

    /**
     * Runs both after the import and when loading an existing Graph
     *
     * @param closeEarly release resources as early as possible
     */
    protected open fun postProcessing(closeEarly: Boolean) {
        calcChecksums()
        initLocationIndex()
        importPublicTransit()

        if (closeEarly) {
            val includesCustomProfiles = profilesByName.values.stream().anyMatch { p -> CustomWeighting.NAME == p.getWeighting() }
            if (!includesCustomProfiles)
                // when there are custom profiles we must not close way geometry or KVStorage, because
                // they might be needed to evaluate the custom weightings for the following preparations
                baseGraph!!.flushAndCloseGeometryAndNameStorage()
        }

        if (lmPreparationHandler.isEnabled())
            loadOrPrepareLM(closeEarly)

        if (closeEarly)
            // we needed the location index for the LM preparation, but we don't need it for CH
            locationIndex!!.close()

        if (chPreparationHandler.isEnabled())
            loadOrPrepareCH(closeEarly)
    }

    protected open fun importPublicTransit() {
    }

    protected open fun interpolateBridgesTunnelsAndFerries() {
        if (getEncodingManager().hasEncodedValue(RoadEnvironment.KEY)) {
            val roadEnvEnc = getEncodingManager().getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment::class.java)
            var sw = StopWatch().start()
            // first step: fix the tower-end elevation values using the surrounding road network
            BridgeTunnelTowerCorrection(baseGraph!!.baseGraph, roadEnvEnc).execute()
            val towerCorrection = sw.stop().getSeconds()
            sw = StopWatch().start()
            EdgeElevationInterpolator(baseGraph!!.baseGraph, roadEnvEnc, RoadEnvironment.TUNNEL).execute()
            val tunnel = sw.stop().getSeconds()
            sw = StopWatch().start()
            EdgeElevationInterpolator(baseGraph!!.baseGraph, roadEnvEnc, RoadEnvironment.BRIDGE).execute()
            val bridge = sw.stop().getSeconds()
            // The SkadiProvider contains bathymetric data. For ferries this can result in bigger elevation changes
            // See #2098 for mor information
            sw = StopWatch().start()
            EdgeElevationInterpolator(baseGraph!!.baseGraph, roadEnvEnc, RoadEnvironment.FERRY).execute()
            logger.info("Tower correction " + towerCorrection.toInt() + "s, bridge interpolation " + bridge.toInt() + "s, tunnel interpolation " + tunnel.toInt() + "s, ferry interpolation " + sw.stop().getSeconds().toInt() + "s")
        }
    }

    fun createWeighting(profile: Profile, hints: PMap): Weighting {
        return createWeighting(profile, hints, false)
    }

    fun createWeighting(profile: Profile, hints: PMap, disableTurnCosts: Boolean): Weighting {
        return createWeightingFactory().createWeighting(profile, hints, disableTurnCosts)
    }

    protected open fun createWeightingFactory(): WeightingFactory {
        return DefaultWeightingFactory(baseGraph!!.baseGraph, getEncodingManager())
    }

    open fun route(request: GHRequest): GHResponse {
        return createRouter().route(request)
    }

    private fun createRouter(): Router {
        if (baseGraph == null || !fullyLoaded)
            throw IllegalStateException("Do a successful call to load or importOrLoad before routing")
        if (baseGraph!!.isClosed)
            throw IllegalStateException("You need to create a new GraphHopper instance as it is already closed")
        if (locationIndex == null)
            throw IllegalStateException("Location index not initialized")

        return doCreateRouter(baseGraph!!, getEncodingManager(), locationIndex!!, profilesByName, pathBuilderFactory,
                trMap, routerConfig, createWeightingFactory(), chGraphs, landmarks)
    }

    protected open fun doCreateRouter(baseGraph: BaseGraph, encodingManager: EncodingManager, locationIndex: LocationIndex, profilesByName: Map<String, Profile>,
                                      pathBuilderFactory: PathDetailsBuilderFactory, trMap: TranslationMap, routerConfig: RouterConfig,
                                      weightingFactory: WeightingFactory, chGraphs: Map<String, RoutingCHGraph>, landmarks: Map<String, LandmarkStorage>): Router {
        return Router(baseGraph, encodingManager, locationIndex, profilesByName, pathBuilderFactory,
                trMap, routerConfig, weightingFactory, chGraphs, landmarks
        )
    }

    protected open fun createLocationIndex(dir: Directory): LocationIndex {
        val tmpIndex = LocationIndexTree(baseGraph!!, dir)
        tmpIndex.setResolution(preciseIndexResolution)
        tmpIndex.setMaxRegionSearch(maxRegionSearch)
        if (!tmpIndex.loadExisting()) {
            ensureWriteAccess()
            tmpIndex.prepareIndex()
        }

        return tmpIndex
    }

    private fun calcChecksums() {
        if (!calcChecksums) return
        logger.info("Calculating checksums for {} profiles", profilesByName.size)
        val sw = StopWatch.started()
        val checksums_fwd = DoubleArray(profilesByName.size)
        val checksums_bwd = DoubleArray(profilesByName.size)
        val weightings = profilesByName.values.stream().map { profile -> createWeighting(profile, PMap()) }.toList()
        val edge = baseGraph!!.allEdges
        while (edge.next()) {
            for (i in 0 until profilesByName.size) {
                var weightFwd = weightings[i].calcEdgeWeight(edge, false)
                if (weightFwd.isInfinite()) weightFwd = -1.0
                weightFwd *= if (i % 2 == 0) -1.0 else 1.0
                var weightBwd = weightings[i].calcEdgeWeight(edge, true)
                if (weightBwd.isInfinite()) weightBwd = -1.0
                weightBwd *= if (i % 2 == 0) -1.0 else 1.0
                checksums_fwd[i] += weightFwd
                checksums_bwd[i] += weightBwd
            }
        }
        var index = 0
        for (profile in profilesByName.values) {
            properties!!.put("checksum.fwd." + profile.getName(), checksums_fwd[index])
            properties!!.put("checksum.bwd." + profile.getName(), checksums_bwd[index])
            logger.info("checksum.fwd." + profile.getName() + ": " + checksums_fwd[index])
            logger.info("checksum.bwd." + profile.getName() + ": " + checksums_bwd[index])
            index++
        }
        logger.info("Calculating checksums took: " + sw.stop().getTimeString())
    }

    /**
     * Initializes the location index after the import is done.
     */
    protected open fun initLocationIndex() {
        if (locationIndex != null)
            throw IllegalStateException("Cannot initialize locationIndex twice!")

        locationIndex = createLocationIndex(baseGraph!!.directory)
    }

    private fun getCHProfileVersion(profile: String): String {
        return properties!!.get("graph.profiles.ch.$profile.version")
    }

    private fun setCHProfileVersion(profile: String, version: Int) {
        properties!!.put("graph.profiles.ch.$profile.version", version)
    }

    private fun getLMProfileVersion(profile: String): String {
        return properties!!.get("graph.profiles.lm.$profile.version")
    }

    private fun setLMProfileVersion(profile: String, version: Int) {
        properties!!.put("graph.profiles.lm.$profile.version", version)
    }

    protected open fun loadOrPrepareCH(closeEarly: Boolean) {
        for (profile in chPreparationHandler.getCHProfiles())
            if (!getCHProfileVersion(profile.getProfile()).isEmpty()
                    && getCHProfileVersion(profile.getProfile()) != "" + getProfileHash(profilesByName[profile.getProfile()]!!))
                throw IllegalArgumentException("CH preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration")

        // we load ch graphs that already exist and prepare the other ones
        val chConfigs = createCHConfigs(chPreparationHandler.getCHProfiles())
        val loaded = chPreparationHandler.load(baseGraph!!.baseGraph, chConfigs)
        val configsToPrepare = chConfigs.stream().filter { c -> !loaded.containsKey(c.name) }.collect(Collectors.toList())
        val prepared = prepareCH(closeEarly, configsToPrepare)

        // we map all profile names for which there is CH support to the according CH graphs
        val chGraphs = LinkedHashMap<String, RoutingCHGraph>()
        this.chGraphs = chGraphs
        for (profile in chPreparationHandler.getCHProfiles()) {
            if (loaded.containsKey(profile.getProfile()) && prepared.containsKey(profile.getProfile()))
                throw IllegalStateException("CH graph should be either loaded or prepared, but not both: " + profile.getProfile())
            else if (prepared.containsKey(profile.getProfile())) {
                setCHProfileVersion(profile.getProfile(), getProfileHash(profilesByName[profile.getProfile()]!!))
                val res = prepared[profile.getProfile()]!!
                chGraphs.put(profile.getProfile(), RoutingCHGraphImpl.fromGraph(baseGraph!!.baseGraph, res.getCHStorage(), res.getCHConfig()))
            } else if (loaded.containsKey(profile.getProfile())) {
                chGraphs.put(profile.getProfile(), loaded[profile.getProfile()]!!)
            } else
                throw IllegalStateException("CH graph should be either loaded or prepared: " + profile.getProfile())
        }
        chGraphs.forEach { (name, ch) ->
            val store = (ch as RoutingCHGraphImpl).chStorage
            logger.info("CH available for profile {}, {}MB, {}, ({}MB)", name, Helper.nf(store.capacity / Helper.MB), store.toDetailsString(), store.getMB())
        }
    }

    protected open fun prepareCH(closeEarly: Boolean, configsToPrepare: List<CHConfig>): Map<String, PrepareContractionHierarchies.Result> {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess()
        if (!baseGraph!!.isFrozen)
            baseGraph!!.freeze()
        return chPreparationHandler.prepare(baseGraph!!, properties!!, configsToPrepare, closeEarly)
    }

    /**
     * For landmarks it is required to always call this method: either it creates the landmark data or it loads it.
     */
    protected open fun loadOrPrepareLM(closeEarly: Boolean) {
        for (profile in lmPreparationHandler.getLMProfiles())
            if (!getLMProfileVersion(profile.getProfile()).isEmpty()
                    && getLMProfileVersion(profile.getProfile()) != "" + getProfileHash(profilesByName[profile.getProfile()]!!))
                throw IllegalArgumentException("LM preparation of " + profile.getProfile() + " already exists in storage and doesn't match configuration")

        // we load landmark storages that already exist and prepare the other ones
        val lmConfigs = createLMConfigs(lmPreparationHandler.getLMProfiles())
        val loaded = lmPreparationHandler.load(lmConfigs, baseGraph!!, getEncodingManager())
        val loadedConfigs = loaded.stream().map { landmarkStorage -> landmarkStorage.getLMConfig() }.toList()
        val configsToPrepare = lmConfigs.stream().filter { c -> !loadedConfigs.contains(c) }.collect(Collectors.toList())
        val prepared = prepareLM(closeEarly, configsToPrepare)

        // we map all profile names for which there is LM support to the according LM storages
        val landmarks = LinkedHashMap<String, LandmarkStorage>()
        this.landmarks = landmarks
        for (lmp in lmPreparationHandler.getLMProfiles()) {
            // cross-querying
            val prepProfile = if (lmp.usesOtherPreparation()) lmp.getPreparationProfile() else lmp.getProfile()
            val loadedLMS = loaded.stream().filter { lms -> lms.getLMConfig().getName() == prepProfile }.findFirst()
            val preparedLMS = prepared.stream().filter { pl -> pl.getLandmarkStorage().getLMConfig().getName() == prepProfile }.findFirst()
            if (loadedLMS.isPresent && preparedLMS.isPresent)
                throw IllegalStateException("LM should be either loaded or prepared, but not both: $prepProfile")
            else if (preparedLMS.isPresent) {
                setLMProfileVersion(lmp.getProfile(), getProfileHash(profilesByName[lmp.getProfile()]!!))
                landmarks.put(lmp.getProfile(), preparedLMS.get().getLandmarkStorage())
            } else
                loadedLMS.ifPresent { landmarkStorage -> landmarks.put(lmp.getProfile(), landmarkStorage) }
        }
    }

    protected open fun prepareLM(closeEarly: Boolean, configsToPrepare: List<LMConfig>): List<PrepareLandmarks> {
        if (!configsToPrepare.isEmpty())
            ensureWriteAccess()
        if (!baseGraph!!.isFrozen)
            baseGraph!!.freeze()
        return lmPreparationHandler.prepare(configsToPrepare, baseGraph!!, getEncodingManager(), properties!!, getLocationIndex(), closeEarly)
    }

    /**
     * Internal method to clean up the graph.
     */
    protected open fun cleanUp() {
        val preparation = PrepareRoutingSubnetworks(baseGraph!!.baseGraph, buildSubnetworkRemovalJobs())
        preparation.setMinNetworkSize(minNetworkSize)
        preparation.setThreads(subnetworksThreads)
        preparation.doWork()
        logger.info("nodes: " + Helper.nf(baseGraph!!.nodes.toLong()) + ", edges: " + Helper.nf(baseGraph!!.edges.toLong()))
    }

    private fun buildSubnetworkRemovalJobs(): List<PrepareJob> {
        val jobs: MutableList<PrepareJob> = ArrayList()
        for (profile in profilesByName.values) {
            // if turn costs are enabled use u-turn costs of zero as we only want to make sure the graph is fully connected assuming finite u-turn costs
            val weighting = createWeighting(profile, PMap().putObject(Parameters.Routing.U_TURN_COSTS, 0))
            jobs.add(PrepareJob(getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profile.getName()!!)), weighting))
        }
        return jobs
    }

    protected open fun flush() {
        logger.info("flushing graph " + getBaseGraphString() + ", details:" + baseGraph!!.toDetailsString() + ", "
                + getMemInfo() + ")")
        baseGraph!!.flush()
        properties!!.flush()
        logger.info("flushed graph " + getMemInfo() + ")")
        setFullyLoaded()
    }

    /**
     * Releases all associated resources like memory or files. But it does not remove them. To
     * remove the files created in graphhopperLocation you have to call clean().
     */
    open fun close() {
        baseGraph?.close()
        properties?.close()

        chGraphs.values.forEach { routingCHGraph -> routingCHGraph.close() }
        landmarks.values.forEach { landmarkStorage -> landmarkStorage.close() }

        locationIndex?.close()

        try {
            lockFactory.forceRemove(fileLockName, true)
        } catch (ex: Exception) {
            // silently fail e.g. on Windows where we cannot remove an unreleased native lock
        }
    }

    /**
     * Removes the on-disc routing files. Call only after calling close or before importOrLoad or
     * load
     */
    open fun clean() {
        if (getGraphHopperLocation().isEmpty())
            throw IllegalStateException("Cannot clean GraphHopper without specified graphHopperLocation")

        val folder = File(getGraphHopperLocation())
        removeDir(folder)
    }

    protected open fun ensureNotLoaded() {
        if (fullyLoaded)
            throw IllegalStateException("No configuration changes are possible after loading the graph")
    }

    protected open fun ensureWriteAccess() {
        if (!allowWrites)
            throw IllegalStateException("Writes are not allowed!")
    }

    private fun setFullyLoaded() {
        fullyLoaded = true
    }

    open fun getFullyLoaded(): Boolean {
        return fullyLoaded
    }

    open fun getRouterConfig(): RouterConfig {
        return routerConfig
    }

    open fun getReaderConfig(): OSMReaderConfig {
        return osmReaderConfig
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphHopper::class.java)

        @JvmStatic
        fun parseEncodedValueString(encodedValuesStr: String): MutableMap<String, PMap> {
            val encodedValuesWithProps = LinkedHashMap<String, PMap>()
            Arrays.stream(encodedValuesStr.split(",".toRegex()).toTypedArray())
                    .filter { evStr -> !evStr.isBlank() }
                    .forEach { evStr ->
                        val key = evStr.trim().split("\\|".toRegex()).toTypedArray()[0]
                        if (encodedValuesWithProps.put(key, PMap(evStr)) != null)
                            throw IllegalArgumentException("duplicate encoded value in config graph.encoded_values: $key")
                    }
            return encodedValuesWithProps
        }

        private fun needsSoftblock(edgeIterator: EdgeIterator, roadAccess: EnumEncodedValue<RoadAccess>, carAccess: BooleanEncodedValue): Boolean {
            while (edgeIterator.next()) {
                if (edgeIterator.get(carAccess) && edgeIterator.get(roadAccess) != RoadAccess.DESTINATION) {
                    return true
                }
            }
            return false
        }

        private fun getRestrictionVehicleTypesByProfile(profiles: Collection<Profile>): Map<String, List<String>> {
            val result = LinkedHashMap<String, List<String>>()
            for (profile in profiles)
                if (profile.hasTurnCosts())
                    result.put(profile.getName()!!, profile.getTurnCostsConfig()!!.getVehicleTypes())
            return result
        }

        private fun createElevationProvider(ghConfig: GraphHopperConfig): ElevationProvider {
            val eleProviderStr = toLowerCase(ghConfig.getString("graph.elevation.provider", "noop")!!)

            if (ghConfig.has("graph.elevation.calcmean"))
                throw IllegalArgumentException("graph.elevation.calcmean is deprecated, use graph.elevation.interpolate")

            val cacheDirStr = ghConfig.getString("graph.elevation.cache_dir", "")!!
            if (cacheDirStr.isEmpty() && ghConfig.has("graph.elevation.cachedir"))
                throw IllegalArgumentException("use graph.elevation.cache_dir not cachedir in configuration")

            val interpolate = if (ghConfig.has("graph.elevation.interpolate"))
                "bilinear" == ghConfig.getString("graph.elevation.interpolate", "none")
            else
                ghConfig.getBool("graph.elevation.calc_mean", false)
            val removeTempElevationFiles = ghConfig.getBool("graph.elevation.clear",
                    ghConfig.getBool("graph.elevation.cgiar.clear", false))

            var elevationProvider = ElevationProvider.NOOP
            if (eleProviderStr.equals("hgt", ignoreCase = true)) {
                elevationProvider = HGTProvider(cacheDirStr)
            } else if (eleProviderStr.equals("srtm", ignoreCase = true)) {
                elevationProvider = SRTMProvider(cacheDirStr)
            } else if (eleProviderStr.equals("cgiar", ignoreCase = true)) {
                elevationProvider = CGIARProvider(cacheDirStr)
            } else if (eleProviderStr.equals("gmted", ignoreCase = true)) {
                elevationProvider = GMTEDProvider(cacheDirStr)
            } else if (eleProviderStr.equals("srtmgl1", ignoreCase = true)) {
                elevationProvider = SRTMGL1Provider(cacheDirStr)
            } else if (eleProviderStr.equals("multi", ignoreCase = true)) {
                elevationProvider = MultiSourceElevationProvider(cacheDirStr)
            } else if (eleProviderStr.equals("skadi", ignoreCase = true)) {
                elevationProvider = SkadiProvider(cacheDirStr)
            } else if (eleProviderStr.equals("sonny", ignoreCase = true)) {
                elevationProvider = SonnyProvider(cacheDirStr)
            } else if (eleProviderStr.equals("multi3", ignoreCase = true)) {
                elevationProvider = MultiSource3ElevationProvider(cacheDirStr)
            } else if (eleProviderStr.equals("pmtiles", ignoreCase = true)) {
                val zoom = ghConfig.getInt("graph.elevation.pmtiles.zoom", -1)
                val terrainEncoding = ghConfig.getString("graph.elevation.pmtiles.terrain_encoding", "terrarium")!!
                elevationProvider = PMTilesElevationProvider(
                        ghConfig.getString("graph.elevation.pmtiles.location", "/tmp/planet.pmtiles")!!,
                        PMTilesElevationProvider.TerrainEncoding.valueOf(terrainEncoding.uppercase(Locale.ROOT)),
                        interpolate, zoom, cacheDirStr)
                        .setAutoRemoveTemporaryFiles(removeTempElevationFiles)
            } else if (!eleProviderStr.isEmpty() && !eleProviderStr.equals("noop", ignoreCase = true)) {
                throw IllegalArgumentException("Did not find elevation provider: $eleProviderStr")
            }

            if (elevationProvider is TileBasedElevationProvider) {
                val provider = elevationProvider

                val baseURL = ghConfig.getString("graph.elevation.base_url", "")!!
                if (baseURL.isEmpty() && ghConfig.has("graph.elevation.baseurl"))
                    throw IllegalArgumentException("use graph.elevation.base_url not baseurl in configuration")

                val elevationDAType = DAType.fromString(ghConfig.getString("graph.elevation.dataaccess", "MMAP")!!)

                provider
                        .setAutoRemoveTemporaryFiles(removeTempElevationFiles)
                        .setInterpolate(interpolate)
                        .setDAType(elevationDAType)
                if (!baseURL.isEmpty())
                    provider.setBaseURL(baseURL)
            }
            return elevationProvider
        }

        @JvmStatic
        fun sortGraphAlongHilbertCurve(graph: BaseGraph) {
            logger.info("sorting graph along Hilbert curve.... (memory:" + getMemInfo() + ")")
            val sw = StopWatch.started()
            val na = graph.nodeAccess
            val order = 31 // using 15 would allow us to use ints for sortIndices, but this would result in (marginally) slower routing
            val sortIndices = LongArrayList()
            for (node in 0 until graph.nodes)
                sortIndices.add(latLonToHilbertIndex(na.getLat(node), na.getLon(node), order))
            val nodeOrder = IndirectSort.mergesort(0, graph.nodes, IndirectComparator { nodeA, nodeB -> java.lang.Long.compare(sortIndices.get(nodeA), sortIndices.get(nodeB)) })
            val explorer = graph.createEdgeExplorer()
            val edges = graph.edges
            val edgeOrder = IntArrayList()
            val edgesFound = GrowableBitSet(edges.toLong())
            for (node in nodeOrder) {
                val iter = explorer.setBaseNode(node)
                while (iter.next()) {
                    if (!edgesFound.get(iter.edge.toLong())) {
                        edgeOrder.add(iter.edge)
                        edgesFound.set(iter.edge.toLong())
                    }
                }
            }
            val newEdgesByOldEdges = ArrayUtil.invert(edgeOrder)
            val newNodesByOldNodes = IntArrayList.from(*ArrayUtil.invert(nodeOrder))
            logger.info("calculating sort order took: " + sw.stop().getTimeString() + ", memory:" + getMemInfo())
            sortGraphForGivenOrdering(graph, newNodesByOldNodes, newEdgesByOldEdges)
        }

        @JvmStatic
        fun sortGraphForGivenOrdering(baseGraph: BaseGraph, newNodesByOldNodes: IntArrayList, newEdgesByOldEdges: IntArrayList) {
            if (!ArrayUtil.isPermutation(newEdgesByOldEdges))
                throw IllegalStateException("New edges: not a permutation")
            if (!ArrayUtil.isPermutation(newNodesByOldNodes))
                throw IllegalStateException("New nodes: not a permutation")
            logger.info("sort graph for fixed ordering...")
            var sw = StopWatch().start()
            baseGraph.sortEdges { oldEdge -> newEdgesByOldEdges.get(oldEdge) }
            logger.info("sorting {} edges took: {}", Helper.nf(newEdgesByOldEdges.size().toLong()), sw.stop().getTimeString())
            sw = StopWatch().start()
            baseGraph.relabelNodes { oldNode -> newNodesByOldNodes.get(oldNode) }
            logger.info("sorting {} nodes took: {}", Helper.nf(newNodesByOldNodes.size().toLong()), sw.stop().getTimeString())
        }

        @JvmStatic
        fun latLonToHilbertIndex(lat: Double, lon: Double, order: Int): Long {
            val nx = (lon + 180) / 360
            val ny = (90 - lat) / 180
            val size = 1L shl order
            var x = (nx * size).toLong()
            var y = (ny * size).toLong()
            x = Math.max(0L, Math.min(size - 1, x))
            y = Math.max(0L, Math.min(size - 1, y))
            return xy2d(order, x, y)
        }

        @JvmStatic
        fun xy2d(n: Int, x: Long, y: Long): Long {
            var x = x
            var y = y
            var d: Long = 0
            var s = 1L shl (n - 1)
            while (s > 0) {
                val rx = if ((x and s) > 0) 1 else 0
                val ry = if ((y and s) > 0) 1 else 0
                d += s * s * ((3 * rx) xor ry)
                // rotate
                if (ry == 0) {
                    if (rx == 1) {
                        x = s - 1 - x
                        y = s - 1 - y
                    }
                    val tmp = x
                    x = y
                    y = tmp
                }
                s = s shr 1
            }
            return d
        }

        @JvmStatic
        fun resolveCustomAreas(customAreasDirectory: String): JsonFeatureCollection {
            val globalAreas = JsonFeatureCollection()
            if (!customAreasDirectory.isEmpty()) {
                val mapper = ObjectMapper().registerModule(JtsModule())
                try {
                    Files.newDirectoryStream(Paths.get(customAreasDirectory), "*.{geojson,json}").use { stream ->
                        StreamSupport.stream(stream.spliterator(), false)
                                .sorted(Comparator.comparing { path: Path -> path.toString() })
                                .forEach { customAreaFile ->
                                    try {
                                        Files.newBufferedReader(customAreaFile, StandardCharsets.UTF_8).use { reader ->
                                            globalAreas.features.addAll(mapper.readValue(reader, JsonFeatureCollection::class.java).features)
                                        }
                                    } catch (e: IOException) {
                                        throw UncheckedIOException(e)
                                    }
                                }
                        logger.info("Will make " + globalAreas.features.size + " areas available to all custom profiles. Found in " + customAreasDirectory)
                    }
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            }
            return globalAreas
        }

        @JvmStatic
        fun resolveCustomModelFiles(customModelFolder: String, profiles: List<Profile>, globalAreas: JsonFeatureCollection): List<Profile> {
            val jsonOM = Jackson.newObjectMapper()
            val newProfiles: MutableList<Profile> = ArrayList()
            for (profile in profiles) {
                if (CustomWeighting.NAME != profile.getWeighting()) {
                    newProfiles.add(profile)
                    continue
                }
                val cm = profile.getHints().getObject<Any?>(CustomModel.KEY, null)
                var customModel: CustomModel
                if (cm != null) {
                    if (!profile.getHints().getObject("custom_model_files", Collections.emptyList<Any>()).isEmpty())
                        throw IllegalArgumentException("Do not use custom_model_files and custom_model together")
                    try {
                        // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                        customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel::class.java)
                        newProfiles.add(profile.setCustomModel(customModel))
                    } catch (ex: Exception) {
                        throw RuntimeException("Cannot load custom_model from " + cm + " for profile " + profile.getName()
                                + ". If you are trying to load from a file, use 'custom_model_files' instead.", ex)
                    }
                } else {
                    if (!profile.getHints().getString("custom_model_file", "").isEmpty())
                        throw IllegalArgumentException("Since 8.0 you must use a custom_model_files array instead of custom_model_file string")
                    val customModelFileNames = profile.getHints().getObject<List<String>?>("custom_model_files", null)
                            ?: throw IllegalArgumentException("Missing 'custom_model' or 'custom_model_files' field in profile '"
                                    + profile.getName() + "'. To use default specify custom_model_files: []")
                    if (customModelFileNames.isEmpty()) {
                        customModel = CustomModel()
                        newProfiles.add(profile.setCustomModel(customModel))
                    } else {
                        customModel = CustomModel()
                        for (file in customModelFileNames) {
                            if (file.contains(File.separator))
                                throw IllegalArgumentException("Use custom_models.directory for the custom_model_files parent")
                            if (!file.endsWith(".json"))
                                throw IllegalArgumentException("Yaml is no longer supported, see #2672. Use JSON with optional comments //")

                            try {
                                var string: String
                                // 1. try to load custom model from jar
                                val inputStream = GHUtility::class.java.getResourceAsStream("/com/graphhopper/custom_models/$file")
                                // dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
                                val customModelFile = Paths.get(customModelFolder).resolve(file)
                                if (inputStream != null) {
                                    if (Files.exists(customModelFile))
                                        throw RuntimeException("Custom model file name '$file' is already used for built-in profiles. Use another name")
                                    string = readJSONFileWithoutComments(InputStreamReader(inputStream))
                                } else {
                                    // 2. try to load custom model file from external location
                                    string = readJSONFileWithoutComments(customModelFile.toFile().absolutePath)
                                }
                                customModel = CustomModel.merge(customModel, jsonOM.readValue(string, CustomModel::class.java))
                            } catch (ex: IOException) {
                                throw RuntimeException("Cannot load custom_model from location " + file + ", profile:" + profile.getName(), ex)
                            }
                        }

                        newProfiles.add(profile.setCustomModel(customModel))
                    }
                }

                // we can fill in all areas here as in the created template we include only the areas that are used in
                // statements (see CustomModelParser)
                customModel.addAreas(globalAreas)
            }
            return newProfiles
        }
    }
}
