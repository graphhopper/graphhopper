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
package com.graphhopper.util

import com.bedatadriven.jackson.datatype.jts.JtsModule
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.IntIndexedContainer
import com.fasterxml.jackson.databind.ObjectMapper
import com.graphhopper.jackson.Jackson
import com.graphhopper.routing.Path
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.State
import com.graphhopper.routing.util.AccessFilter
import com.graphhopper.routing.util.CustomArea
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.storage.RoutingCHEdgeIterator
import com.graphhopper.storage.TurnCostStorage
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.Helper.readJSONFileWithoutComments
import com.graphhopper.util.shapes.BBox
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Random
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A helper class to avoid cluttering the Graph interface with all the common methods. Most of the
 * methods are useful for unit tests or debugging only.
 *
 * @author Peter Karich
 */
object GHUtility {
    @JvmField
    val OSM_WARNING_LOGGER: Logger = LoggerFactory.getLogger("com.graphhopper.osm_warnings")
    private val LOGGER = LoggerFactory.getLogger(GHUtility::class.java)

    /**
     * This method could throw an exception if problems like index out of bounds etc
     */
    @JvmStatic
    fun getProblems(g: Graph): List<String> {
        val problems = ArrayList<String>()
        val nodes = g.nodes
        var nodeIndex = 0
        val na = g.nodeAccess
        try {
            val explorer = g.createEdgeExplorer()
            while (nodeIndex < nodes) {
                val lat = na.getLat(nodeIndex)
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds $lat")

                val lon = na.getLon(nodeIndex)
                if (lon > 180 || lon < -180)
                    problems.add("longitude is not within its bounds $lon")

                val iter = explorer.setBaseNode(nodeIndex)
                while (iter.next()) {
                    if (iter.adjNode >= nodes) {
                        problems.add("edge of " + nodeIndex + " has a node " + iter.adjNode + " greater or equal to getNodes")
                    }
                    if (iter.adjNode < 0) {
                        problems.add("edge of " + nodeIndex + " has a negative node " + iter.adjNode)
                    }
                }
                nodeIndex++
            }
        } catch (ex: Exception) {
            throw RuntimeException("problem with node $nodeIndex", ex)
        }

//        for (int i = 0; i < nodes; i++) {
//            new BreadthFirstSearch().start(g, i);
//        }
        return problems
    }

    /**
     * Counts reachable edges.
     */
    @JvmStatic
    fun count(iter: EdgeIterator): Int {
        var counter = 0
        while (iter.next()) {
            counter++
        }
        return counter
    }

    @JvmStatic
    fun count(iter: RoutingCHEdgeIterator): Int {
        var counter = 0
        while (iter.next()) {
            counter++
        }
        return counter
    }

    @JvmStatic
    fun asSet(vararg values: Int): Set<Int> {
        val s = HashSet<Int>()
        for (v in values) {
            s.add(v)
        }
        return s
    }

    @JvmStatic
    fun getNeighbors(iter: RoutingCHEdgeIterator): Set<Int> {
        // make iteration order over set static => linked
        val list = LinkedHashSet<Int>()
        while (iter.next()) {
            list.add(iter.adjNode)
        }
        return list
    }

    @JvmStatic
    fun getNeighbors(iter: EdgeIterator): Set<Int> {
        // make iteration order over set static => linked
        val list = LinkedHashSet<Int>()
        while (iter.next()) {
            list.add(iter.adjNode)
        }
        return list
    }

    @JvmStatic
    fun getEdgeIds(iter: EdgeIterator): List<Int> {
        val list = ArrayList<Int>()
        while (iter.next()) {
            list.add(iter.edge)
        }
        return list
    }

