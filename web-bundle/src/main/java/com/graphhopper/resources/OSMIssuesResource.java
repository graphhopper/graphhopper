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
package com.graphhopper.resources;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;

import static com.graphhopper.util.Parameters.Details.COVERED_TAG;
import static com.graphhopper.util.Parameters.Details.MAX_HEIGHT_SIGNED_TAG;
import static com.graphhopper.util.Parameters.Details.MAX_HEIGHT_TAG;
import static com.graphhopper.util.Parameters.Details.MAX_WEIGHT_TAG;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;

/**
 * Finds OSM tagging issues around bridges in the current map view: roads passing under a bridge
 * without max_height, bridges without max_weight and roads crossing each other without a bridge
 * (or tunnel) tag at all. The result is GeoJSON with one point per issue, including the OSM way
 * ids so that the issue can be fixed right away.
 * <p>
 * Everything is calculated on the fly for the requested bbox, i.e. there is no preprocessing.
 */
@Path("osm-issues")
public class OSMIssuesResource {

    private static final Logger logger = LoggerFactory.getLogger(OSMIssuesResource.class);
    /** safety net against OOM: we hold every edge geometry of the bbox, about 400 bytes each */
    private static final int MAX_EDGES = 8_000_000;
    /** intersections closer than this to an end point of both edges are rather unconnected junctions */
    private static final double MIN_DIST_TO_TOWER_NODE = 1;

    public static final String MISSING_MAXHEIGHT = "missing_maxheight";
    public static final String MISSING_MAXWEIGHT = "missing_maxweight";
    public static final String MISSING_BRIDGE = "missing_bridge";
    public static final String MISSING_MAXHEIGHT_TUNNEL = "missing_maxheight_tunnel";
    /** maxheight=below_default: a mapper confirmed a restriction, but a router still has to guess the number */
    public static final String MAXHEIGHT_BELOW_DEFAULT = "maxheight_below_default";

    /**
     * P(a visit finds a real sign), as additive log-odds over clearance, road below, what is over
     * the road, country and what the other ways under the same bridge say. Fitted by fit.py in
     * graphs-measure/results, see TODO-osm-issues-map.md. Null when the file is missing, then
     * nothing is scored.
     */
    private static final SignModel MODEL = SignModel.load();

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    static class SignModel {
        final double baseLogOdds;
        /** the bin edges of the numeric features, e.g. clearance, bridge_len, end_dist */
        final Map<String, double[]> bins;
        final Map<String, Map<String, Double>> weights;
        /** what the probability is about: "any" numeric sign, or a "low" one that changes truck routing */
        final String target;

        SignModel(double base, Map<String, double[]> bins, Map<String, Map<String, Double>> weights, String target) {
            this.baseLogOdds = Math.log(base / (1 - base));
            this.bins = bins;
            this.weights = weights;
            this.target = target;
        }

        static SignModel load() {
            try (InputStream in = OSMIssuesResource.class.getResourceAsStream("osm-issues-model.json")) {
                if (in == null) {
                    logger.warn("osm-issues-model.json not found, issues will not be scored");
                    return null;
                }
                JsonNode root = new ObjectMapper().readTree(in);
                Map<String, double[]> bins = new HashMap<>();
                root.get("bins").fields().forEachRemaining(e -> {
                    double[] b = new double[e.getValue().size()];
                    for (int i = 0; i < b.length; i++) b[i] = e.getValue().get(i).asDouble();
                    bins.put(e.getKey(), b);
                });
                Map<String, Map<String, Double>> w = new HashMap<>();
                root.get("weights").fields().forEachRemaining(e -> {
                    Map<String, Double> t = new HashMap<>();
                    e.getValue().fields().forEachRemaining(v -> t.put(v.getKey(), v.getValue().asDouble()));
                    w.put(e.getKey(), t);
                });
                return new SignModel(root.get("base_rate").asDouble(), bins, w,
                        root.has("target") ? root.get("target").asText() : "any");
            } catch (Exception e) {
                logger.warn("could not read osm-issues-model.json, issues will not be scored", e);
                return null;
            }
        }

        private double weight(String feature, String value) {
            Map<String, Double> t = weights.get(feature);
            Double v = t == null ? null : t.get(value);
            return v == null ? 0 : v;      // an unseen value is simply uninformative
        }

        /** the bin of a numeric feature, or "unknown" if the value is NaN or the model has no bins for it */
        private String bin(String feature, double value) {
            double[] b = bins.get(feature);
            if (b == null || Double.isNaN(value)) return "unknown";
            int bin = b.length - 2;
            for (int i = 0; i + 1 < b.length; i++)
                if (value >= b[i] && value < b[i + 1]) {
                    bin = i;
                    break;
                }
            return String.valueOf(bin);
        }

        /**
         * @param clearance NaN if unknown, e.g. without elevation or for a tunnel. Then it does not
         *                  count, rather than falling into a bin. Same for the other numbers.
         * @param bridgeLen the length of the bridge edge in meter, a viaduct is not a bridge
         * @param endDist   how far the crossing is from the nearest end of the bridge edge in
         *                  meter, i.e. from the ground the bridge elevation is anchored to
         * @param over      what is over the road: a bridge group, "tunnel" or "covered"
         */
        double probability(double clearance, double bridgeLen, double endDist, String below,
                           String over, String country, String neighbours) {
            double lo = baseLogOdds + weight("clearance", bin("clearance", clearance))
                    + weight("bridge_len", bin("bridge_len", bridgeLen))
                    + weight("end_dist", bin("end_dist", endDist))
                    + weight("below", below) + weight("bridge", over)
                    + weight("country", country) + weight("neighbours", neighbours);
            return 1 / (1 + Math.exp(-lo));
        }
    }

