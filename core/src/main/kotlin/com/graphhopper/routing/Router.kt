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
package com.graphhopper.routing

import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.ResponsePath
import com.graphhopper.config.Profile
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.Subnetwork
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory
import com.graphhopper.routing.lm.LandmarkStorage
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.util.DefaultSnapFilter
import com.graphhopper.routing.util.DirectedEdgeFilter
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.routing.weighting.custom.CustomWeighting
import com.graphhopper.routing.weighting.custom.FindMinMax
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.Graph
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.CustomModel
import com.graphhopper.util.DistanceCalcEarth.Companion.DIST_EARTH
import com.graphhopper.util.Helper
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters
import com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE
import com.graphhopper.util.Parameters.Algorithms.ROUND_TRIP
import com.graphhopper.util.Parameters.Routing.ALGORITHM
import com.graphhopper.util.Parameters.Routing.CURBSIDE
import com.graphhopper.util.Parameters.Routing.CURBSIDE_STRICTNESS
import com.graphhopper.util.Parameters.Routing.ELEVATION_WAY_POINT_MAX_DISTANCE
import com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES
import com.graphhopper.util.Parameters.Routing.PASS_THROUGH
import com.graphhopper.util.Parameters.Routing.POINT_HINT
import com.graphhopper.util.Parameters.Routing.TIMEOUT_MS
import com.graphhopper.util.PathMerger
import com.graphhopper.util.PointList
import com.graphhopper.util.RamerDouglasPeucker
import com.graphhopper.util.StopWatch
import com.graphhopper.util.TranslationMap
import com.graphhopper.util.TurnCostsConfig.INFINITE_U_TURN_COSTS
import com.graphhopper.util.details.PathDetailsBuilderFactory
import com.graphhopper.util.exceptions.PointDistanceExceededException
import com.graphhopper.util.exceptions.PointNotFoundException
import com.graphhopper.util.exceptions.PointOutOfBoundsException
import com.graphhopper.util.shapes.GHPoint

