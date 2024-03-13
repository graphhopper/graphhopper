### 9.0 [not yet released]

- u_turn_costs information is no longer stored in profile. Use the TurnCostsConfig instead
- the custom models do no longer include the speed, access and priority encoded values only implicitly, see #2938 for a migration guide. You have to specify an initial statement like `{ "if": "true", "limit_to": "car_average_speed" }` for `speed` and `{ "if": "!car_access", "multiply_by": "0" }` for `priority`
- conditional access restriction tags are no longer considered from vehicle tag parsers and instead a car_road_access_conditional encoded value (similarly for bike + foot) can be used in a custom model. This fixes #2477. More details are accessible via path details "access_conditional" (i.e. converted from OSM access:conditional). See #2863
- replaced (Vehicle)EncodedValueFactory and (Vehicle)TagParserFactory with ImportRegistry, #2935
- encoded values used in custom models are added automatically, no need to add them to graph.encoded_values anymore, #2935
- removed the ability to sort the graph (graph.do_sort) due to incomplete support, #2919
- minor changes for import hooks, #2917
- removed wheelchair vehicle and related parsers, with currently no complete replacement as it needs to be redone properly with a custom model
- removed deprecated PMap.put

### 8.0 [18 Oct 2023]

- access "turn"-EncodedValue of EncodingManager through separate methods, see #2884
- removed fastest weighting for public usage, use custom instead, see #2866
- removed shortest weighting for public usage, use a high distance_influence instead, see #2865
- removed duration:seconds as intermediate tag
- /info endpoint does no longer return the vehicle used per profile and won't return encoded value of vehicles like car_average_speed
- Country rules no longer contain maxspeed handling, enable a much better alternative via `max_speed_calculator.enabled: true`. On the client side use `max_speed_estimated` to determine if max_speed is from OSM or an estimation. See #2810
- bike routing better avoids dangerous roads, see #2796 and #2802
- routing requests can be configured to timeout after some time, see #2795
- custom_model_file string changed to custom_model_files array, see #2787
- renamed EdgeKVStorage to KVStorage as it is (temporarily) used for node tage too, see #2705
- bike vehicles are now allowed to go in reverse direction of oneways, see custom_models/bike.json #196
- prefer cycleways, bicycle_road and cyclestreet for bike routing, see #2784 and #2778
- add support for further surfaces like pebblestones or concrete:lanes, see #2751
- reduced memory usage for urban density calculation, see #2828
- urban density is now based on road junctions, so the according parameters need adjustment in case
  the config file does not use the defaults, see #2842
- removed heading penalty *time*, see #2563
- base graph no longer allows loop edges, see #2862

### 7.0 [14 Mar 2023]