    @JvmStatic
    fun printGraphForUnitTest(g: Graph, speedEnc: DecimalEncodedValue) {
        printGraphForUnitTest(g, speedEnc, BBox(
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
    }

    @JvmStatic
    fun printGraphForUnitTest(g: Graph, speedEnc: DecimalEncodedValue, bBox: BBox) {
        val na = g.nodeAccess
        for (node in 0 until g.nodes) {
            if (bBox.contains(na.getLat(node), na.getLon(node))) {
                System.out.printf(Locale.ROOT, "na.setNode(%d, %f, %f);\n", node, na.getLat(node), na.getLon(node))
            }
        }
        val iter = g.allEdges
        while (iter.next()) {
            if (bBox.contains(na.getLat(iter.baseNode), na.getLon(iter.baseNode)) &&
                    bBox.contains(na.getLat(iter.adjNode), na.getLon(iter.adjNode))) {
                printUnitTestEdge(speedEnc, iter)
            }
        }
    }

    private fun printUnitTestEdge(speedEnc: DecimalEncodedValue, edge: EdgeIteratorState) {
        val fwd = edge.get(speedEnc) > 0
        val from = if (fwd) edge.baseNode else edge.adjNode
        val to = if (fwd) edge.adjNode else edge.baseNode
        System.out.printf(Locale.ROOT,
                "graph.edge(%d, %d).setDistance(%f).set(speedEnc, %f, %f); // edgeId=%s\n",
                from, to, edge.distance, edge.get(speedEnc), edge.getReverse(speedEnc),
                edge.edge)
    }

    /**
     * @param speed if null a random speed will be assigned to every edge
     */
    @JvmStatic
    fun buildRandomGraph(graph: Graph, random: Random, numNodes: Int, meanDegree: Double,
                         allowZeroDistance: Boolean, speedEnc: DecimalEncodedValue?, speed: Double?,
                         pBothDir: Double, pRandomDistanceOffset: Double) {
        if (numNodes < 2 || meanDegree < 1) {
            throw IllegalArgumentException("numNodes must be >= 2, meanDegree >= 1")
        }
        for (i in 0 until numNodes) {
            val lat = 49.4 + (random.nextDouble() * 0.01)
            val lon = 9.7 + (random.nextDouble() * 0.01)
            graph.nodeAccess.setNode(i, lat, lon)
        }
        var minDist = Double.MAX_VALUE
        var maxDist = Double.MIN_VALUE
        val totalNumEdges = (0.5 * meanDegree * numNodes).toInt()
        var numEdges = 0
        while (numEdges < totalNumEdges) {
            val from = random.nextInt(numNodes)
            val to = random.nextInt(numNodes)
            if (from == to)
                continue
            var distance = getDistance(from, to, graph.nodeAccess)
            if (!allowZeroDistance) {
                distance = max(0.001, distance)
            }
            // add some random offset, but also allow duplicate edges with same weight
            if (random.nextDouble() < pRandomDistanceOffset)
                distance += random.nextDouble() * distance * 0.01
            minDist = min(minDist, distance)
            maxDist = max(maxDist, distance)
            // using bidirectional edges will increase mean degree of graph above given value
            val bothDirections = random.nextDouble() < pBothDir
            val edge = graph.edge(from, to).setDistance(distance)
            var fwdSpeed = 10 + random.nextDouble() * 110
            var bwdSpeed = 10 + random.nextDouble() * 110
            // if an explicit speed is given we discard the random speeds and use the given one instead
            if (speed != null) {
                fwdSpeed = speed
                bwdSpeed = speed
            }
            if (speedEnc != null) {
                edge.set(speedEnc, fwdSpeed)
                if (speedEnc.isStoreTwoDirections)
                    edge.setReverse(speedEnc, if (!bothDirections) 0.0 else bwdSpeed)
            }
            numEdges++
        }
        LOGGER.debug(String.format(Locale.ROOT, "Finished building random graph" +
                ", nodes: %d, edges: %d , min distance: %.2f, max distance: %.2f\n",
                graph.nodes, graph.edges, minDist, maxDist))
    }

    @JvmStatic
    fun getDistance(from: Int, to: Int, nodeAccess: NodeAccess): Double {
        val fromLat = nodeAccess.getLat(from)
        val fromLon = nodeAccess.getLon(from)
        val toLat = nodeAccess.getLat(to)
        val toLon = nodeAccess.getLon(to)
        return DistancePlaneProjection.DIST_PLANE.calcDist(fromLat, fromLon, toLat, toLon)
    }

    @JvmStatic
    fun addRandomTurnCosts(graph: Graph, seed: Long, accessEnc: BooleanEncodedValue?, turnCostEnc: DecimalEncodedValue,
                           maxTurnCost: Int, turnCostStorage: TurnCostStorage) {
        val random = Random(seed)
        val pNodeHasTurnCosts = 0.3
        val pEdgePairHasTurnCosts = 0.6
        val pCostIsRestriction = 0.1

        val inExplorer = graph.createEdgeExplorer(if (accessEnc == null) EdgeFilter { true } else AccessFilter.inEdges(accessEnc))
        val outExplorer = graph.createEdgeExplorer(if (accessEnc == null) EdgeFilter { true } else AccessFilter.outEdges(accessEnc))
        for (node in 0 until graph.nodes) {
            if (random.nextDouble() < pNodeHasTurnCosts) {
                val inIter = inExplorer.setBaseNode(node)
                while (inIter.next()) {
                    val outIter = outExplorer.setBaseNode(node)
                    while (outIter.next()) {
                        if (inIter.edge == outIter.edge) {
                            // leave u-turns as they are
                            continue
                        }
                        if (random.nextDouble() < pEdgePairHasTurnCosts) {
                            val cost = if (random.nextDouble() < pCostIsRestriction) Double.POSITIVE_INFINITY else random.nextDouble() * maxTurnCost
                            turnCostStorage.set(turnCostEnc, inIter.edge, node, outIter.edge, cost)
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun createRandomSnaps(bbox: BBox, locationIndex: LocationIndex, rnd: Random, numPoints: Int, acceptTower: Boolean,
                          filter: EdgeFilter): List<Snap> {
        val maxTries = numPoints * 100
        var tries = 0
        val snaps = ArrayList<Snap>(numPoints)
        while (snaps.size < numPoints) {
            if (tries > maxTries)
                throw IllegalArgumentException("Could not create " + numPoints + " random points. tries: " + tries + ", maxTries: " + maxTries)
            val snap = getRandomSnap(locationIndex, rnd, bbox, filter)
            var accepted = snap.isValid
            if (!acceptTower)
                accepted = accepted && snap.snappedPosition != Snap.Position.TOWER
            if (accepted)
                snaps.add(snap)
            tries++
        }
        return snaps
    }

    @JvmStatic
    fun getRandomSnap(locationIndex: LocationIndex, rnd: Random, bbox: BBox, filter: EdgeFilter): Snap {
        return locationIndex.findClosest(
                randomDoubleInRange(rnd, bbox.minLat, bbox.maxLat),
                randomDoubleInRange(rnd, bbox.minLon, bbox.maxLon),
                filter
        )
    }

    @JvmStatic
    fun randomDoubleInRange(rnd: Random, min: Double, max: Double): Double {
        return min + rnd.nextDouble() * (max - min)
    }

    @JvmStatic
    fun getAdjNode(g: Graph, edge: Int, adjNode: Int): Int {
        if (EdgeIterator.Edge.isValid(edge)) {
            val iterTo = g.getEdgeIteratorState(edge, adjNode)!!
            return iterTo.adjNode
        }
        return adjNode
    }

    @JvmStatic
    fun checkDAVersion(name: String, expectedVersion: Int, version: Int) {
        if (version != expectedVersion) {
            throw IllegalStateException("Unexpected version for '" + name + "'. Got: " + version + ", " +
                    "expected: " + expectedVersion + ". "
                    + "Make sure you are using the same GraphHopper version for reading the files that was used for creating them. "
                    + "See https://discuss.graphhopper.com/t/722")
        }
    }

    /**
     * @return the edge between base and adj, or null if there is no such edge
     * @throws IllegalArgumentException when there are multiple edges
     */
    @JvmStatic
    fun getEdge(graph: Graph, base: Int, adj: Int): EdgeIteratorState? {
        val explorer = graph.createEdgeExplorer()
        val count = count(explorer.setBaseNode(base), adj)
        if (count > 1)
            throw IllegalArgumentException("There are multiple edges between nodes $base and $adj")
        else if (count == 0)
            return null
        val iter = explorer.setBaseNode(base)
        while (iter.next()) {
            if (iter.adjNode == adj)
                return iter
        }
        throw IllegalStateException("There should be an edge")
    }

    /**
     * @return the number of edges with the given adj node
     */
    @JvmStatic
    fun count(iterator: EdgeIterator, adj: Int): Int {
        var count = 0
        while (iterator.next()) {
            if (iterator.adjNode == adj)
                count++
        }
        return count
    }

    /**
     * Creates an edge key, i.e. an integer number that encodes an edge ID and the direction of an edge
     */
    @JvmStatic
    fun createEdgeKey(edgeId: Int, reverse: Boolean): Int {
        // edge state in storage direction -> edge key is even
        // edge state against storage direction -> edge key is odd
        return (edgeId shl 1) + (if (reverse) 1 else 0)
    }

    /**
     * Returns the edgeKey of the opposite direction
     */
    @JvmStatic
    fun reverseEdgeKey(edgeKey: Int): Int = edgeKey xor 1

    /**
     * @return edge ID for edgeKey
     */
    @JvmStatic
    fun getEdgeFromEdgeKey(edgeKey: Int): Int = edgeKey / 2

    /**
     * @return the common node of two edges
     * @throws IllegalArgumentException if one of the edges doesn't exist or is a loop or the edges
     *                                  aren't connected at exactly one distinct node
     */
    @JvmStatic
    fun getCommonNode(baseGraph: BaseGraph, edge1: Int, edge2: Int): Int {
        val e1 = baseGraph.getEdgeIteratorState(edge1, Int.MIN_VALUE)!!
        val e2 = baseGraph.getEdgeIteratorState(edge2, Int.MIN_VALUE)!!
        if (e1.baseNode == e1.adjNode)
            throw IllegalArgumentException("edge1: " + edge1 + " is a loop at node " + e1.baseNode)
        if (e2.baseNode == e2.adjNode)
            throw IllegalArgumentException("edge2: " + edge2 + " is a loop at node " + e2.baseNode)

        if ((e1.baseNode == e2.baseNode && e1.adjNode == e2.adjNode) || (e1.baseNode == e2.adjNode && e1.adjNode == e2.baseNode))
            throw IllegalArgumentException("edge1: $edge1 and edge2: $edge2 form a circle")
        else if (e1.baseNode == e2.baseNode || e1.baseNode == e2.adjNode)
            return e1.baseNode
        else if (e1.adjNode == e2.adjNode || e1.adjNode == e2.baseNode)
            return e1.adjNode
        else
            throw IllegalArgumentException("edge1: $edge1 and edge2: $edge2 aren't connected")
    }

    @JvmStatic
    fun setSpeed(fwdSpeed: Double, bwdSpeed: Double, accessEnc: BooleanEncodedValue, speedEnc: DecimalEncodedValue,
                 vararg edges: EdgeIteratorState) {
        setSpeed(fwdSpeed, bwdSpeed, accessEnc, speedEnc, listOf(*edges))
    }

    @JvmStatic
    fun setSpeed(fwdSpeed: Double, bwdSpeed: Double, accessEnc: BooleanEncodedValue, speedEnc: DecimalEncodedValue,
                 edges: Collection<EdgeIteratorState>) {
        if (fwdSpeed < 0 || bwdSpeed < 0)
            throw IllegalArgumentException("Speed must be positive but wasn't! fwdSpeed:$fwdSpeed, bwdSpeed:$bwdSpeed")
        for (edge in edges) {
            edge.set(speedEnc, fwdSpeed)
            if (fwdSpeed > 0)
                edge.set(accessEnc, true)

            if (bwdSpeed > 0 && (fwdSpeed != bwdSpeed || speedEnc.isStoreTwoDirections)) {
                if (!speedEnc.isStoreTwoDirections)
                    throw IllegalArgumentException("EncodedValue " + speedEnc.name + " supports only one direction " +
                            "but two different speeds were specified " + fwdSpeed + " " + bwdSpeed)
                edge.setReverse(speedEnc, bwdSpeed)
            }
            if (bwdSpeed > 0)
                edge.setReverse(accessEnc, true)
        }
    }

    @JvmStatic
    fun setSpeed(averageSpeed: Double, fwd: Boolean, bwd: Boolean, accessEnc: BooleanEncodedValue,
                 avSpeedEnc: DecimalEncodedValue, edge: EdgeIteratorState): EdgeIteratorState {
        if (averageSpeed < 0.0001 && (fwd || bwd))
            throw IllegalStateException("Zero speed is only allowed if edge will get inaccessible. Otherwise Weighting can produce inconsistent results")
        edge.set(accessEnc, fwd, bwd)
        if (fwd)
            edge.set(avSpeedEnc, averageSpeed)
        if (bwd && avSpeedEnc.isStoreTwoDirections)
            edge.setReverse(avSpeedEnc, averageSpeed)
        return edge
    }

    @JvmStatic
    fun updateDistancesFor(g: Graph, node: Int, vararg latlonele: Double) {
        val na = g.nodeAccess
        if (latlonele.size == 3)
            na.setNode(node, latlonele[0], latlonele[1], latlonele[2])
        else if (latlonele.size == 2) {
            if (na.is3D()) throw IllegalArgumentException("graph requires elevation")
            na.setNode(node, latlonele[0], latlonele[1])
        } else
            throw IllegalArgumentException("illegal number of arguments " + latlonele.size)
        val iter = g.createEdgeExplorer().setBaseNode(node)
        while (iter.next()) {
            iter.setDistance(DistanceCalcEarth.DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)))
        }
    }

    /**
     * Calculates the weight of a given edge like [Weighting.calcEdgeWeight] and adds the transition
     * cost (the turn weight, [Weighting.calcTurnWeight]) associated with transitioning from/to the edge with ID prevOrNextEdgeId.
     *
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     */
    @JvmStatic
    fun calcWeightWithTurnWeight(weighting: Weighting, edgeState: EdgeIteratorState, reverse: Boolean, prevOrNextEdgeId: Int): Double {
        val edgeWeight = weighting.calcEdgeWeight(edgeState, reverse)
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId)) {
            return edgeWeight
        }
        val turnWeight = if (reverse)
            weighting.calcTurnWeight(edgeState.edge, edgeState.baseNode, prevOrNextEdgeId)
        else
            weighting.calcTurnWeight(prevOrNextEdgeId, edgeState.baseNode, edgeState.edge)
        return edgeWeight + turnWeight
    }

    /**
     * @see .calcWeightWithTurnWeight
     */
    @JvmStatic
    fun calcMillisWithTurnMillis(weighting: Weighting, edgeState: EdgeIteratorState, reverse: Boolean, prevOrNextEdgeId: Int): Long {
        val edgeMillis = weighting.calcEdgeMillis(edgeState, reverse)
        if (edgeMillis == Long.MAX_VALUE)
            return edgeMillis
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId))
            return edgeMillis
        // should we also separate weighting vs. time for turn? E.g. a fast but dangerous turn - is this common?
        // todo: why no first/last orig edge here as in calcWeight ?
//        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        val origEdgeId = edgeState.edge
        val turnMillis = if (reverse)
            weighting.calcTurnMillis(origEdgeId, edgeState.baseNode, prevOrNextEdgeId)
        else
            weighting.calcTurnMillis(prevOrNextEdgeId, edgeState.baseNode, origEdgeId)
        if (turnMillis == Long.MAX_VALUE)
            return turnMillis
        return edgeMillis + turnMillis
    }

    /**
     * Reads the country borders from the countries.geojson resource file
     */
    @JvmStatic
    fun readCountries(): List<CustomArea> {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(JtsModule())

        val enumSet = HashSet<String>(Country.entries.size * 2)
        for (c in Country.entries) {
            if (c == Country.MISSING) continue
            if (c.states.isEmpty()) enumSet.add(c.alpha2)
            else for (s in c.states) enumSet.add(s.stateCode)
        }

        try {
            InputStreamReader(GHUtility::class.java.getResourceAsStream("/com/graphhopper/countries/countries.geojson"), StandardCharsets.UTF_8).use { reader ->
                val jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection::class.java)
                return jsonFeatureCollection.features
                        // exclude areas not in the list of Country enums like FX => Metropolitan France
                        .filter { customArea -> enumSet.contains(getIdOrPropertiesId(customArea)) }
                        .map { f ->
                            val ca = CustomArea.fromJsonFeature(f)
                            // the Feature does not include "id" but we expect it
                            if (f.id == null) f.id = getIdOrPropertiesId(f)
                            // the underlying map comes from the parsed JsonFeature and is mutable,
                            // exactly like ca.getProperties().put(...) in the java version
                            @Suppress("UNCHECKED_CAST")
                            (ca.properties as MutableMap<String, Any?>)[State.ISO_3166_2] = f.id
                            ca
                        }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun getIdOrPropertiesId(feature: JsonFeature): String? {
        if (feature.id != null) return feature.id
        if (feature.properties != null) return feature.properties["id"] as String?
        return null
    }

    @JvmStatic
    fun runConcurrently(runnables: Stream<Runnable>, threads: Int) {
        val pool = ForkJoinPool(threads)
        try {
            pool.submit(Runnable { runnables.parallel().forEach { it.run() } }).get()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        } finally {
            pool.shutdown()
        }
    }

    @JvmStatic
    fun createBBox(edgeState: EdgeIteratorState): BBox {
        val towerNodes = edgeState.fetchWayGeometry(FetchMode.TOWER_ONLY)
        val secondIndex = if (towerNodes.size() == 1) 0 else 1
        return BBox.fromPoints(towerNodes.getLat(0), towerNodes.getLon(0),
                towerNodes.getLat(secondIndex), towerNodes.getLon(secondIndex))
    }

    @JvmStatic
    fun createCircle(id: String, centerLat: Double, centerLon: Double, radius: Double): JsonFeature {
        val n = 36
        val delta = 360.0 / n
        val coordinates = Array(n + 1) { i ->
            val p = DistanceCalcEarth.DIST_EARTH.projectCoordinate(centerLat, centerLon, radius, (i * delta) % 360)
            Coordinate(p.lon, p.lat)
        }
        val polygon = GeometryFactory().createPolygon(coordinates)
        val result = JsonFeature()
        result.id = id
        result.geometry = polygon
        return result
    }

    @JvmStatic
    fun createRectangle(id: String, minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): JsonFeature {
        val coordinates = arrayOf(
                Coordinate(minLon, minLat),
                Coordinate(minLon, maxLat),
                Coordinate(maxLon, maxLat),
                Coordinate(maxLon, minLat),
                Coordinate(minLon, minLat)
        )
        val polygon = GeometryFactory().createPolygon(coordinates)
        val result = JsonFeature()
        result.id = id
        result.geometry = polygon
        return result
    }

    @JvmStatic
    fun comparePaths(refPath: Path, path: Path, source: Int, target: Int, checkNodes: Boolean, seed: Long): List<String> {
        if (path.getGraph().nodes != refPath.getGraph().nodes)
            fail("path and refPath graphs have unequal number of nodes")
        val strictViolations = ArrayList<String>()
        val refWeight = refPath.getWeight()
        val weight = path.getWeight()
        if (refWeight != weight) {
            LOGGER.warn("expected: " + refPath.calcNodes())
            LOGGER.warn("given:    " + path.calcNodes())
            LOGGER.warn("seed: $seed")
            fail("wrong weight: " + source + "->" + target + "\nexpected: " + refWeight + "\ngiven:    " + weight + "\nseed: " + seed + "L")
        }
        if (path.getDistance_mm() != refPath.getDistance_mm()) {
            strictViolations.add("wrong distance: " + source + "->" + target + "\nexpected: " + refPath.getDistance_mm() + "\ngiven:    " + path.getDistance_mm() + "\nseed: " + seed + "L")
        }
        if (path.getTime() != refPath.getTime()) {
            strictViolations.add("wrong time: " + source + "->" + target + "\nexpected: " + refPath.getTime() + "\ngiven: " + path.getTime() + "\nseed: " + seed + "L")
        }
        if (checkNodes) {
            val refNodes = refPath.calcNodes()
            val pathNodes = path.calcNodes()
            if (refNodes != pathNodes) {
                // sometimes there are paths including an edge a-c that has the same distance as the two edges a-b-c. in this
                // case both options are valid best paths. we only check for this most simple and frequent case here...
                if (!pathsEqualExceptOneEdge(path.getGraph(), refNodes, pathNodes))
                    strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refNodes + "\ngiven:    " + pathNodes)
            }
        }
        return strictViolations
    }

    /**
     * Sometimes the graph can contain edges like this:
     * A--C
     * \-B|
     * where A-C is the same distance as A-B-C. In this case the shortest path is not well defined in terms of nodes.
     * This method checks if two node-paths are equal except for such an edge.
     */
    private fun pathsEqualExceptOneEdge(graph: Graph, p1: IntIndexedContainer, p2: IntIndexedContainer): Boolean {
        if (p1 == p2)
            throw IllegalArgumentException("paths are equal")
        if (abs(p1.size() - p2.size()) != 1)
            return false
        val shorterPath = if (p1.size() < p2.size()) p1 else p2
        val longerPath = if (p1.size() < p2.size()) p2 else p1
        if (shorterPath.size() < 2)
            return false
        val indicesWithDifferentNodes = IntArrayList()
        for (i in 1 until shorterPath.size()) {
            if (shorterPath.get(i - indicesWithDifferentNodes.size()) != longerPath.get(i)) {
                indicesWithDifferentNodes.add(i)
            }
        }
        if (indicesWithDifferentNodes.size() != 1)
            return false
        val b = indicesWithDifferentNodes.get(0)
        val a = b - 1
        val c = b + 1
        assert(shorterPath.get(a) == longerPath.get(a))
        assert(shorterPath.get(b) != longerPath.get(b))
        if (shorterPath.get(b) != longerPath.get(c))
            return false
        val distABC = getMinDist(graph, longerPath.get(a), longerPath.get(b)) + getMinDist(graph, longerPath.get(b), longerPath.get(c))

        val distAC = getMinDist(graph, shorterPath.get(a), longerPath.get(c))
        if (abs(distABC - distAC) > 0.1)
            return false
        LOGGER.info("Distance " + shorterPath.get(a) + "-" + longerPath.get(c) + " is the same as distance " +
                longerPath.get(a) + "-" + longerPath.get(b) + "-" + longerPath.get(c) + " -> there are multiple possibilities " +
                "for shortest paths")
        return true
    }

    private fun getMinDist(graph: Graph, p: Int, q: Int): Double {
        val explorer = graph.createEdgeExplorer()
        val iter = explorer.setBaseNode(p)
        var distance = Double.MAX_VALUE
        while (iter.next())
            if (iter.adjNode == q)
                distance = min(distance, iter.distance)
        return distance
    }

    private fun fail(message: String): Nothing {
        throw AssertionError(message)
    }

    @JvmStatic
    fun loadCustomModelFromJar(name: String): CustomModel {
        try {
            val inputStream = GHUtility::class.java.getResourceAsStream("/com/graphhopper/custom_models/$name")
                    ?: throw IllegalArgumentException("There is no built-in custom model '$name'")
            val json = readJSONFileWithoutComments(InputStreamReader(inputStream))
            val objectMapper = Jackson.newObjectMapper()
            return objectMapper.readValue(json, CustomModel::class.java)
        } catch (e: IOException) {
            throw IllegalArgumentException("Could not load built-in custom model '$name'", e)
        }
    }
}