    /**
     * A candidate for a missing maxheight, kept until it can be scored: a road under a bridge waits
     * until everything under that bridge is known, a tunnel can be scored right away.
     */
    private static class Scored {
        final String type;
        final Coordinate at;
        /** the bridge is null for a tunnel, there is nothing crossing the road there */
        final EdgeIteratorState below, bridge;
        final double belowEle, bridgeEle;
        /** length of the bridge edge and distance of the crossing to its nearest end, NaN for a tunnel */
        final double bridgeLen, endDist;
        /** what is over the road: the bridge group of the model, "tunnel" or "covered" */
        final String country, over;
        String neighbours = "unknown";
        double p = Double.NaN;

        Scored(String type, Coordinate at, EdgeIteratorState below, EdgeIteratorState bridge, LineString bridgeLS,
               double belowEle, double bridgeEle, String country, String over) {
            this.type = type;
            this.at = at;
            this.below = below.detach(false);
            this.bridge = bridge == null ? null : bridge.detach(false);
            this.bridgeLen = bridge == null ? Double.NaN : bridge.getDistance();
            this.endDist = bridge == null ? Double.NaN : Math.min(dist(bridgeLS.getCoordinateN(0), at),
                    dist(bridgeLS.getCoordinateN(bridgeLS.getNumPoints() - 1), at));
            this.belowEle = belowEle;
            this.bridgeEle = bridgeEle;
            this.country = country;
            this.over = over;
        }
    }

    /** true if the raw maxheight is a number, i.e. someone read it off a plate */
    private static boolean hasNumber(String raw) {
        if (raw == null) return false;
        try {
            double v = OSMValueExtractor.stringToMeter(raw);
            return !Double.isNaN(v) && !Double.isInfinite(v);
        } catch (Exception e) {
            return false;
        }
    }

    /** the bridge_group of the measurement: how the bridge over the road is classified */
    static String bridgeGroup(RoadClass rc) {
        switch (rc) {
            case MOTORWAY:
            case TRUNK:
                return "motorway+trunk";
            case PRIMARY:
                return "primary";
            case SECONDARY:
            case TERTIARY:
            case RESIDENTIAL:
                return "secondary+tertiary+residential";
            case OTHER:
                return "railway";
            default:
                return "rest";
        }
    }

