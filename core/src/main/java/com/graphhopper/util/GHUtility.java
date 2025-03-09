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
package com.graphhopper.util;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.State;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.graphhopper.routing.ev.State.ISO_3166_2;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;
import static com.graphhopper.util.Helper.readJSONFileWithoutComments;

/**
 * A helper class to avoid cluttering the Graph interface with all the common methods. Most of the
 * methods are useful for unit tests or debugging only.
 *
 * @author Peter Karich
 */
public class GHUtility {
    public static final Logger OSM_WARNING_LOGGER = LoggerFactory.getLogger("com.graphhopper.osm_warnings");
    private static final Logger LOGGER = LoggerFactory.getLogger(GHUtility.class);

    /**
     * This method could throw an exception if problems like index out of bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<>();
        int nodes = g.getNodes();
        int nodeIndex = 0;
        NodeAccess na = g.getNodeAccess();
        try {
            EdgeExplorer explorer = g.createEdgeExplorer();
            for (; nodeIndex < nodes; nodeIndex++) {
                double lat = na.getLat(nodeIndex);
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds " + lat);

                double lon = na.getLon(nodeIndex);
                if (lon > 180 || lon < -180)
                    problems.add("longitude is not within its bounds " + lon);

                EdgeIterator iter = explorer.setBaseNode(nodeIndex);
                while (iter.next()) {
                    if (iter.getAdjNode() >= nodes) {
                        problems.add("edge of " + nodeIndex + " has a node " + iter.getAdjNode() + " greater or equal to getNodes");
                    }
                    if (iter.getAdjNode() < 0) {
                        problems.add("edge of " + nodeIndex + " has a negative node " + iter.getAdjNode());
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("problem with node " + nodeIndex, ex);
        }

//        for (int i = 0; i < nodes; i++) {
//            new BreadthFirstSearch().start(g, i);
//        }
        return problems;
    }

    /**
     * Counts reachable edges.
     */
    public static int count(EdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            counter++;
        }
        return counter;
    }

    public static int count(RoutingCHEdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            counter++;
        }
        return counter;
    }

    public static Set<Integer> asSet(int... values) {
        Set<Integer> s = new HashSet<>();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }

    public static Set<Integer> getNeighbors(RoutingCHEdgeIterator iter) {
        // make iteration order over set static => linked
        Set<Integer> list = new LinkedHashSet<>();
        while (iter.next()) {
            list.add(iter.getAdjNode());
        }
        return list;
    }

    public static Set<Integer> getNeighbors(EdgeIterator iter) {
        // make iteration order over set static => linked
        Set<Integer> list = new LinkedHashSet<>();
        while (iter.next()) {
            list.add(iter.getAdjNode());
        }
        return list;
    }

    public static List<Integer> getEdgeIds(EdgeIterator iter) {
        List<Integer> list = new ArrayList<>();
        while (iter.next()) {
            list.add(iter.getEdge());
        }
        return list;
    }

    public static void printGraphForUnitTest(Graph g, DecimalEncodedValue speedEnc) {
        printGraphForUnitTest(g, speedEnc, new BBox(
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    public static void printGraphForUnitTest(Graph g, DecimalEncodedValue speedEnc, BBox bBox) {
        NodeAccess na = g.getNodeAccess();
        for (int node = 0; node < g.getNodes(); ++node) {
            if (bBox.contains(na.getLat(node), na.getLon(node))) {
                System.out.printf(Locale.ROOT, "na.setNode(%d, %f, %f);\n", node, na.getLat(node), na.getLon(node));
            }
        }
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next()) {
            if (bBox.contains(na.getLat(iter.getBaseNode()), na.getLon(iter.getBaseNode())) &&
                    bBox.contains(na.getLat(iter.getAdjNode()), na.getLon(iter.getAdjNode()))) {
                printUnitTestEdge(speedEnc, iter);
            }
        }
    }

    private static void printUnitTestEdge(DecimalEncodedValue speedEnc, EdgeIteratorState edge) {
        boolean fwd = edge.get(speedEnc) > 0;
        int from = fwd ? edge.getBaseNode() : edge.getAdjNode();
        int to = fwd ? edge.getAdjNode() : edge.getBaseNode();
        System.out.printf(Locale.ROOT,
                "graph.edge(%d, %d).setDistance(%f).set(speedEnc, %f, %f); // edgeId=%s\n",
                from, to, edge.getDistance(), edge.get(speedEnc), edge.getReverse(speedEnc),
                edge.getEdge());
    }

    /**
     * @param speed if null a random speed will be assigned to every edge
     */
    public static void buildRandomGraph(Graph graph, Random random, int numNodes, double meanDegree,
                                        boolean allowZeroDistance, DecimalEncodedValue speedEnc, Double speed,
                                        double pBothDir, double pRandomDistanceOffset) {
        if (numNodes < 2 || meanDegree < 1) {
            throw new IllegalArgumentException("numNodes must be >= 2, meanDegree >= 1");
        }
        for (int i = 0; i < numNodes; ++i) {
            double lat = 49.4 + (random.nextDouble() * 0.01);
            double lon = 9.7 + (random.nextDouble() * 0.01);
            graph.getNodeAccess().setNode(i, lat, lon);
        }
        double minDist = Double.MAX_VALUE;
        double maxDist = Double.MIN_VALUE;
        int totalNumEdges = (int) (0.5 * meanDegree * numNodes);
        int numEdges = 0;
        while (numEdges < totalNumEdges) {
            int from = random.nextInt(numNodes);
            int to = random.nextInt(numNodes);
            if (from == to)
                continue;
            double distance = GHUtility.getDistance(from, to, graph.getNodeAccess());
            if (!allowZeroDistance) {
                distance = Math.max(0.001, distance);
            }
            // add some random offset, but also allow duplicate edges with same weight
            if (random.nextDouble() < pRandomDistanceOffset)
                distance += random.nextDouble() * distance * 0.01;
            minDist = Math.min(minDist, distance);
            maxDist = Math.max(maxDist, distance);
            // using bidirectional edges will increase mean degree of graph above given value
            boolean bothDirections = random.nextDouble() < pBothDir;
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance);
            double fwdSpeed = 10 + random.nextDouble() * 110;
            double bwdSpeed = 10 + random.nextDouble() * 110;
            // if an explicit speed is given we discard the random speeds and use the given one instead
            if (speed != null) {
                fwdSpeed = bwdSpeed = speed;
            }
            if (speedEnc != null) {
                edge.set(speedEnc, fwdSpeed);
                if (speedEnc.isStoreTwoDirections())
                    edge.setReverse(speedEnc, !bothDirections ? 0 : bwdSpeed);
            }
            numEdges++;
        }
        LOGGER.debug(String.format(Locale.ROOT, "Finished building random graph" +
                        ", nodes: %d, edges: %d , min distance: %.2f, max distance: %.2f\n",
                graph.getNodes(), graph.getEdges(), minDist, maxDist));
    }

    public static double getDistance(int from, int to, NodeAccess nodeAccess) {
        double fromLat = nodeAccess.getLat(from);
        double fromLon = nodeAccess.getLon(from);
        double toLat = nodeAccess.getLat(to);
        double toLon = nodeAccess.getLon(to);
        return DistancePlaneProjection.DIST_PLANE.calcDist(fromLat, fromLon, toLat, toLon);
    }

    public static void addRandomTurnCosts(Graph graph, long seed, BooleanEncodedValue accessEnc, DecimalEncodedValue turnCostEnc, int maxTurnCost, TurnCostStorage turnCostStorage) {
        Random random = new Random(seed);
        double pNodeHasTurnCosts = 0.3;
        double pEdgePairHasTurnCosts = 0.6;
        double pCostIsRestriction = 1.0;

        EdgeExplorer inExplorer = graph.createEdgeExplorer(accessEnc == null ? edge -> true : AccessFilter.inEdges(accessEnc));
        EdgeExplorer outExplorer = graph.createEdgeExplorer(accessEnc == null ? edge -> true : AccessFilter.outEdges(accessEnc));
        for (int node = 0; node < graph.getNodes(); ++node) {
            if (random.nextDouble() < pNodeHasTurnCosts) {
                EdgeIterator inIter = inExplorer.setBaseNode(node);
                while (inIter.next()) {
                    EdgeIterator outIter = outExplorer.setBaseNode(node);
                    while (outIter.next()) {
                        if (inIter.getEdge() == outIter.getEdge()) {
                            // leave u-turns as they are
                            continue;
                        }
                        if (random.nextDouble() < pEdgePairHasTurnCosts) {
                            double cost = random.nextDouble() < pCostIsRestriction ? Double.POSITIVE_INFINITY : random.nextDouble() * maxTurnCost;
                            turnCostStorage.set(turnCostEnc, inIter.getEdge(), node, outIter.getEdge(), cost);
                            if (cost > 0)
                                System.out.println("tcs.set(turnCostEnc, " + inIter.getEdge() + ", " + node + ", " + outIter.getEdge() + ", " + cost + ");");
                        }
                    }
                }
            }
        }
    }

    public static List<Snap> createRandomSnaps(BBox bbox, LocationIndex locationIndex, Random rnd, int numPoints, boolean acceptTower, EdgeFilter filter) {
        int maxTries = numPoints * 100;
        int tries = 0;
        List<Snap> snaps = new ArrayList<>(numPoints);
        while (snaps.size() < numPoints) {
            if (tries > maxTries)
                throw new IllegalArgumentException("Could not create " + numPoints + " random points. tries: " + tries + ", maxTries: " + maxTries);
            Snap snap = getRandomSnap(locationIndex, rnd, bbox, filter);
            boolean accepted = snap.isValid();
            if (!acceptTower)
                accepted = accepted && !snap.getSnappedPosition().equals(Snap.Position.TOWER);
            if (accepted)
                snaps.add(snap);
            tries++;
        }
        return snaps;
    }

    public static Snap getRandomSnap(LocationIndex locationIndex, Random rnd, BBox bbox, EdgeFilter filter) {
        return locationIndex.findClosest(
                randomDoubleInRange(rnd, bbox.minLat, bbox.maxLat),
                randomDoubleInRange(rnd, bbox.minLon, bbox.maxLon),
                filter
        );
    }

    public static double randomDoubleInRange(Random rnd, double min, double max) {
        return min + rnd.nextDouble() * (max - min);
    }

    public static int getAdjNode(Graph g, int edge, int adjNode) {
        if (EdgeIterator.Edge.isValid(edge)) {
            EdgeIteratorState iterTo = g.getEdgeIteratorState(edge, adjNode);
            return iterTo.getAdjNode();
        }
        return adjNode;
    }

    public static void checkDAVersion(String name, int expectedVersion, int version) {
        if (version != expectedVersion) {
            throw new IllegalStateException("Unexpected version for '" + name + "'. Got: " + version + ", " +
                    "expected: " + expectedVersion + ". "
                    + "Make sure you are using the same GraphHopper version for reading the files that was used for creating them. "
                    + "See https://discuss.graphhopper.com/t/722");
        }
    }

    /**
     * @return the edge between base and adj, or null if there is no such edge
     * @throws IllegalArgumentException when there are multiple edges
     */
    public static EdgeIteratorState getEdge(Graph graph, int base, int adj) {
        EdgeExplorer explorer = graph.createEdgeExplorer();
        int count = count(explorer.setBaseNode(base), adj);
        if (count > 1)
            throw new IllegalArgumentException("There are multiple edges between nodes " + base + " and " + adj);
        else if (count == 0)
            return null;
        EdgeIterator iter = explorer.setBaseNode(base);
        while (iter.next()) {
            if (iter.getAdjNode() == adj)
                return iter;
        }
        throw new IllegalStateException("There should be an edge");
    }

    /**
     * @return the number of edges with the given adj node
     */
    public static int count(EdgeIterator iterator, int adj) {
        int count = 0;
        while (iterator.next()) {
            if (iterator.getAdjNode() == adj)
                count++;
        }
        return count;
    }

    /**
     * Creates an edge key, i.e. an integer number that encodes an edge ID and the direction of an edge
     */
    public static int createEdgeKey(int edgeId, boolean reverse) {
        // edge state in storage direction -> edge key is even
        // edge state against storage direction -> edge key is odd
        return (edgeId << 1) + (reverse ? 1 : 0);
    }

    /**
     * Returns the edgeKey of the opposite direction
     */
    public static int reverseEdgeKey(int edgeKey) {
        return edgeKey % 2 == 0 ? edgeKey + 1 : edgeKey - 1;
    }

    /**
     * @return edge ID for edgeKey
     */
    public static int getEdgeFromEdgeKey(int edgeKey) {
        return edgeKey / 2;
    }

    /**
     * @return the common node of two edges
     * @throws IllegalArgumentException if one of the edges doesn't exist or is a loop or the edges
     *                                  aren't connected at exactly one distinct node
     */
    public static int getCommonNode(BaseGraph baseGraph, int edge1, int edge2) {
        EdgeIteratorState e1 = baseGraph.getEdgeIteratorState(edge1, Integer.MIN_VALUE);
        EdgeIteratorState e2 = baseGraph.getEdgeIteratorState(edge2, Integer.MIN_VALUE);
        if (e1.getBaseNode() == e1.getAdjNode())
            throw new IllegalArgumentException("edge1: " + edge1 + " is a loop at node " + e1.getBaseNode());
        if (e2.getBaseNode() == e2.getAdjNode())
            throw new IllegalArgumentException("edge2: " + edge2 + " is a loop at node " + e2.getBaseNode());

        if ((e1.getBaseNode() == e2.getBaseNode() && e1.getAdjNode() == e2.getAdjNode()) || (e1.getBaseNode() == e2.getAdjNode() && e1.getAdjNode() == e2.getBaseNode()))
            throw new IllegalArgumentException("edge1: " + edge1 + " and edge2: " + edge2 + " form a circle");
        else if (e1.getBaseNode() == e2.getBaseNode() || e1.getBaseNode() == e2.getAdjNode())
            return e1.getBaseNode();
        else if (e1.getAdjNode() == e2.getAdjNode() || e1.getAdjNode() == e2.getBaseNode())
            return e1.getAdjNode();
        else
            throw new IllegalArgumentException("edge1: " + edge1 + " and edge2: " + edge2 + " aren't connected");
    }

    public static void setSpeed(double fwdSpeed, double bwdSpeed, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, EdgeIteratorState... edges) {
        setSpeed(fwdSpeed, bwdSpeed, accessEnc, speedEnc, Arrays.asList(edges));
    }

    public static void setSpeed(double fwdSpeed, double bwdSpeed, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, Collection<EdgeIteratorState> edges) {
        if (fwdSpeed < 0 || bwdSpeed < 0)
            throw new IllegalArgumentException("Speed must be positive but wasn't! fwdSpeed:" + fwdSpeed + ", bwdSpeed:" + bwdSpeed);
        for (EdgeIteratorState edge : edges) {
            edge.set(speedEnc, fwdSpeed);
            if (fwdSpeed > 0)
                edge.set(accessEnc, true);

            if (bwdSpeed > 0 && (fwdSpeed != bwdSpeed || speedEnc.isStoreTwoDirections())) {
                if (!speedEnc.isStoreTwoDirections())
                    throw new IllegalArgumentException("EncodedValue " + speedEnc.getName() + " supports only one direction " +
                            "but two different speeds were specified " + fwdSpeed + " " + bwdSpeed);
                edge.setReverse(speedEnc, bwdSpeed);
            }
            if (bwdSpeed > 0)
                edge.setReverse(accessEnc, true);
        }
    }

    public static EdgeIteratorState setSpeed(double averageSpeed, boolean fwd, boolean bwd, BooleanEncodedValue accessEnc, DecimalEncodedValue avSpeedEnc, EdgeIteratorState edge) {
        if (averageSpeed < 0.0001 && (fwd || bwd))
            throw new IllegalStateException("Zero speed is only allowed if edge will get inaccessible. Otherwise Weighting can produce inconsistent results");
        edge.set(accessEnc, fwd, bwd);
        if (fwd)
            edge.set(avSpeedEnc, averageSpeed);
        if (bwd && avSpeedEnc.isStoreTwoDirections())
            edge.setReverse(avSpeedEnc, averageSpeed);
        return edge;
    }

    public static void updateDistancesFor(Graph g, int node, double... latlonele) {
        NodeAccess na = g.getNodeAccess();
        if (latlonele.length == 3)
            na.setNode(node, latlonele[0], latlonele[1], latlonele[2]);
        else if (latlonele.length == 2) {
            if (na.is3D()) throw new IllegalArgumentException("graph requires elevation");
            na.setNode(node, latlonele[0], latlonele[1]);
        } else
            throw new IllegalArgumentException("illegal number of arguments " + latlonele.length);
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(node);
        while (iter.next()) {
            iter.setDistance(DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)));
        }
    }

    /**
     * Calculates the weight of a given edge like {@link Weighting#calcEdgeWeight} and adds the transition
     * cost (the turn weight, {@link Weighting#calcTurnWeight}) associated with transitioning from/to the edge with ID prevOrNextEdgeId.
     *
     * @param prevOrNextEdgeId if reverse is false this has to be the previous edgeId, if true it
     *                         has to be the next edgeId in the direction from start to end.
     */
    public static double calcWeightWithTurnWeight(Weighting weighting, EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        final double edgeWeight = weighting.calcEdgeWeight(edgeState, reverse);
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId)) {
            return edgeWeight;
        }
        double turnWeight = reverse
                ? weighting.calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId)
                : weighting.calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), edgeState.getEdge());
        return edgeWeight + turnWeight;
    }

    /**
     * @see #calcWeightWithTurnWeight(Weighting, EdgeIteratorState, boolean, int)
     */
    public static long calcMillisWithTurnMillis(Weighting weighting, EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long edgeMillis = weighting.calcEdgeMillis(edgeState, reverse);
        if (edgeMillis == Long.MAX_VALUE)
            return edgeMillis;
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId))
            return edgeMillis;
        // should we also separate weighting vs. time for turn? E.g. a fast but dangerous turn - is this common?
        // todo: why no first/last orig edge here as in calcWeight ?