open class Router(
    @JvmField protected val graph: BaseGraph,
    @JvmField protected val encodingManager: EncodingManager,
    @JvmField protected val locationIndex: LocationIndex,
    @JvmField protected val profilesByName: Map<String, Profile>,
    @JvmField protected val pathDetailsBuilderFactory: PathDetailsBuilderFactory,
    @JvmField protected val translationMap: TranslationMap,
    @JvmField protected val routerConfig: RouterConfig,
    @JvmField protected val weightingFactory: WeightingFactory,
    @JvmField protected val chGraphs: Map<String, RoutingCHGraph>,
    @JvmField protected val landmarks: Map<String, LandmarkStorage>
) {
    init {
        for (profile in profilesByName.keys) {
            if (!encodingManager.hasEncodedValue(Subnetwork.key(profile)))
                throw IllegalStateException("The profile '" + profile + "' needs an EncodedValue '" + Subnetwork.key(profile) + "'")
        }
    }

    fun route(request: GHRequest): GHResponse {
        try {
            checkNoLegacyParameters(request)
            checkAtLeastOnePoint(request)
            checkIfPointsAreInBoundsAndNotNull(request.getPoints())
            checkHeadings(request)
            checkPointHints(request)
            checkCurbsides(request)
            checkNoBlockArea(request)
            checkCustomModel(request)

            val solver = createSolver(request)
            solver.checkRequest()
            solver.init()

            return if (ROUND_TRIP.equals(request.getAlgorithm(), ignoreCase = true)) {
                if (solver !is FlexSolver)
                    throw IllegalArgumentException("algorithm=round_trip only works with a flexible algorithm")
                routeRoundTrip(request, solver)
            } else if (ALT_ROUTE.equals(request.getAlgorithm(), ignoreCase = true)) {
                routeAlt(request, solver)
            } else {
                routeVia(request, solver)
            }
        } catch (ex: MultiplePointsNotFoundException) {
            val ghRsp = GHResponse()
            for (p in ex.pointsNotFound) {
                ghRsp.addError(PointNotFoundException("Cannot find point " + p.value + ": " + request.getPoints().get(p.value), p.value))
            }
            return ghRsp
        } catch (ex: IllegalArgumentException) {
            val ghRsp = GHResponse()
            ghRsp.addError(ex)
            return ghRsp
        }
    }

    private fun checkNoLegacyParameters(request: GHRequest) {
        if (request.getHints().has("vehicle"))
            throw IllegalArgumentException("GHRequest may no longer contain a vehicle, use the profile parameter instead, see docs/core/profiles.md")
        if (request.getHints().has("weighting"))
            throw IllegalArgumentException("GHRequest may no longer contain a weighting, use the profile parameter instead, see docs/core/profiles.md")
        if (request.getHints().has(Parameters.Routing.TURN_COSTS))
            throw IllegalArgumentException("GHRequest may no longer contain the turn_costs=true/false parameter, use the profile parameter instead, see docs/core/profiles.md")
        if (request.getHints().has(Parameters.Routing.EDGE_BASED))
            throw IllegalArgumentException("GHRequest may no longer contain the edge_based=true/false parameter, use the profile parameter instead, see docs/core/profiles.md")
    }

    private fun checkAtLeastOnePoint(request: GHRequest) {
        if (request.getPoints().isEmpty())
            throw IllegalArgumentException("You have to pass at least one point")
    }

    private fun checkIfPointsAreInBoundsAndNotNull(points: List<GHPoint?>) {
        val bounds = graph.bounds
        for (i in points.indices) {
            val point = points[i]
                ?: throw IllegalArgumentException("Point $i is null")
            if (!bounds.contains(point.lat, point.lon))
                throw PointOutOfBoundsException("Point $i is out of bounds: $point, the bounds are: $bounds", i)
        }
    }

    private fun checkHeadings(request: GHRequest) {
        if (request.getHeadings().size > 1 && request.getHeadings().size != request.getPoints().size)
            throw IllegalArgumentException("The number of 'heading' parameters must be zero, one "
                    + "or equal to the number of points (" + request.getPoints().size + ")")
        for (i in request.getHeadings().indices)
            if (!GHRequest.isAzimuthValue(request.getHeadings().get(i)))
                throw IllegalArgumentException("Heading for point " + i + " must be in range [0,360) or NaN, but was: " + request.getHeadings().get(i))
    }

    private fun checkPointHints(request: GHRequest) {
        if (!request.getPointHints().isEmpty() && request.getPointHints().size != request.getPoints().size)
            throw IllegalArgumentException("If you pass $POINT_HINT, you need to pass exactly one hint for every point, empty hints will be ignored")
    }

    private fun checkCurbsides(request: GHRequest) {
        if (!request.getCurbsides().isEmpty() && request.getCurbsides().size != request.getPoints().size)
            throw IllegalArgumentException("If you pass $CURBSIDE, you need to pass exactly one curbside for every point, empty curbsides will be ignored")
    }

    private fun checkNoBlockArea(request: GHRequest) {
        if (request.getHints().has("block_area"))
            throw IllegalArgumentException("The `block_area` parameter is no longer supported. Use a custom model with `areas` instead.")
    }

    private fun checkCustomModel(request: GHRequest) {
        if (request.getCustomModel() != null && request.getCustomModel().isInternal())
            throw IllegalArgumentException("CustomModel of query cannot be internal")
    }

    protected open fun createSolver(request: GHRequest): Solver {
        return if (chGraphs.containsKey(request.getProfile()) && !getDisableCH(request.getHints())) {
            createCHSolver(request, profilesByName, routerConfig, encodingManager, chGraphs)
        } else if (landmarks.containsKey(request.getProfile()) && !getDisableLM(request.getHints())) {
            createLMSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, graph, locationIndex, landmarks)
        } else {
            createFlexSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, graph, locationIndex)
        }
    }

    protected open fun createCHSolver(request: GHRequest, profilesByName: Map<String, Profile>, routerConfig: RouterConfig,
                                      encodingManager: EncodingManager, chGraphs: Map<String, RoutingCHGraph>): Solver {
        return CHSolver(request, profilesByName, routerConfig, encodingManager, chGraphs)
    }

    protected open fun createLMSolver(request: GHRequest, profilesByName: Map<String, Profile>, routerConfig: RouterConfig,
                                      encodingManager: EncodingManager, weightingFactory: WeightingFactory, baseGraph: BaseGraph,
                                      locationIndex: LocationIndex, landmarks: Map<String, LandmarkStorage>): Solver {
        return LMSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, baseGraph, locationIndex, landmarks)
    }

    protected open fun createFlexSolver(request: GHRequest, profilesByName: Map<String, Profile>, routerConfig: RouterConfig,
                                        encodingManager: EncodingManager, weightingFactory: WeightingFactory, baseGraph: BaseGraph,
                                        locationIndex: LocationIndex): Solver {
        return FlexSolver(request, profilesByName, routerConfig, encodingManager, weightingFactory, baseGraph, locationIndex)
    }

    protected open fun routeRoundTrip(request: GHRequest, solver: FlexSolver): GHResponse {
        val ghRsp = GHResponse()
        val sw = StopWatch().start()
        val startHeading = if (request.getHeadings().isEmpty()) Double.NaN else request.getHeadings().get(0)
        val params = RoundTripRouting.Params(request.getHints(), startHeading, routerConfig.getMaxRoundTripRetries())
        val snaps = RoundTripRouting.lookup(request.getPoints(), solver.createSnapFilter(), locationIndex, params)
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s")

        val queryGraph = QueryGraph.create(graph, snaps)
        val pathCalculator = solver.createPathCalculator(queryGraph)

        val result = RoundTripRouting.calcPaths(snaps, pathCalculator)
        // we merge the different legs of the roundtrip into one response path
        // note that the waypoints are not just the snapped points of the snaps, as usual, because we do some kind of tweak
        // to avoid 'unnecessary tails' in the roundtrip algo
        val responsePath = concatenatePaths(request, solver.weighting, queryGraph, result.paths, result.wayPoints!!)
        ghRsp.add(responsePath)
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes)
        ghRsp.getHints().putObject("visited_nodes.average", result.visitedNodes.toFloat() / (snaps.size - 1))
        return ghRsp
    }

    protected open fun routeAlt(request: GHRequest, solver: Solver): GHResponse {
        if (request.getPoints().size > 2)
            throw IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + request.getPoints().size + " points")
        val ghRsp = GHResponse()
        val sw = StopWatch().start()
        val directedEdgeFilter = solver.createDirectedEdgeFilter()
        val snaps = ViaRouting.lookup(encodingManager, request.getPoints(), solver.createSnapFilter(), locationIndex,
                request.getSnapPreventions(), request.getPointHints(), directedEdgeFilter, request.getHeadings())
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s")
        val queryGraph = QueryGraph.create(graph, snaps)
        val pathCalculator = solver.createPathCalculator(queryGraph)
        val passThrough = getPassThrough(request.getHints())
        val curbsideStrictness = getCurbsideStrictness(request.getHints())
        if (passThrough)
            throw IllegalArgumentException("Alternative paths and $PASS_THROUGH at the same time is currently not supported")
        if (!request.getCurbsides().isEmpty())
            throw IllegalArgumentException("Alternative paths do not support the $CURBSIDE parameter yet")

        val result = ViaRouting.calcPaths(request.getPoints(), queryGraph, snaps, directedEdgeFilter,
                pathCalculator, request.getCurbsides(), curbsideStrictness, request.getHeadings(), passThrough, encodingManager)
        if (result.paths.isEmpty())
            throw RuntimeException("Empty paths for alternative route calculation not expected")

        // each path represents a different alternative and we do the path merging for each of them
        val pathMerger = createPathMerger(request, solver.weighting, queryGraph)
        for (path in result.paths) {
            val waypoints = getWaypoints(snaps)
            val responsePath = pathMerger.doWork(waypoints, listOf(path), encodingManager, translationMap.getWithFallBack(request.getLocale()))
            ghRsp.add(responsePath)
        }
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes)
        ghRsp.getHints().putObject("visited_nodes.average", result.visitedNodes.toFloat() / (snaps.size - 1))
        return ghRsp
    }

    protected open fun routeVia(request: GHRequest, solver: Solver): GHResponse {
        val ghRsp = GHResponse()
        val sw = StopWatch().start()
        val directedEdgeFilter = solver.createDirectedEdgeFilter()
        val snaps = ViaRouting.lookup(encodingManager, request.getPoints(), solver.createSnapFilter(), locationIndex,
                request.getSnapPreventions(), request.getPointHints(), directedEdgeFilter, request.getHeadings())
        ghRsp.addDebugInfo("idLookup:" + sw.stop().getSeconds() + "s")
        // (base) query graph used to resolve headings, curbsides etc. this is not necessarily the same thing as
        // the (possibly implementation specific) query graph used by PathCalculator
        val queryGraph = QueryGraph.create(graph, snaps)
        val pathCalculator = solver.createPathCalculator(queryGraph)
        val passThrough = getPassThrough(request.getHints())
        val curbsideStrictness = getCurbsideStrictness(request.getHints())
        val result = ViaRouting.calcPaths(request.getPoints(), queryGraph, snaps, directedEdgeFilter,
                pathCalculator, request.getCurbsides(), curbsideStrictness, request.getHeadings(), passThrough, encodingManager)

        if (request.getPoints().size != result.paths.size + 1)
            throw RuntimeException("There should be exactly one more point than paths. points:" + request.getPoints().size + ", paths:" + result.paths.size)

        // here each path represents one leg of the via-route and we merge them all together into one response path
        val responsePath = concatenatePaths(request, solver.weighting, queryGraph, result.paths, getWaypoints(snaps))
        responsePath.addDebugInfo(result.debug)
        ghRsp.add(responsePath)
        ghRsp.getHints().putObject("visited_nodes.sum", result.visitedNodes)
        ghRsp.getHints().putObject("visited_nodes.average", result.visitedNodes.toFloat() / (snaps.size - 1))
        return ghRsp
    }

    private fun createPathMerger(request: GHRequest, weighting: Weighting, graph: Graph): PathMerger {
        val enableInstructions = request.getHints().getBool(Parameters.Routing.INSTRUCTIONS, routerConfig.isInstructionsEnabled())
        val enableViaPointInstructions = request.getHints().getBool(Parameters.Routing.VIA_POINT_INSTRUCTIONS, routerConfig.isViaPointInstructionsEnabled())
        val calcPoints = request.getHints().getBool(Parameters.Routing.CALC_POINTS, routerConfig.isCalcPoints())
        val wayPointMaxDistance = request.getHints().getDouble(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0.5)
        val elevationWayPointMaxDistance = request.getHints().getDouble(ELEVATION_WAY_POINT_MAX_DISTANCE, routerConfig.getElevationWayPointMaxDistance())

        val peucker = RamerDouglasPeucker()
            .setMaxDistance(wayPointMaxDistance)
            .setElevationMaxDistance(elevationWayPointMaxDistance)
        val pathMerger = PathMerger(graph, weighting)
            .setCalcPoints(calcPoints)
            .setRamerDouglasPeucker(peucker)
            .setEnableInstructions(enableInstructions)
            .setEnableViaPointInstructions(enableViaPointInstructions)
            .setPathDetailsBuilders(pathDetailsBuilderFactory, request.getPathDetails())
            .setSimplifyResponse(routerConfig.isSimplifyResponse() && wayPointMaxDistance > 0)

        if (!request.getHeadings().isEmpty())
            pathMerger.setFavoredHeading(request.getHeadings().get(0))
        return pathMerger
    }

    private fun concatenatePaths(request: GHRequest, weighting: Weighting, queryGraph: QueryGraph, paths: List<Path>, waypoints: PointList): ResponsePath {
        val pathMerger = createPathMerger(request, weighting, queryGraph)
        return pathMerger.doWork(waypoints, paths, encodingManager, translationMap.getWithFallBack(request.getLocale()))
    }

    private fun getWaypoints(snaps: List<Snap>): PointList {
        val pointList = PointList(snaps.size, graph.nodeAccess.is3D())
        for (snap in snaps) {
            pointList.add(snap.getSnappedPoint())
        }
        return pointList
    }

    companion object {
        private fun getDisableLM(hints: PMap): Boolean = hints.getBool(Parameters.Landmark.DISABLE, false)

        private fun getDisableCH(hints: PMap): Boolean = hints.getBool(Parameters.CH.DISABLE, false)

        private fun getPassThrough(hints: PMap): Boolean = hints.getBool(PASS_THROUGH, false)

        private fun getCurbsideStrictness(hints: PMap): String {
            if (hints.has(CURBSIDE_STRICTNESS)) return hints.getString(CURBSIDE_STRICTNESS, "strict")

            // legacy
            return if (hints.getBool("force_curbside", true)) "strict" else "soft"
        }
    }

    // note: in the Java original 'checkRequest', 'init', 'createSnapFilter', 'createDirectedEdgeFilter',
    // 'createPathCalculator' and the 'weighting' field were protected/private and accessed by the outer Router
    // class (allowed in Java for nested classes). Kotlin has no package/outer-access for protected members, so
    // these are public here (init is internal); overridability is unchanged.
    abstract class Solver(
        @JvmField protected val request: GHRequest,
        private val profilesByName: Map<String, Profile>,
        private val routerConfig: RouterConfig,
        @JvmField protected val lookup: EncodedValueLookup
    ) {
        // nullable field (not lateinit) because the getProfile() method must keep its JVM signature,
        // which would clash with a property getter
        @JvmField
        protected var profile: Profile? = null

        lateinit var weighting: Weighting

        open fun checkRequest() {
            checkProfileSpecified()
            checkMaxVisitedNodes()
        }

        private fun checkProfileSpecified() {
            if (Helper.isEmpty(request.getProfile()))
                throw IllegalArgumentException("You need to specify a profile to perform a routing request, see docs/core/profiles.md")
        }

        private fun checkMaxVisitedNodes() {
            if (getMaxVisitedNodes(request.getHints()) > routerConfig.getMaxVisitedNodes())
                throw IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + routerConfig.getMaxVisitedNodes())
        }

        internal fun init() {
            profile = getProfile()
            checkProfileCompatibility()
            weighting = createWeighting()
        }

        protected open fun getProfile(): Profile {
            return profilesByName[request.getProfile()]
                ?: throw IllegalArgumentException("The requested profile '" + request.getProfile() + "' does not exist.\nAvailable profiles: " + profilesByName.keys)
        }

        protected open fun checkProfileCompatibility() {
            if (!profile!!.hasTurnCosts() && !request.getCurbsides().isEmpty())
                throw IllegalArgumentException("To make use of the " + CURBSIDE + " parameter you need to use a profile that supports turn costs" +
                        "\nThe following profiles do support turn costs: " + getTurnCostProfiles())
            if (request.getCustomModel() != null && CustomWeighting.NAME != profile!!.getWeighting())
                throw IllegalArgumentException("The requested profile '" + request.getProfile() + "' cannot be used with `custom_model`, because it has weighting=" + profile!!.getWeighting())

            val uTurnCostsInt = request.getHints().getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS)
            if (uTurnCostsInt != INFINITE_U_TURN_COSTS && !profile!!.hasTurnCosts()) {
                throw IllegalArgumentException("Finite u-turn costs can only be used for edge-based routing, you need to use a profile that" +
                        " supports turn costs. Currently the following profiles that support turn costs are available: " + getTurnCostProfiles())
            }
        }

        protected abstract fun createWeighting(): Weighting

        open fun createSnapFilter(): EdgeFilter {
            return DefaultSnapFilter(weighting, lookup.getBooleanEncodedValue(Subnetwork.key(profile!!.getName()!!)))
        }

        open fun createDirectedEdgeFilter(): DirectedEdgeFilter {
            val inSubnetworkEnc: BooleanEncodedValue = lookup.getBooleanEncodedValue(Subnetwork.key(profile!!.getName()!!))
            return DirectedEdgeFilter { edgeState, reverse -> !edgeState.get(inSubnetworkEnc) && weighting.calcEdgeWeight(edgeState, reverse).isFinite() }
        }

        abstract fun createPathCalculator(queryGraph: QueryGraph): PathCalculator

        private fun getTurnCostProfiles(): List<String> {
            val turnCostProfiles = ArrayList<String>()
            for (p in profilesByName.values) {
                if (p.hasTurnCosts()) {
                    turnCostProfiles.add(p.getName()!!)
                }
            }
            return turnCostProfiles
        }

        internal fun getMaxVisitedNodes(hints: PMap): Int {
            return hints.getInt(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes())
        }

        internal fun getTimeoutMillis(hints: PMap): Long {
            // we silently use the minimum between the requested timeout and the server-side limit
            // see: https://github.com/graphhopper/graphhopper/pull/2795#discussion_r1168371343
            return Math.min(routerConfig.getTimeoutMillis(), hints.getLong(TIMEOUT_MS, routerConfig.getTimeoutMillis()))
        }
    }

    private class CHSolver(
        request: GHRequest, profilesByName: Map<String, Profile>, routerConfig: RouterConfig,
        lookup: EncodedValueLookup, private val chGraphs: Map<String, RoutingCHGraph>
    ) : Solver(request, profilesByName, routerConfig, lookup) {

        override fun checkRequest() {
            super.checkRequest()
            if (!request.getHeadings().isEmpty())
                throw IllegalArgumentException("The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483")

            if (getPassThrough(request.getHints()))
                throw IllegalArgumentException("The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765")

            if (request.getCustomModel() != null)
                throw IllegalArgumentException("The 'custom_model' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`.")

            if (ROUND_TRIP.equals(request.getAlgorithm(), ignoreCase = true))
                throw IllegalArgumentException("algorithm=round_trip cannot be used with CH")
        }

        override fun createWeighting(): Weighting {
            // todo: do not allow things like short_fastest.distance_factor or u_turn_costs unless CH is disabled
            // and only under certain conditions for LM

            // the request hints are ignored for CH as we cannot change the profile after the preparation like this.
            // the weighting here needs to be the same as the one we later use for CHPathCalculator and as it was
            // used for the preparation
            return getRoutingCHGraph(profile!!.getName()!!).weighting
        }

        override fun createPathCalculator(queryGraph: QueryGraph): PathCalculator {
            val opts = PMap(request.getHints())
            opts.putObject(ALGORITHM, request.getAlgorithm())
            opts.putObject(MAX_VISITED_NODES, getMaxVisitedNodes(request.getHints()))
            opts.putObject(TIMEOUT_MS, getTimeoutMillis(request.getHints()))
            return CHPathCalculator(CHRoutingAlgorithmFactory(getRoutingCHGraph(profile!!.getName()!!), queryGraph), opts)
        }

        private fun getRoutingCHGraph(profileName: String): RoutingCHGraph {
            return chGraphs[profileName]
                ?: throw IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + profileName + "'" +
                        "\nYou can try disabling CH using " + Parameters.CH.DISABLE + "=true" +
                        "\navailable CH profiles: " + chGraphs.keys)
        }
    }

    // note: the constructor was protected in the Java original, but the outer Router class must call it (Java
    // grants outer classes access to protected members of nested classes, Kotlin does not) -> internal
    open class FlexSolver internal constructor(
        request: GHRequest, profilesByName: Map<String, Profile>,
        @JvmField protected val routerConfig: RouterConfig,
        lookup: EncodedValueLookup,
        private val weightingFactory: WeightingFactory,
        private val baseGraph: BaseGraph,
        private val locationIndex: LocationIndex
    ) : Solver(request, profilesByName, routerConfig, lookup) {

        override fun checkRequest() {
            super.checkRequest()
            checkNonChMaxWaypointDistance(request.getPoints())
        }

        override fun createWeighting(): Weighting {
            val requestHints = PMap(request.getHints())
            requestHints.putObject(CustomModel.KEY, request.getCustomModel())
            return weightingFactory.createWeighting(profile!!, requestHints, false)
        }

        override fun createPathCalculator(queryGraph: QueryGraph): FlexiblePathCalculator {
            val algorithmFactory: RoutingAlgorithmFactory = RoutingAlgorithmFactorySimple()
            return FlexiblePathCalculator(queryGraph, algorithmFactory, weighting, getAlgoOpts())
        }

        protected fun getAlgoOpts(): AlgorithmOptions {
            val algoOpts = AlgorithmOptions()
                .setAlgorithm(request.getAlgorithm())
                .setTraversalMode(if (profile!!.hasTurnCosts()) TraversalMode.EDGE_BASED else TraversalMode.NODE_BASED)
                .setMaxVisitedNodes(getMaxVisitedNodes(request.getHints()))
                .setTimeoutMillis(getTimeoutMillis(request.getHints()))
                .setHints(request.getHints())

            // use A* for round trips
            if (ROUND_TRIP.equals(request.getAlgorithm(), ignoreCase = true)) {
                algoOpts.setAlgorithm(Parameters.Algorithms.ASTAR_BI)
                algoOpts.getHints().putObject(Parameters.Algorithms.AStarBi.EPSILON, 2)
            }
            return algoOpts
        }

        private fun checkNonChMaxWaypointDistance(points: List<GHPoint>) {
            if (routerConfig.getNonChMaxWaypointDistance() == Int.MAX_VALUE) {
                return
            }
            var lastPoint = points[0]
            var point: GHPoint
            var dist: Double
            for (i in 1 until points.size) {
                point = points[i]
                dist = DIST_EARTH.calcDist(lastPoint.lat, lastPoint.lon, point.lat, point.lon)
                if (dist > routerConfig.getNonChMaxWaypointDistance()) {
                    val detailMap = HashMap<String, Any>(2)
                    detailMap["from"] = i - 1
                    detailMap["to"] = i
                    throw PointDistanceExceededException("Point " + i + " is too far from Point " + (i - 1) + ": " + point, detailMap)
                }
                lastPoint = point
            }
        }
    }

    private class LMSolver(
        request: GHRequest, profilesByName: Map<String, Profile>, routerConfig: RouterConfig, lookup: EncodedValueLookup,
        weightingFactory: WeightingFactory, graph: BaseGraph, locationIndex: LocationIndex,
        private val landmarks: Map<String, LandmarkStorage>
    ) : FlexSolver(request, profilesByName, routerConfig, lookup, weightingFactory, graph, locationIndex) {

        override fun createPathCalculator(queryGraph: QueryGraph): FlexiblePathCalculator {
            // for now do not allow mixing CH&LM #1082,#1889
            val landmarkStorage = landmarks[profile!!.getName()]
                ?: throw IllegalArgumentException("Cannot find LM preparation for the requested profile: '" + profile!!.getName() + "'" +
                        "\nYou can try disabling LM using " + Parameters.Landmark.DISABLE + "=true" +
                        "\navailable LM profiles: " + landmarks.keys)
            if (request.getCustomModel() != null)
                FindMinMax.checkLMConstraints(profile!!.getCustomModel(), request.getCustomModel(), lookup)
            val routingAlgorithmFactory: RoutingAlgorithmFactory = LMRoutingAlgorithmFactory(landmarkStorage).setDefaultActiveLandmarks(routerConfig.getActiveLandmarkCount())
            return FlexiblePathCalculator(queryGraph, routingAlgorithmFactory, weighting, getAlgoOpts())
        }
    }
}
