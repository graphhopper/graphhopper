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
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;

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
 * Finds OSM tagging problems around bridges in the current map view: roads passing under a bridge
 * without max_height, bridges without max_weight and roads crossing each other without a bridge
 * (or tunnel) tag at all. The result is GeoJSON with one point per problem, including the OSM way
 * ids so that the problem can be fixed right away.
 * <p>
 * Everything is calculated on the fly for the requested bbox, i.e. there is no preprocessing.
 */
@Path("osm-issues")
public class OSMIssuesResource {

    private static final Logger logger = LoggerFactory.getLogger(OSMIssuesResource.class);
    /** we keep the geometry of every edge of the bbox in memory, so this is just a safety net against OOM */
    private static final int MAX_EDGES = 2_000_000;
    /** intersections closer than this to an end point of both edges are rather unconnected junctions */
    private static final double MIN_DIST_TO_TOWER_NODE = 1;

    public static final String MISSING_MAXHEIGHT = "missing_maxheight";
    public static final String MISSING_MAXWEIGHT = "missing_maxweight";
    public static final String MISSING_BRIDGE = "missing_bridge";

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

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
                          @QueryParam("limit") @DefaultValue("2000") int limit) {
        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, OSMWayID.KEY))
            if (!encodingManager.hasEncodedValue(key))
                throw new IllegalArgumentException("You need to configure GraphHopper with graph.encoded_values: "
                        + "road_class, road_environment, osm_way_id but " + key + " is missing");

        Set<String> types = typesStr == null || typesStr.isEmpty()
                ? new HashSet<>(Arrays.asList(MISSING_MAXHEIGHT, MISSING_MAXWEIGHT, MISSING_BRIDGE))
                : new HashSet<>(Arrays.asList(typesStr.split(",")));

        StopWatch sw = new StopWatch().start();
        BBox bbox = parseBBox(bboxStr);
        // an empty list means every road class
        Set<String> roads = roadsStr.isEmpty() ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(roadsStr.split(",")));
        List<Map<String, Object>> features = findIssues(bbox, types, roads, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FeatureCollection");
        result.put("features", features);
        sw.stop();
        logger.debug("osm-issues " + bbox + " took: " + sw.getMillis() + "ms, issues: " + features.size());
        return Response.ok(result).header("X-GH-Took", "" + sw.getMillis()).build();
    }

    private List<Map<String, Object>> findIssues(BBox bbox, Set<String> types, Set<String> roads, int limit) {
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

        if (types.contains(MISSING_MAXHEIGHT)) {
            for (int i = 0; i < bridgeIds.size() && features.size() < limit; i++) {
                int bridgeId = bridgeIds.get(i);
                LineString bridgeLS = geometries.get(bridgeId);
                EdgeIteratorState bridge = graph.getEdgeIteratorStateForKey(bridgeId * 2);
                for (Object o : tree.query(bridgeLS.getEnvelopeInternal())) {
                    int belowId = (Integer) o;
                    if (belowId == bridgeId) continue;
                    EdgeIteratorState below = graph.getEdgeIteratorStateForKey(belowId * 2);
                    // a bridge or tunnel below a bridge does not need a max_height
                    if (isSeparatedLevel(below.get(roadEnvEnc))) continue;
                    // a way that has a maxheight tag we cannot parse, like "default", is tagged just
                    // fine, and maxheight:signed=no says a mapper checked that there is no sign
                    if (below.getValue(MAX_HEIGHT_TAG) != null || !isMotorized(below.get(roadClassEnc))) continue;
                    if ("no".equals(below.getValue(MAX_HEIGHT_SIGNED_TAG))) continue;
                    if (!wanted(below.get(roadClassEnc), roads)) continue;
                    Coordinate at = crossing(bridge, bridgeLS, below, geometries.get(belowId), bbox);
                    if (at != null)
                        addFeature(features, reported, MISSING_MAXHEIGHT, at, below, bridge, wayIdEnc, roadClassEnc);
                }
            }
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
        int wayId = edge.get(wayIdEnc);
        int otherWayId = otherEdge == null ? 0 : otherEdge.get(wayIdEnc);
        // report every way (or pair of ways) only once even if it is split into multiple edges
        String key = otherEdge == null ? type + "-" + wayId
                : MISSING_BRIDGE.equals(type) ? type + "-" + Math.min(wayId, otherWayId) + "-" + Math.max(wayId, otherWayId)
                : type + "-" + wayId + "-" + otherWayId;
        if (!reported.add(key)) return;

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", type);
        properties.put("way_id", wayId);
        properties.put("way_name", edge.getName());
        properties.put("road_class", edge.get(roadClassEnc).toString());
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

    /**
     * The road classes are offered in three groups, so they can be combined as needed.
     *
     * @param roads the wanted groups, an empty set means all of them
     */
    private static boolean wanted(RoadClass roadClass, Set<String> roads) {
        return roads.isEmpty() || roads.contains(group(roadClass));
    }

    private static String group(RoadClass roadClass) {
        switch (roadClass) {
            case MOTORWAY:
            case TRUNK:
                return "main";
            case PRIMARY:
            case SECONDARY:
            case TERTIARY:
            case RESIDENTIAL:
                return "normal";
            default:
                return "rest";
        }
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