- access node tags via List instead of Map: List<Map<String, Object>> nodeTags = way.getTag("node_tags", emptyList()), see #2705
- remove StringEncodedValue support from custom model due to insufficient usage/testing
- handle also node_tags in handleWayTags, when extending AbstractAccessParser call handleNodeTags, #2738
- Format of 'areas' in CustomModel changed to 'FeatureCollection'. The old format is deprecated and will be removed in a later version, #2734
- TagParser#handleWayTags no longer returns an IntsRef. We assume it never returned anything other than the input IntsRef.
- there is no longer a default value for the distanceInfluence parameter in custom models sent via client-hc. Previously it was 70. Not setting it explicitly now means the server-side value will be used. getDistanceInfluence can now be null. Server-side profiles with custom weighting now use distance_influence: 0 by default (previously it was 70). see #2716
- there is a new, required 'import.osm.ignored_highways' configuration option that must be used to not increase the graph size and decrease performance for motorized-only routing compared to previous versions, #2702
- new osm_way_id encoded value, #2701
- the parameters vehicle, weighting, edge_based and turn_costs are no longer supported, use the profile parameter instead
- removed motorroad to road_class conversion, #2329
- removed YAML support for custom models on the server-side. Only allow JSON with // comments.
- Bike2WeightTagParser was removed. Use the bike vehicle with a custom model, see custom_models/bike.json
- CurvatureWeighting was removed. Use a custom model with 'curvature' instead, see custom_models/curvature.json (#2665)
- internal keys for EdgeKVStorage changed to contain the street_ prefix like the path details too. Similarly, the
  extra_info in the instructions of the API response, see #2661
- subnetwork preparation can now be run in parallel to slightly speed up the base graph import (#2737)
- The block_area parameter was removed. Use custom model areas instead.

### 6.0 [13 Sep 2022]

- Car4WDTagParser was removed. Use the roads vehicle with a custom model, see custom_models/car4wd.json see #2651
- When using a DecimalEncodedValue with useMaximumAsInfinity=true and a single bit of space make sure you always use 
  Double.POSITIVE_INFINITY to set the value, see #2646
- renamed DouglasPeucker to RamerDouglasPeucker
- path details at via-points are no longer merged, see #2626
- removed the FlagEncoder interface. for example encoder.getAccessEnc() is now encodingManager.getBooleanEncodedValue(
  VehicleAccess.key("car")), #2611
- backward incompatible change as instructions and the street_name path detail do no longer contain the ref #2598
- StringIndex is now EdgeKVStorage and can store e.g. byte arrays. String values needs to be limited to 255 bytes before
  storing them. See EdgeKVStorage.cutString and #2597.
- the Matrix client changed and users have to adapt the usage, see #2587
- replaced car$access with car_access (and same for <vehicle>$average_speed and <vehicle>$priority)
- don't allow cars or motorcycles to use ways tagged with service=emergency_access (#2484)
- faster flexible routing, especially in conjunction with turn costs (#2571)
- negative OSM Ids are not supported any longer (#2652)
- new urban_density encoded value based on road density calculation (#2637)

### 5.0 [23 Mar 2022]

- Use routing.instructions to disable instructions on the server side. datareader.instructions is used to disable the
  name parsing (#2537)
- no more explicit passByDefaultBarriers in FlagEncoders, blockByDefaultBarriers was renamed to just barriers, no more
  handling of highway=ford (#2538)
- OSMReader no longer sets the artificial estimated_distance tag, but sets the edge_distance and point_list tags for all
  edges, the way_distance for selected ways and additionally the duration:seconds and speed_from_duration tags when the
  duration tag is present (#2528)
- fixed speed calculation for ferry routes with duration tags (#2528)
- request gzipping for matrix and route clients (#2511)
- bugfix: client-hc now considers headings and custom models (#2009, #2535)
- the artificial tag duration:seconds is now a long, no longer a string, commit 6d81d8ae8de52987522991edd835e42c8d2046cf
- added FlagEncoder#getName (use just like toString() before), commit 86f6a8b5209ad8ef47c24d935f5746e7694eb11c
- faster edge-based CH preparation, especially with large u-turn costs and GermanyCountryRule (many large weight edges
  due to access=destination on tracks) (#2522)
- consider subnetworks when evaluating curbside constraints (#2502)
- improved node-based CH performance (faster preparation and less shortcuts(=memory usage)) (#2491)
- the GraphHopperApplication class was moved from com.graphhopper.http to com.graphhopper.application (#2487)
- it is now possible to add CH preparations to an existing graph folder, CH graphs no longer need to be added before
  GraphHopperStorage#freeze (#2481)
- the two EncodedValue implementations accept now negative values too. The default value can now only be 0 or
  Double.Infinity, but this option will be removed later too, see discussion in #2473
- throw MaximumNodesExceededException instead of a generic IllegalArgumentException (#2464)
- removed graphhopper.sh script. Use java command directly instead. (#2431)
- removed the ferry argument of TagParser#handleWayTags. ferry ways can be recognized using the reader way (#2467)
- removed RoadEnvironment.SHUTTLE_TRAIN. this is covered by `FERRY` (#2466)
- create edge flags per edge, not per way. increases custom_area precision. areas are recognized by points along the
  edges now -> (#2457, #2472)
- fixed handling of too large mtb:scale tags (#2458)
- added Toll.MISSING; custom models must be adapted to check for explicit toll values e.g `toll != NO`
  -> `toll == HGV || toll == ALL` (#2164)
- use GraphHopper#setGraphHopperLocation before calling load() instead of GraphHopper#load(graphHopperLocation) (#2437)
- barrier nodes at junctions are now ignored (#2433)
- AbstractFlagEncoder#handleNodeTags was replaced by AbstractFlagEncoder#isBarrier (#2434)
- consider heading when snapping coordinates to the road network, this is especially important for navigation (#2411)
- OSMReader no longer sets the artificial 'estimated_center' tag and processNode also receives EMPTY_NODEs (971d686)

### 4.0 [29 Sep 2021]

- faster node-based CH preparation (~20%), (#2390)
- more flexible ElevationProvider interface, support providing elevation via node tags (#2374, #2381)
- added country encoded value for all countries (#2353)
- bike improvements (#2357, #2371, #2389)
- improved handling of barriers (#2345, #2340, #2406)
- removed spatial rules, replaced by country rules and custom areas (#2353)
- removed api module and moved it into web-api, no more Jackson MixIns (#2372)
- flag encoders are no longer versioned (#2355)
- JSON route response contains now bbox if start and end are identical
- renamed PriorityCode enums: AVOID_IF_POSSIBLE -> SLIGHT_AVOID, REACH_DEST -> AVOID, AVOID_AT_ALL_COSTS -> AVOID_MORE, WORST -> BAD
- added smoothness encoded value, used to determine bike speed (#2303)
- maps: custom_model is now included in URL (#2328)
- maps/isochrone: works for different profiles now (#2332)
- there is no stable tag anymore, either use master or one of the release branches like 2.x, 3.x, ...
- moved custom model editor to github.com/graphhopper/custom-model-editor
- PointList#getSize() -> PointList#size()
- migrated tests from junit 4 to 5 (#2324)
- barriers do no longer block by default for car; remove block_barriers config option (see discussion in #2340)

### 3.0 [17 May 2021]

- removed the stable tag (was pointing to commit dd2c20c763e4c19b701e92386432b37713cd8dc5)
- fix location lookup with point hints for curved roads, #2319
- custom_model_file only accepts file names without path. Use custom_model_folder instead.
- the load method in GraphHopperWeb (client-hc) was removed
- routing.ch.disabling_allowed and routing.lm.disabling_allowed configuration options are no longer supported
- moved the graphhopper-reader-osm module into core, use the graphhopper-core module directly instead. GraphHopperOSM is
  deprecated, use GraphHopper instead.
- subnetwork removal has changed and for every LocationIndex lookup one needs to explicitly include the new 'subnetwork'
  EncodedValue, see #2290
- DefaultEdgeFilter was renamed to AccessFilter. Use DefaultSnapFilter for the location lookup to make sure the query
  points do not snap to subnetworks. Previously subnetwork edges' access was set to zero, but this is no longer the
  case. Now subnetworks need to be identified by the subnetwork encoded value.
- moved the graphhopper-isochrone module into graphhopper-core
- removed setEncodingManager use setProfiles instead or for more custom requirements use
  GraphHopper.getEncodingManagerBuilder
- CustomWeighting language breaks old format but is more powerful and easier to read; it also allows factors >1 for
  server-side CustomModels
- renamed GHUtilities.setProperties to setSpeed
- Helper.createFormatter is using the ENGLISH Locale instead of UK, see #2186
- the name of an encoded value can only contain lower letters, underscore or numbers. It has to start with a lower
  letter
- default for GraphHopperMatrixWeb (client for Matrix API) is now the sync POST request without the artificial polling
  delay in most cases
- refactored TransportationMode to better reflect the usage in source data parsing only
- customizable routing is included in the endpoint /route (under the "custom_model" entry) and does not need to be
  enabled
- the format of customizable routing completely changed but the maps demo includes a simple editor to learn it easily (#2239). See #2209 and #2251. See examples in this blog
  post: https://www.graphhopper.com/blog/2020/05/31/examples-for-customizable-routing/
- removed Dockerfile
- the second argument of the VirtualEdgeIteratorState constructor is now an edge key (was an edge id before)
- removed instruction annotations(cycleway, off_bike, ford, ferry, private, toll)
- the boundaries file is now expected to provide the countrycodes via the `ISO3166-1:alpha3` property

### 2.0 [29 Sep 2020]

- AbstractFlagEncoder#getMaxSpeed returns now Double#NaN instead of -1 if no maxspeed was found
- renamed QueryResult -> Snap
- much faster CH preparation (node- and edge-based), but increased memory usage during preparation, #2132
- added navigation repo #2071
- use Java 8 also for core, client-hc and reader-osm modules. all modules use Java 8 now
- removed android demo, #1940
- added edge key path detail, #2073
- fixed bug for turn restrictions on bridges/tunnels, #2070
- improved resolution of elevation profiles, 3D Ramer-Douglas-Peucker and long edge sampling, #1953

### 1.0 [22 May 2020]

- changes to config.yml:
  * properties have to follow the snake_case, #1918, easy convert via https://github.com/karussell/snake_case
  * all routing profiles have to be configured when setting up GraphHopper, #1958, #1922
  * for developing with JavaScript, npm and the web UI it is important to change your assets overrides to
    web/target/classes/assets/, see #2041
- moved SPTEntry and ShortcutUnpacker to com.graphhopper.routing(.ch)
- renamed PathWrapper to ResponsePath
- removed min_one_way_network_size parameter, #2042
- moved package from core/src/test/java/com/graphhopper/routing/profiles to ev
- removed HintsMap
- removed /change endpoint
- removed vehicle,weighting and edge_based from GraphHopper class, replaced with new profile parameter, #1958
- there no longer is a default vehicle, not setting the vehicle now only works if there is exactly one profile that
  matches the other parameters. anyway you should use the new profile rather than the vehicle parameter.
- removed IPFilter. Use a firewall instead.
- PMap refactored. It is recommended to use putObject(String, Object) instead of put, #1956
- removed UnsafeDataAccess as not maintained, see #1620
- add profiles parameter and replace prepare.ch/lm.weightings and prepare.ch.edge_based with profiles_ch/lm config
  parameters, #1922
- GraphHopper class no longer enables CH by default, #1914
- replaced command line arguments -Dgraphhopper.. with -Ddw.graphhopper.. due to #1879, see also #1897
- GraphHopper.init(CmdArgs)->GraphHopper.init(GraphHopperConfig), see #1879
- removed GenericWeighting and DataFlagEncoder as a normal CarFlagEncoder does the job too. Or use the new
  CustomWeighting see #1841
- remove TurnWeighting, see #1863
- speed up path simplification with path details/instructions, see #1802
- revert compression of landmark preparation data, see #1749 and #1376
- add required EncodedValues like road_class to EncodingManager if not added from user
- removed PathNative,PathBidirRef,Path4CH,EdgeBasedPathCH and moved path extraction code out of Path class, added
  PathExtractor,BidirPathExtractor(+subclasses for CH) instead, #1730
- conditional turn restrictions now supported, #1683
- added new `curbside` feature, #1697
- moved QueryGraph to new routing.querygraph package
- removed GraphExtension, #1783, renamed TurnCostExtension to TurnCostStorage
- removed `heading`, `pass_through` and `ch.force_heading` parameters for speed mode/CH, #1763

### 0.13 [17 Sep 2019]

- removed docker compose file
- PathDetails return null instead of -1 for Infinity by default
- replaced AverageSpeedDetails by DecimalDetails
- moved TagParser into new package: com.graphhopper.routing.util.parsers
- removed FlagEncoderFactory.DEFAULT
- removed forestry and motorroad from RoadClass
- SpatialRule.AccessValue replaced by RoadAccess
- removed EncodedValueLookup.hasEncoder, use hasEncodedValue instead
- removed MappedDecimalEncodedValue: should be replaced with FactoredDecimalEncodedValue
- DataFlagEncoder: it is now required to add EncodedValues before. graph.encoded_values:
  max_speed,road_class,road_environment,road_access
- instead of EncodingManager.ROUNDABOUT use Roundabout.KEY
- changed output format of result=pointlist and moved it into separate endpoint /spt
- removed TraversalMode.EDGE_BASED_1DIR and TraversalMode.EDGE_BASED_2DIR_UTURN, renamed TraversalMode.EDGE_BASED_2DIR
  to TraversalMode.EDGE_BASED
- to prevent u-turns when using edge-based algorithms it is now required to use TurnWeighting, #1640
- GraphHopperStorage.getGraph(Class) was replaced by GraphHopperStorage.getBase/CHGraph(), #1669
- CHGraph.shortcut(int, int) was removed (use .shortcut(int, int, ...) and/or .shortcutEdgeBased(int, int, ...) instead, #1693
- CH graphs are now identified using CHProfile instead of Weighting, #1670
- removed the 'traversal_mode` request parameter for /route, instead of 'traversal_mode=edge_based_2dir' use
  edge_based=true
- removed GraphHopper.set/getTraversalMode() methods, #1705
- edge-based CH is now chosen by default if it was prepared, #1706
- it is now possible to specify finite u-turn costs for CH preparation, #1671
- removed distances from CH shortcuts, reduces memory consumption per shortcut by 4 bytes (about 8-10%), #1719

### 0.12 [25 Mar 2019]

- renamed VirtualEdgeIteratorState.getOriginalEdgeKey to more precise getOriginalEdgeKey #1549
- access refactoring #1436 that moves AccessValue into SpatialRule.Access
- refactoring of EncodingManager to use builder pattern. Migration should be simple. Replace new EncodingManager with
  EncodingManager.crea- The methods GraphHopper.setEnableInstructions/setPreferredLanguage is now in
  EncodingManager.Builder
- EncodingManager.supports renames to hasEncoder
- big refactoring #1447: to increase 64bit limit of flags, make reverse direction handling easier, to allow shared
  EncodedValues, remove reverseFlags method, much simpler property access, simplify FlagEncoder (maybe even deprecate
  this interface at a later stage)
- moved shp-reader into separate repository: https://github.com/graphhopper/graphhopper-reader-shp

### 0.11 [12 Sep 2018]

- web resources for dropwizard web framework (no servlets anymore) #1108
- prefix -Dgraphhopper. for command line arguments necessary, see docs/web/quickstart.md or
  docs/core/quickstart-from-source.md#running--debbuging-with-intellij for details
- delegated reading properties to dropwizard, i.e. the new format yml is not read again in GraphHopper.init
- changed file format for landmarks #1376
- convert properties into new yml format via: https://gist.github.com/karussell/dbc9b4c455bca98b6a38e4a160e23bf8

### 0.10 [26 Feb 2018]

- introduce path details
- added handcoded API java client to this repository

### 0.9 [13 Jun 2017]

- remove war bundling support #297
- rename of DefaultModule to GraphHopperModule and GHServletModule to GraphHopperServletModule
- EncodedValue uses Math.round(value/factor). This can change the retrieved values for EncodedValues #954
- EncodedDoubleValue and EncodedValue requires maxValue/factor to be a natural number #954
- default base algorithm for all modes is bidirectional A* (except speed mode)
- introduced landmarks based hybrid mode, #780
- moving all prepare.xy configs to prepare.ch.xy and e.g. disallow the old
- removed deprecated methods in GraphHopper (setCHWeighting, setCHWeightings, getCHWeightings, setCHWeightings,
  getCHPrepareThreads, setCHPrepareThreads), Path.calcMillis, findID of LocationIndex and all implementations

### 0.8 [18 Oct 2016]

- refactoring to Weighting class, see #807
- removed FlagEncoder from parameters as weighting.getFlagEncoder can and is used
- all properties with prefix "osmreader." changed to "datareader." and osmreader.osm changed to datareader.file
- maven/gradle dependency graphhopper is now split into graphhopper-core and graphhopper-reader-osm, i.e. if you
  previouls depend on 'graphhopper' artificat you should now use graphhopper-reader-osm except environments like Android
  where you just load the graph and do no import
- use GraphHopperOSM as base class instead of GraphHopper
- OSM reader separated from core, use new graphhopper-core package
- moved subnetwork code into own package com.graphhopper.routing.subnetwork
- moved weighting code into own package com.graphhopper.routing.weighting
- code format has changed, so it is important to change your PRs too before you merge master, see discussion #770

### 0.7 [15 Jun 2016]

- added snapped points to output JSON for every path
- the foot routing is now much smoother and only considers safe paths, to use beautiful roads (i.e. prefer hiking routes
  etc) use the new 'hike' profiles, see #633
- vehicle constants have moved to FlagEncoderFactory
- several constants changed to under score notation see #719 with a few breaking changes, e.g. use lower case names for
  flag encoders or jsonp_allowed instead of the jsonpAllowed annotation
- moving all string parameter constants into the Parameters class
- no more acceptedRailways set see #662 for more information
- web API: content type of gpx export is now application/gpx+xml if not explicitly specified
- use prepare.ch.weightings instead of prepare.chWeighting e.g. for disabling CH use prepare.ch.weightings=no
- GraphHopper class is refactored regarding RoutingAlgorithmFactory in order to fix problem when integrating flexibility
  routing, most of the CH related stuff is moved into CHAlgoFactoryDecorator, several methods are deprecated to use the
  methods of the decorator, see #631
- WeightingMap is now named HintsMap
- use the correct graphHopperFolder as no automatic fallback to 'folder-gh' is happening anymore, see #704
- refactored FlagEncoder.handleFerryWay to getFerrySpeed to make it possible to fix #665
- removed setWeightLimit as too unspecific for arbitrary weights, use setMaxVisitedNodes instead
- missing renames for Path.setEdgeEntry -> setSPTEntry and AbstractAlgorithm.createEdgeEntry -> createSPTEntry

### 0.6 [08 Feb 2016]

- removed methods deprecated in 0.4 and 0.5
- renamed EdgeEntry to SPTEntry and AStar.AStarEdge to AStar.AStarEntry
- parameter force removed from AbstractFlagEncoder.applyMaxSpeed
- GHResponse now wraps multiple PathWrapper; renamed GraphHopper.getPaths to calcPaths as 'get' sounded too cheap; a new
  method RoutingAlgorithm.calcPaths is added; see #596
- moving lgpl licensed file into own submodule graphhopper-tools-lgpl
- renaming of Tarjans algorithm class to TarjansSCCAlgorithm
- more strict naming for Weighting enforced and more strict matching to select Weighting (equals check), #490
- specify the preferred-language for way names during graph import (ISO 639-1 or ISO 639-2)
- renaming of getCHWeighting to getCHWeightings due to supporting multiple CH weightings, #623
- deprecation of setCHWeighting, please use setCHWeightings instead, #623

### 0.5 [12 Aug 2015]

- Several names have changed see #466, #467, #468
- GraphHopper.optimize removed use postProcessing instead
- method GraphHopper.getGraph() changed to getGraphHopperStorage()
- the interface GraphStorage does no longer extend from the Graph interface. Use GraphHopperStorage (which implements
  the Graph interface via the base graph) or only Graph instead
- now it is necessary to call baseGraph/chGraph.freeze in order to use the chGraph (to simply determine when an edgeId
  is a shortcut)
- LevelGraphStorage is now a GraphHopperStorage with an additional ch graph (CHGraphImpl)
- GraphHopperStorage implements now the Graph interface and delegates all necessary methods to the underlying base
  graph. To do routing you call getGraph(CHGraph.class or Graph.class) where the parameter Graph.class returns the base
  graph and the behaviour is identical to GraphHopperStorage itself
- renamed LevelGraph* to CHGraph*
- renamed NoGraphExtension to NoOpExtension
- removed visitedNodes method in GraphHopper replaced with per response information: response.getHints().getLong("
  visited_nodes.sum", 0)
- added ability to store hints in GHResponse which will be forwarded to the json too
- breaking change in HTTP API: error JSON format changed to be message:"" instead of within info.errors, see updated api
  documentation
- made GHResponse.getMillis, Path.getMillis, GPXEntry.getMillis deprecated, use getTime instead
- in AbstractFlagEncoder, parse*() and getStr() are now deprecated, use properties.get* instead

### 0.4 [18 Mar 2015]

- translation key turn changed and merged with left etc into turn_left, turn_right etc
- create location index before preparation in the GraphHopper class
- encodingManager.getSingle() is removed and flagEncoder list is no longer sorted, the first vehicle is used for CH
  preparation
- removed LocationIndexTreeSC, use new LocationIndexTree(levelGraph.getBaseGraph(), directory) instead
- getLevel and setLevel do no longer automatically increase node count, use getNodeAccess.ensureNode for that
- normal algorithms are now possible on prepared graph use getBaseGraph, see #116
- GHResponse no longer has isFound method, use !hasErrors instead
- merged unused Edge class into EdgeEntry
- astar and astarbi are now both none-heuristic and take parameters for beeline approximation:
  astar.approximation=BeelineSimplification|BeelineAccurate or astarbi.approximation=...
- making GPX export according to the schema to support import from various tools like basecamp
- refactoring: AllEdgesIterator.getMaxId is now named getCount
- major change of internal API: moved method "Path RoutingAlgorithm.calcPath(QueryResult,QueryResult)" to a helper
  method QueryGraph.lookup, call queryResult.getClosestNode for the calcPath(nodeFrom,nodeTo) method
- no cachedWays and cachedPoints in Path anymore
- Path.findInstruction was moved to InstructionList.find
- if start and end point are identical an algorithm will find the path consisting only of one node, one point and one
  instruction (finish instruction), but without edges
- astarbi has new default values for approximation (false) and approximation_factor (1.2) in
  RoutingAlgorithmFactorySimple
- instead of strings use the variables in AlgorithmOptions to specify an algorithm
- use RoutingAlgorithmFactorySimple instead of RoutingAlgorithmFactory, also more consistent algorithm preparation
  handling due to new AlgorithmOptions, therefor removed NoOpAlgorithmPreparation
- GHResponse.getXX methods now fail fast (throw an exception) if an error while route calculation occurred. See #287
- renamed less often used URL parameter 'min_path_precision' to way_point_max_distance which makes it identical to the
  setWayPointMaxDistance method used for simplification at OSMImport
- removed douglas.minprecision from Java API ghRequest.hints => use wayPointMaxDistance instead
- encoder.supportTurnCost is replaced by encoder.supports(TurnWeighting.class)
- CmdArgs is now a Map<String, String> instead Map<String, Object>. The value will be parsed up on every getXY call,
  makes storing string vs. object less error-prone
- removed GHRequest.getHint, instead use the provided methods in GHRequest.getHints().getXY and GHRequest.getHints().put
- important graph incompatibility as properties cannot be loaded. renamed osmreader.bytesForFlags to
  graph.bytesForFlags, renamed config property osmreader.acceptWay to graph.flagEncoders
- default weighting is now fastest, fixing #261
- moved method GraphHopper.main into tools module and class com.graphhopper.tools.Import, see #250
- refactored GraphHopper.createWeighting to accept more than one configuration option, see #237
- refactored GraphHopper.disableCHShortcuts to setCHEnable(boolean)
- moving the boolean parameter of GraphHopper.setInMemory into a separate method setStoreOnFlush
- renaming of GraphHopper.setCHShortcuts to setCHWeighting, as well as the property prepare.chShortcuts to
  prepare.chWeighting
- jsonp is disabled by default. You need to enable it in the config.properties, see the config-example.properties
- EncodingManager cannot be null in GraphHopperStorage. If you need to parse EncodingManager configuration from existing
  graph use EncodingManager.create
- no reflection done in EncodingManager which improves portability and makes configuration of encoders possible before
  adding to manager
- removed dijkstraNativebi as no performance advantage but maintenance disadvantage and similar to oneToManyDijkstra
- to provide context for turn costs we needed to add prevEdgeId into Weighting.calcWeight, see new documentation
- with the introduction of lock protection mechanism (see #112) GraphHopper needs always write access, see also #217
- new GraphHopper.clean method to remove the graph directory via Java API

### 0.3.0 [13 May 2014]

- introduced prefer bits, now bike uses more bits and 3 bike encoder do not fit into 32 bit anymore, will be fixed later
- moved Translation argument into Path.calcInstruction for more fine grained control, instructions are now uncached and
  GHRequest: new locale parameter
- CoordTrig and the like are removed, GHPlace is mostly replaced by GHPoint and so GHRequest has now methods ala
  addPoint instead
- removed isBoth from AbstractFlagEncoder, moved canBeOverwritten and associated test to PrepareEncoder
- removed unused directory.rename
- refactor edge.copyProperties into copyPropertiesTo to have similar semantics as Graph.copyTo
- calcWeight now contains reverse boolean to calculate correct direction dependent weight
- completely different web API response format. see docs/web
- swapDirections is renamed to reverseFlags (EncodingManager and FlagEncoders)
- edgeState.detach has now a reverse parameter, just use false to get previous results
- web api: buildDate contains now timezone, algoType is replaced with weighting
- dijkstraNative is now dijkstraNativebi
- fixed #151
- calcWeight now contains reverse boolean to calculate correct direction dependent weight
- EncodingManager always takes the encoders in constructor, to force always init
- GraphHopper.setMemory(true, true/false) was refactored to GraphHopper.setMemory(true/false), use mmap config via
  GraphHopper.setMemoryMapped()
- incompatible edges => you need to re-import data and/or update the edges file
- the instructions of the web response does not contain times (string) but instead millis (long)
- PrepareContractionHierarchies.setPeriodicUpdates is now in percentage not in absolute counts
- improved bike routing #132, #138, #139, #150
- gpx export via API, HTTP (route?type=gpx) and web interface is possible: #113, #136, #141

### 0.2.0 [23 Nov 2013]

- change inconsistent default settings for contraction hierarchies in the API - see https://lists.openstreetmap.org/pipermail/graphhopper/2013-December/000585.html
- fixed issues with android:
  * graphhopper: use maps from 0.2 path; updated maps
  * mapsforge: use mapsforge-map dependency; merged #461; avoid duplicates otherwise mapsforge-core would be duplicate (
    ?)
- refactored/renamed classes and methods:
  * refactor 'flags' from int to long (still only int is stored)
  * replacing Graph.edge(a,b,dist,edgeFlags) by Graph.edge(a,b).setDistance().setFlags()
  * FlagEncoder.flags => use FlagEncoder.setProperties or separate setAccess and setSpeed method
  * renamed LocationIDResult to QueryResult and Location2NodesNtree to LocationIndexTree
  * renamed Location2IDIndex to LocationIndex
  * renamed WeightCalculation to Weighting and getWeight to calcWeight, the URL parameter algoType in web module is now
    deprecated and 'weighting' should be used
  * removed GHDijkstraHeap, GHDijkstraHeap2
  * made DistanceCalc into interface (new DistanceCalcEarth implementation)
  * made GraphStorage into interface (new GraphHopperStorage implementation) move some methods from Graph into
    GraphStorage -> optimize + node removal stuff -> not necessary in algorithms
- incompatible storage layout due to: pluggable endianness (#103) -> changed default endianness to LITTLE
- add highly experimental UnsafeDataAccess to speed up search ~15%
- several small bug fixes and improvements
- different edge insert
- important bug fix for edge retrieval which leads to massive speed up in prepare + CH algos
- finally fixed major feature request #27 to allow gps-to-gps queries instead of only junction-to-junction ones.
  * follow up in #52 and #115
  * slower but more precise and necessary edge distance calculation
- fixed bug #105 for disconnected areas
- fix which made CH preparation ~5% faster
- more align API for all algorithms. and initCollection is called via 1000 not something depending on the graph size
- API changed
  * case of vehicle now case does not matter
  * returned distance is in meter now
- better i18n support
- fixed major bug #102 when removing subnetworks
- fixed bug #89 for trams on roads
- completed improvement #93 for ferries
- edge explorer makes none-CH algorithms ~8% faster
- link to all closed issues: https://github.com/graphhopper/graphhopper/issues?milestone=2&state=closed

### 0.1.1 [06 August 2013]

- correct maven bundling and some more issues
- more i18n

### 0.1 [23 July 2013]

- initial version with lots of features
- 24 closed issues: https://github.com/graphhopper/graphhopper/issues?milestone=3&state=closed, e.g.:
  * Route instructions
  * Implement OSM import for bike/foot/car
  * More compact graph (nodes along a way are stored in a separate storage => faster and reduced RAM usage)
  * Made routing working world wide. Make German-sized networks working on Android.
  * Made routing faster via bidirectional algorithms, contraction hierarchies, graph sorting, better OSM integration and
    some fine tuning.