    @Inject
    public OSMIssuesResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(@QueryParam("bbox") String bboxStr,
                          @QueryParam("types") String typesStr,
                          @QueryParam("roads") @DefaultValue("") String roadsStr,
                          @QueryParam("min_clearance") @DefaultValue("0") double minClearance,
                          @QueryParam("tagged") @DefaultValue("false") boolean tagged,
                          @QueryParam("limit") @DefaultValue("2000") int limit) {
        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, OSMWayID.KEY))
            if (!encodingManager.hasEncodedValue(key))
                throw new IllegalArgumentException("You need to configure GraphHopper with graph.encoded_values: "
                        + "road_class, road_environment, osm_way_id but " + key + " is missing");

        Set<String> types = typesStr == null || typesStr.isEmpty()
                ? new HashSet<>(Arrays.asList(MISSING_MAXHEIGHT, MISSING_MAXWEIGHT, MISSING_BRIDGE,
                        MISSING_MAXHEIGHT_TUNNEL, MAXHEIGHT_BELOW_DEFAULT))
                : new HashSet<>(Arrays.asList(typesStr.split(",")));

        StopWatch sw = new StopWatch().start();
        BBox bbox = parseBBox(bboxStr);
        // an empty list means every road class
        Set<String> roads = roadsStr.isEmpty() ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(roadsStr.toLowerCase().split(",")));
        List<Map<String, Object>> features = findIssues(bbox, types, roads, minClearance, tagged, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FeatureCollection");
        // so the UI can say what p_sign is the probability of
        if (MODEL != null) result.put("sign_target", MODEL.target);
        result.put("features", features);
        sw.stop();
        logger.debug("osm-issues " + bbox + " took: " + sw.getMillis() + "ms, issues: " + features.size());
        return Response.ok(result).header("X-GH-Took", "" + sw.getMillis()).build();
    }

    private List<Map<String, Object>> findIssues(BBox bbox, Set<String> types, Set<String> roads,
                                                double minClearance, boolean tagged, int limit) {
        BaseGraph graph = graphHopper.getBaseGraph();
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        IntEncodedValue wayIdEnc = encodingManager.getIntEncodedValue(OSMWayID.KEY);

        IntHashSet visited = new IntHashSet();
        IntArrayList edgeIds = new IntArrayList();
        locationIndex.query(bbox, edgeId -> {
            if (visited.add(edgeId)) edgeIds.add(edgeId);
        });
        List<Map<String, Object>> features = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        // only the missing bridge tags require the crossings of all ways with each other, max_height
        // starts at the bridges and max_weight needs no crossing at all
        boolean allCrossings = types.contains(MISSING_BRIDGE);
        boolean needsTree = allCrossings || types.contains(MISSING_MAXHEIGHT);
        // we only keep the geometry of every edge in memory when we need the tree, so only then we have to limit the area
        if (needsTree && edgeIds.size() > MAX_EDGES)
            throw new IllegalArgumentException("Too many edges in this area (" + edgeIds.size() + "), please zoom in");

        IntObjectHashMap<LineString> geometries = new IntObjectHashMap<>(edgeIds.size());
        IntArrayList bridgeIds = new IntArrayList();
        STRtree tree = new STRtree();
        for (int i = 0; i < edgeIds.size(); i++) {
            int edgeId = edgeIds.get(i);
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
            RoadEnvironment re = edge.get(roadEnvEnc);
            if (re == RoadEnvironment.FERRY) continue;
            LineString ls = null;
            if (needsTree) {
                ls = edge.fetchWayGeometry(FetchMode.ALL).toLineString(false);
                geometries.put(edgeId, ls);
                tree.insert(ls.getEnvelopeInternal(), edgeId);
            }
            if (re != RoadEnvironment.BRIDGE) continue;
            bridgeIds.add(edgeId);

            // a bridge needs a max_weight regardless of what it crosses (river, railway, road, ...)
            if (types.contains(MISSING_MAXWEIGHT) && edge.getValue(MAX_WEIGHT_TAG) == null
                    && isMotorized(edge.get(roadClassEnc)) && wanted(edge.get(roadClassEnc), roads)) {
                if (ls == null) ls = edge.fetchWayGeometry(FetchMode.ALL).toLineString(false);
                Coordinate at = ls.getCoordinateN(ls.getNumPoints() / 2);
                if (bbox.contains(at.y, at.x))
                    addFeature(features, reported, MISSING_MAXWEIGHT, at, edge, null, wayIdEnc, roadClassEnc);
            }
        }

        EnumEncodedValue<Country> countryEnc = encodingManager.hasEncodedValue(Country.KEY)
                ? encodingManager.getEnumEncodedValue(Country.KEY, Country.class) : null;
        // roads under bridges and in tunnels are scored, then sorted together, so that a truncated
        // answer keeps the most promising places of both kinds
        List<Scored> scored = new ArrayList<>();
        if (types.contains(MISSING_MAXHEIGHT)) {
            // Two passes per bridge: what the ways under it already say is the sharpest predictor
            // (2% still have a sign where every neighbour says none, 83% where one has a number),
            // so it has to be known before the untagged ones can be scored. Neighbours outside the
            // bbox are not seen, so a bridge at the edge is scored on what is visible.
            for (int i = 0; i < bridgeIds.size(); i++) {
                int bridgeId = bridgeIds.get(i);
                LineString bridgeLS = geometries.get(bridgeId);
                EdgeIteratorState bridge = graph.getEdgeIteratorStateForKey(bridgeId * 2);
                List<Scored> underBridge = new ArrayList<>();
                int under = 0;
                boolean anyNumber = false, anyWithoutNumber = false;
                for (Object o : tree.query(bridgeLS.getEnvelopeInternal())) {
                    int belowId = (Integer) o;
                    if (belowId == bridgeId) continue;
                    EdgeIteratorState below = graph.getEdgeIteratorStateForKey(belowId * 2);
                    // a bridge or tunnel below a bridge does not need a max_height
                    if (isSeparatedLevel(below.get(roadEnvEnc))) continue;
                    if (!isMotorized(below.get(roadClassEnc))) continue;
                    Coordinate at = crossing(bridge, bridgeLS, below, geometries.get(belowId), bbox);
                    if (at == null) continue;
                    under++;
                    // a way that has a maxheight tag we cannot parse, like "default", is tagged just
                    // fine, and maxheight:signed=no says a mapper checked that there is no sign.
                    // With tagged=true exactly those are reported instead, to compare their clearance.
                    String raw = (String) below.getValue(MAX_HEIGHT_TAG);
                    boolean hasTag = raw != null || "no".equals(below.getValue(MAX_HEIGHT_SIGNED_TAG));
                    if (hasNumber(raw)) anyNumber = true;
                    else if (hasTag) anyWithoutNumber = true;
                    if (hasTag != tagged) continue;
                    if (!wanted(below.get(roadClassEnc), roads)) continue;
                    // how much higher the bridge is than the road below, if the graph has elevation
                    double bridgeEle = elevationAt(bridge.fetchWayGeometry(FetchMode.ALL), at);
                    double belowEle = elevationAt(below.fetchWayGeometry(FetchMode.ALL), at);
                    if (minClearance > 0 && bridgeEle - belowEle > minClearance) continue;
                    underBridge.add(new Scored(MISSING_MAXHEIGHT, at, below, bridge, bridgeLS, belowEle, bridgeEle,
                            countryEnc == null ? "" : below.get(countryEnc).getAlpha3(),
                            bridgeGroup(bridge.get(roadClassEnc))));
                }
                String neighbours = under <= 1 ? "none"
                        : anyNumber ? "has_number"
                        : anyWithoutNumber ? "only_default" : "untagged";
                for (Scored c : underBridge) {
                    c.neighbours = neighbours;
                    c.p = MODEL == null ? Double.NaN : MODEL.probability(
                            c.bridgeEle - c.belowEle, c.bridgeLen, c.endDist,
                            c.below.get(roadClassEnc).toString(), c.over, c.country, neighbours);
                }
                scored.addAll(underBridge);
            }
        }

        if (types.contains(MISSING_MAXHEIGHT_TUNNEL)) {
            // A tunnel or a roof over the road limits its height by itself, so unlike a bridge there
            // is nothing to intersect. covered=yes is not in road_environment - tunnel, bridge and
            // ford win there - so it is read from the raw tag the reader keeps.
            for (int i = 0; i < edgeIds.size(); i++) {
                int edgeId = edgeIds.get(i);
                EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
                boolean tunnel = edge.get(roadEnvEnc) == RoadEnvironment.TUNNEL;
                boolean roofed = tunnel || edge.getValue(COVERED_TAG) != null;
                if (!roofed || !isMotorized(edge.get(roadClassEnc))) continue;
                if (!wanted(edge.get(roadClassEnc), roads)) continue;
                boolean hasTag = edge.getValue(MAX_HEIGHT_TAG) != null
                        || "no".equals(edge.getValue(MAX_HEIGHT_SIGNED_TAG));
                if (hasTag != tagged) continue;
                PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                if (pl.size() < 2) continue;
                int mid = pl.size() / 2;
                Coordinate at = new Coordinate(pl.getLon(mid), pl.getLat(mid));
                if (!bbox.contains(at.y, at.x)) continue;
                // no clearance and no neighbours here, so the model ranks these by what is left:
                // the road, the country and that it is a tunnel or a roof
                Scored c = new Scored(MISSING_MAXHEIGHT_TUNNEL, at, edge, null, null, Double.NaN, Double.NaN,
                        countryEnc == null ? "" : edge.get(countryEnc).getAlpha3(), tunnel ? "tunnel" : "covered");
                c.p = MODEL == null ? Double.NaN : MODEL.probability(Double.NaN, Double.NaN, Double.NaN,
                        c.below.get(roadClassEnc).toString(), c.over, c.country, c.neighbours);
                scored.add(c);
            }
        }

        if (types.contains(MAXHEIGHT_BELOW_DEFAULT)) {
            // below_default says a mapper saw that the road is lower than the legal default, so a
            // restriction exists for sure and only the number is missing - the most valuable kind
            // of gap. Service roads and roads cars may not use are left out, they do not route.
            BooleanEncodedValue carAccessEnc = encodingManager.hasEncodedValue("car_access")
                    ? encodingManager.getBooleanEncodedValue("car_access") : null;
            for (int i = 0; i < edgeIds.size() && features.size() < limit; i++) {
                int edgeId = edgeIds.get(i);
                EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
                if (!"below_default".equals(edge.getValue(MAX_HEIGHT_TAG))) continue;
                RoadClass rc = edge.get(roadClassEnc);
                if (!isMotorized(rc) || rc == RoadClass.SERVICE || !wanted(rc, roads)) continue;
                if (carAccessEnc != null && !edge.get(carAccessEnc) && !edge.getReverse(carAccessEnc)) continue;
                PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                if (pl.size() < 2) continue;
                int mid = pl.size() / 2;
                Coordinate at = new Coordinate(pl.getLon(mid), pl.getLat(mid));
                if (!bbox.contains(at.y, at.x)) continue;
                addFeature(features, reported, MAXHEIGHT_BELOW_DEFAULT, at, edge, null, wayIdEnc, roadClassEnc);
            }
        }

        // the most promising first, so a truncated answer keeps the places worth visiting
        if (MODEL != null)
            scored.sort((a, b) -> Double.compare(b.p, a.p));
        for (Scored c : scored) {
            if (features.size() >= limit) break;
            addFeature(features, reported, c, wayIdEnc, roadClassEnc, tagged);
        }

        if (allCrossings) {
            for (int i = 0; i < edgeIds.size() && features.size() < limit; i++) {
                int edgeId = edgeIds.get(i);
                LineString lsA = geometries.get(edgeId);
                if (lsA == null) continue;
                EdgeIteratorState edgeA = graph.getEdgeIteratorStateForKey(edgeId * 2);
                if (isSeparatedLevel(edgeA.get(roadEnvEnc))) continue;
                for (Object o : tree.query(lsA.getEnvelopeInternal())) {
                    int otherId = (Integer) o;
                    // every pair should be checked only once
                    if (otherId <= edgeId) continue;
                    EdgeIteratorState edgeB = graph.getEdgeIteratorStateForKey(otherId * 2);
                    if (isSeparatedLevel(edgeB.get(roadEnvEnc))) continue;
                    // here it is unknown which of the two should carry the bridge tag, so both of
                    // them have to pass the filter - otherwise a filtered class could be reported
                    if (!wanted(edgeA.get(roadClassEnc), roads) || !wanted(edgeB.get(roadClassEnc), roads))
                        continue;
                    Coordinate at = crossing(edgeA, lsA, edgeB, geometries.get(otherId), bbox);
                    if (at != null)
                        addFeature(features, reported, MISSING_BRIDGE, at, edgeA, edgeB, wayIdEnc, roadClassEnc);
                }
            }
        }
        return features;
    }

    /**
     * @return the point where the two edges cross without being connected there, or null if they do not
     */
    private Coordinate crossing(EdgeIteratorState a, LineString lsA, EdgeIteratorState b, LineString lsB, BBox bbox) {
        if (sharesTowerNode(a, b)) return null;
        if (!lsA.intersects(lsB)) return null;
        Geometry intersection = lsA.intersection(lsB);
        if (intersection.isEmpty()) return null;
        Coordinate at = intersection.getCoordinate();
        if (!bbox.contains(at.y, at.x)) return null;
        // if both edges just end here this is an unconnected junction and not a crossing
        if (closeToTowerNode(lsA, at) && closeToTowerNode(lsB, at)) return null;
        return at;
    }

    private static boolean isSeparatedLevel(RoadEnvironment re) {
        return re == RoadEnvironment.BRIDGE || re == RoadEnvironment.TUNNEL || re == RoadEnvironment.FORD;
    }

    private void addFeature(List<Map<String, Object>> features, Set<String> reported, String type, Coordinate at,
                            EdgeIteratorState edge, EdgeIteratorState otherEdge, IntEncodedValue wayIdEnc,
                            EnumEncodedValue<RoadClass> roadClassEnc) {
        addFeature(features, reported, type, at, edge, otherEdge, wayIdEnc, roadClassEnc, Collections.emptyMap(), null);
    }

    /**
     * @param perBridge report a road once per bridge it passes under instead of once altogether.
     *                  Used for tagged=true, where the result is training data and the pairing with
     *                  the lowest bridge is done there
     */
    private void addFeature(List<Map<String, Object>> features, Set<String> reported, Scored c,
                            IntEncodedValue wayIdEnc, EnumEncodedValue<RoadClass> roadClassEnc, boolean perBridge) {
        Map<String, Object> extra = new LinkedHashMap<>();
        // how likely a visit here finds an actual sign rather than another maxheight=default
        if (!Double.isNaN(c.p)) {
            extra.put("p_sign", Math.round(c.p * 100) / 100.0);
            if (!"unknown".equals(c.neighbours)) extra.put("neighbours", c.neighbours);
        }
        extra.put("over", c.over);
        if (!c.country.isEmpty()) extra.put("country", c.country);
        // how much higher the bridge is than the way below, as a hint whether it is high enough.
        // Only meaningful for bridges in the routable network, see TODO-osm-issues-map.md
        if (!Double.isNaN(c.bridgeEle)) {
            extra.put("clearance", round(c.bridgeEle - c.belowEle));
            extra.put("ele_below", round(c.belowEle));
            extra.put("ele_bridge", round(c.bridgeEle));
        }
        if (!Double.isNaN(c.bridgeLen)) {
            extra.put("bridge_len", Math.round(c.bridgeLen));
            extra.put("end_dist", Math.round(c.endDist));
        }
        String key = perBridge && c.bridge != null
                ? c.type + "-" + c.below.get(wayIdEnc) + "-" + c.bridge.get(wayIdEnc) : null;
        addFeature(features, reported, c.type, c.at, c.below, c.bridge, wayIdEnc, roadClassEnc, extra, key);
    }

    private void addFeature(List<Map<String, Object>> features, Set<String> reported, String type, Coordinate at,
                            EdgeIteratorState edge, EdgeIteratorState otherEdge, IntEncodedValue wayIdEnc,
                            EnumEncodedValue<RoadClass> roadClassEnc, Map<String, Object> extra, String key) {
        int wayId = edge.get(wayIdEnc);
        int otherWayId = otherEdge == null ? 0 : otherEdge.get(wayIdEnc);
        // Report every way (or pair of ways) once. For maxheight the bridge is not part of the key:
        // one tag fixes every crossing of that road, and the list is sorted by score, so the
        // crossing that survives is the most promising one.
        if (key == null)
            key = otherEdge == null || MISSING_MAXHEIGHT.equals(type) ? type + "-" + wayId
                    : MISSING_BRIDGE.equals(type) ? type + "-" + Math.min(wayId, otherWayId) + "-" + Math.max(wayId, otherWayId)
                    : type + "-" + wayId + "-" + otherWayId;
        if (!reported.add(key)) return;

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", type);
        properties.putAll(extra);
        properties.put("way_id", wayId);
        properties.put("way_name", edge.getName());
        properties.put("road_class", edge.get(roadClassEnc).toString());
        if (edge.getValue(MAX_HEIGHT_TAG) != null) properties.put("maxheight", edge.getValue(MAX_HEIGHT_TAG));
        if (otherEdge != null) {
            properties.put("other_way_id", otherWayId);
            properties.put("other_way_name", otherEdge.getName());
            properties.put("other_road_class", otherEdge.get(roadClassEnc).toString());
        }

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", Arrays.asList(at.x, at.y));

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        features.add(feature);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * @param roads the wanted road class names as GraphHopper spells them, e.g. "motorway,trunk".
     *              An empty set or "all" means every road class, so the grouping stays in the UI.
     */
    private static boolean wanted(RoadClass roadClass, Set<String> roads) {
        return roads.isEmpty() || roads.contains("all") || roads.contains(roadClass.toString());
    }

    /**
     * The elevation of the way at the given point, interpolated along the segment it falls on.
     * Bridges carry interpolated elevation from the import, so the difference to the way below is
     * roughly the clearance under the bridge.
     *
     * @return NaN if the graph has no elevation
     */
    private static double elevationAt(PointList pl, Coordinate at) {
        if (!pl.is3D()) return Double.NaN;
        double bestDist = Double.MAX_VALUE, ele = Double.NaN;
        double cosLat = Math.cos(Math.toRadians(at.y));
        for (int i = 1; i < pl.size(); i++) {
            double ax = pl.getLon(i - 1) * cosLat, ay = pl.getLat(i - 1);
            double dx = pl.getLon(i) * cosLat - ax, dy = pl.getLat(i) - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((at.x * cosLat - ax) * dx + (at.y - ay) * dy) / len2));
            double distX = at.x * cosLat - (ax + t * dx), distY = at.y - (ay + t * dy);
            double dist = distX * distX + distY * distY;
            if (dist < bestDist) {
                bestDist = dist;
                ele = pl.getEle(i - 1) + t * (pl.getEle(i) - pl.getEle(i - 1));
            }
        }
        return ele;
    }

    private static boolean isMotorized(RoadClass roadClass) {
        switch (roadClass) {
            case FOOTWAY:
            case CYCLEWAY:
            case PATH:
            case STEPS:
            case PEDESTRIAN:
            case BRIDLEWAY:
            case PLATFORM:
            case CORRIDOR:
            case OTHER:
                return false;
            default:
                return true;
        }
    }

    private static boolean sharesTowerNode(EdgeIteratorState a, EdgeIteratorState b) {
        return a.getBaseNode() == b.getBaseNode() || a.getBaseNode() == b.getAdjNode()
                || a.getAdjNode() == b.getBaseNode() || a.getAdjNode() == b.getAdjNode();
    }

    private static boolean closeToTowerNode(LineString ls, Coordinate at) {
        return dist(ls.getCoordinateN(0), at) < MIN_DIST_TO_TOWER_NODE
                || dist(ls.getCoordinateN(ls.getNumPoints() - 1), at) < MIN_DIST_TO_TOWER_NODE;
    }

    private static double dist(Coordinate a, Coordinate b) {
        return DistanceCalcEarth.DIST_EARTH.calcDist(a.y, a.x, b.y, b.x);
    }

    static BBox parseBBox(String bboxStr) {
        if (bboxStr == null || bboxStr.isEmpty())
            throw new IllegalArgumentException("You need to specify a bbox like bbox=minLon,minLat,maxLon,maxLat");
        String[] splits = bboxStr.split(",");
        if (splits.length != 4)
            throw new IllegalArgumentException("bbox must be of the form minLon,minLat,maxLon,maxLat but was " + bboxStr);
        BBox bbox = new BBox(Double.parseDouble(splits[0]), Double.parseDouble(splits[2]),
                Double.parseDouble(splits[1]), Double.parseDouble(splits[3]));
        if (!bbox.isValid()) throw new IllegalArgumentException("Invalid bbox " + bboxStr);
        return bbox;
    }
}