//        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        final int origEdgeId = edgeState.getEdge();
        long turnMillis = reverse
                ? weighting.calcTurnMillis(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : weighting.calcTurnMillis(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        if (turnMillis == Long.MAX_VALUE)
            return turnMillis;
        return edgeMillis + turnMillis;
    }

    /**
     * Reads the country borders from the countries.geojson resource file
     */
    public static List<CustomArea> readCountries() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());

        Set<String> enumSet = new HashSet<>(Country.values().length * 2);
        for (Country c : Country.values()) {
            if (c == Country.MISSING) continue;
            if (c.getStates().isEmpty()) enumSet.add(c.getAlpha2());
            else for (State s : c.getStates()) enumSet.add(s.getStateCode());
        }

        try (Reader reader = new InputStreamReader(GHUtility.class.getResourceAsStream("/com/graphhopper/countries/countries.geojson"), StandardCharsets.UTF_8)) {
            JsonFeatureCollection jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
            return jsonFeatureCollection.getFeatures().stream()
                    // exclude areas not in the list of Country enums like FX => Metropolitan France
                    .filter(customArea -> enumSet.contains(getIdOrPropertiesId(customArea)))
                    .map((f) -> {
                        CustomArea ca = CustomArea.fromJsonFeature(f);
                        // the Feature does not include "id" but we expect it
                        if (f.getId() == null) f.setId(getIdOrPropertiesId(f));
                        ca.getProperties().put(ISO_3166_2, f.getId());
                        return ca;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String getIdOrPropertiesId(JsonFeature feature) {
        if (feature.getId() != null) return feature.getId();
        if (feature.getProperties() != null) return (String) feature.getProperties().get("id");
        return null;
    }

    public static void runConcurrently(Stream<Runnable> runnables, int threads) {
        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.submit(() -> runnables.parallel().forEach(Runnable::run)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
    }

    public static BBox createBBox(EdgeIteratorState edgeState) {
        PointList towerNodes = edgeState.fetchWayGeometry(FetchMode.TOWER_ONLY);
        int secondIndex = towerNodes.size() == 1 ? 0 : 1;
        return BBox.fromPoints(towerNodes.getLat(0), towerNodes.getLon(0),
                towerNodes.getLat(secondIndex), towerNodes.getLon(secondIndex));
    }

    public static JsonFeature createCircle(String id, double centerLat, double centerLon, double radius) {
        final int n = 36;
        final double delta = 360.0 / n;
        Coordinate[] coordinates = IntStream.range(0, n + 1)
                .mapToObj(i -> DIST_EARTH.projectCoordinate(centerLat, centerLon, radius, (i * delta) % 360))
                .map(p -> new Coordinate(p.lon, p.lat)).toArray(Coordinate[]::new);
        Polygon polygon = new GeometryFactory().createPolygon(coordinates);
        JsonFeature result = new JsonFeature();
        result.setId(id);
        result.setGeometry(polygon);
        return result;
    }

    public static JsonFeature createRectangle(String id, double minLat, double minLon, double maxLat, double maxLon) {
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(minLon, minLat),
                new Coordinate(minLon, maxLat),
                new Coordinate(maxLon, maxLat),
                new Coordinate(maxLon, minLat),
                new Coordinate(minLon, minLat)
        };
        Polygon polygon = new GeometryFactory().createPolygon(coordinates);
        JsonFeature result = new JsonFeature();
        result.setId(id);
        result.setGeometry(polygon);
        return result;
    }

    public static List<String> comparePaths(Path refPath, Path path, int source, int target, long seed) {
        List<String> strictViolations = new ArrayList<>();
        double refWeight = refPath.getWeight();
        double weight = path.getWeight();
        if (Math.abs(refWeight - weight) > 1.e-2) {
            LOGGER.warn("expected: " + refPath.calcNodes());
            LOGGER.warn("given:    " + path.calcNodes());
            LOGGER.warn("seed: " + seed);
            fail("wrong weight: " + source + "->" + target + "\nexpected: " + refWeight + "\ngiven:    " + weight + "\nseed: " + seed);
        }
        if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
            strictViolations.add("wrong distance " + source + "->" + target + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
        }
        if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
            strictViolations.add("wrong time " + source + "->" + target + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
        }
        IntIndexedContainer refNodes = refPath.calcNodes();
        IntIndexedContainer pathNodes = path.calcNodes();
        if (!refNodes.equals(pathNodes)) {
            // sometimes there are paths including an edge a-c that has the same distance as the two edges a-b-c. in this
            // case both options are valid best paths. we only check for this most simple and frequent case here...
            if (path.getGraph() != refPath.getGraph())
                fail("path and refPath graphs are different");
            if (!pathsEqualExceptOneEdge(path.getGraph(), refNodes, pathNodes))
                strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refNodes + "\ngiven:    " + pathNodes);
        }
        return strictViolations;
    }

    /**
     * Sometimes the graph can contain edges like this:
     * A--C
     * \-B|
     * where A-C is the same distance as A-B-C. In this case the shortest path is not well defined in terms of nodes.
     * This method checks if two node-paths are equal except for such an edge.
     */
    private static boolean pathsEqualExceptOneEdge(Graph graph, IntIndexedContainer p1, IntIndexedContainer p2) {
        if (p1.equals(p2))
            throw new IllegalArgumentException("paths are equal");
        if (Math.abs(p1.size() - p2.size()) != 1)
            return false;
        IntIndexedContainer shorterPath = p1.size() < p2.size() ? p1 : p2;
        IntIndexedContainer longerPath = p1.size() < p2.size() ? p2 : p1;
        if (shorterPath.size() < 2)
            return false;
        IntArrayList indicesWithDifferentNodes = new IntArrayList();
        for (int i = 1; i < shorterPath.size(); i++) {
            if (shorterPath.get(i - indicesWithDifferentNodes.size()) != longerPath.get(i)) {
                indicesWithDifferentNodes.add(i);
            }
        }
        if (indicesWithDifferentNodes.size() != 1)
            return false;
        int b = indicesWithDifferentNodes.get(0);
        int a = b - 1;
        int c = b + 1;
        assert shorterPath.get(a) == longerPath.get(a);
        assert shorterPath.get(b) != longerPath.get(b);
        if (shorterPath.get(b) != longerPath.get(c))
            return false;
        double distABC = getMinDist(graph, longerPath.get(a), longerPath.get(b)) + getMinDist(graph, longerPath.get(b), longerPath.get(c));

        double distAC = getMinDist(graph, shorterPath.get(a), longerPath.get(c));
        if (Math.abs(distABC - distAC) > 0.1)
            return false;
        LOGGER.info("Distance " + shorterPath.get(a) + "-" + longerPath.get(c) + " is the same as distance " +
                longerPath.get(a) + "-" + longerPath.get(b) + "-" + longerPath.get(c) + " -> there are multiple possibilities " +
                "for shortest paths");
        return true;
    }

    private static double getMinDist(Graph graph, int p, int q) {
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(p);
        double distance = Double.MAX_VALUE;
        while (iter.next())
            if (iter.getAdjNode() == q)
                distance = Math.min(distance, iter.getDistance());
        return distance;
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

    public static CustomModel loadCustomModelFromJar(String name) {
        try {
            InputStream is = GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + name);
            if (is == null)
                throw new IllegalArgumentException("There is no built-in custom model '" + name + "'");
            String json = readJSONFileWithoutComments(new InputStreamReader(is));
            ObjectMapper objectMapper = Jackson.newObjectMapper();
            return objectMapper.readValue(json, CustomModel.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load built-in custom model '" + name + "'", e);
        }
    }
}
