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
    /** if the bbox contains more edges than this we refuse to answer and ask the user to zoom in */
    private static final int MAX_EDGES = 30_000;
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
                          @QueryParam("limit") @DefaultValue("2000") int limit) {
        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, MaxHeight.KEY, MaxWeight.KEY, OSMWayID.KEY))
            if (!encodingManager.hasEncodedValue(key))
                throw new IllegalArgumentException("You need to configure GraphHopper with graph.encoded_values: "
                        + "road_class, road_environment, max_height, max_weight, osm_way_id but " + key + " is missing");

        Set<String> types = typesStr == null || typesStr.isEmpty()
                ? new HashSet<>(Arrays.asList(MISSING_MAXHEIGHT, MISSING_MAXWEIGHT, MISSING_BRIDGE))
                : new HashSet<>(Arrays.asList(typesStr.split(",")));

        StopWatch sw = new StopWatch().start();
        BBox bbox = parseBBox(bboxStr);
        List<Map<String, Object>> features = findIssues(bbox, types, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FeatureCollection");
        result.put("features", features);
        sw.stop();
        logger.debug("osm-issues " + bbox + " took: " + sw.getMillis() + "ms, issues: " + features.size());
        return Response.ok(result).header("X-GH-Took", "" + sw.getMillis()).build();
    }

    private List<Map<String, Object>> findIssues(BBox bbox, Set<String> types, int limit) {
        BaseGraph graph = graphHopper.getBaseGraph();
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> roadEnvEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        DecimalEncodedValue maxHeightEnc = encodingManager.getDecimalEncodedValue(MaxHeight.KEY);
        DecimalEncodedValue maxWeightEnc = encodingManager.getDecimalEncodedValue(MaxWeight.KEY);
        IntEncodedValue wayIdEnc = encodingManager.getIntEncodedValue(OSMWayID.KEY);

        IntHashSet visited = new IntHashSet();
        IntArrayList edgeIds = new IntArrayList();
        locationIndex.query(bbox, edgeId -> {
            if (visited.add(edgeId)) edgeIds.add(edgeId);
        });
        if (edgeIds.size() > MAX_EDGES)
            throw new IllegalArgumentException("Too many edges in this area (" + edgeIds.size() + "), please zoom in");

        List<Map<String, Object>> features = new ArrayList<>();
        Set<String> reported = new HashSet<>();

        IntObjectHashMap<LineString> geometries = new IntObjectHashMap<>(edgeIds.size());
        STRtree tree = new STRtree(Math.max(10, edgeIds.size()));
        for (int i = 0; i < edgeIds.size(); i++) {
            int edgeId = edgeIds.get(i);
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeId * 2);
            if (edge.get(roadEnvEnc) == RoadEnvironment.FERRY) continue;
            LineString ls = edge.fetchWayGeometry(FetchMode.ALL).toLineString(false);
            geometries.put(edgeId, ls);
            tree.insert(ls.getEnvelopeInternal(), edgeId);

            // a bridge needs a max_weight regardless of what it crosses (river, railway, road, ...)
            if (types.contains(MISSING_MAXWEIGHT) && edge.get(roadEnvEnc) == RoadEnvironment.BRIDGE
                    && Double.isInfinite(edge.get(maxWeightEnc)) && isMotorized(edge.get(roadClassEnc))) {
                Coordinate at = ls.getCoordinateN(ls.getNumPoints() / 2);
                if (bbox.contains(at.y, at.x))
                    addFeature(features, reported, MISSING_MAXWEIGHT, at, edge, null, wayIdEnc, roadClassEnc);
            }
        }
        for (int i = 0; i < edgeIds.size(); i++) {
            int edgeId = edgeIds.get(i);
            LineString lsA = geometries.get(edgeId);
            if (lsA == null) continue;
            EdgeIteratorState edgeA = graph.getEdgeIteratorStateForKey(edgeId * 2);
            for (Object o : tree.query(lsA.getEnvelopeInternal())) {
                int otherId = (Integer) o;
                // every pair should be checked only once
                if (otherId <= edgeId) continue;
                LineString lsB = geometries.get(otherId);
                EdgeIteratorState edgeB = graph.getEdgeIteratorStateForKey(otherId * 2);
                if (sharesTowerNode(edgeA, edgeB)) continue;
                if (!lsA.intersects(lsB)) continue;
                Geometry intersection = lsA.intersection(lsB);
                if (intersection.isEmpty()) continue;
                Coordinate at = intersection.getCoordinate();
                if (!bbox.contains(at.y, at.x)) continue;
                // if both edges just end here this is an unconnected junction and not a crossing
                if (closeToTowerNode(lsA, at) && closeToTowerNode(lsB, at)) continue;

                RoadEnvironment reA = edgeA.get(roadEnvEnc), reB = edgeB.get(roadEnvEnc);
                EdgeIteratorState bridge = null, below = null;
                if (reA == RoadEnvironment.BRIDGE && reB != RoadEnvironment.BRIDGE) {
                    bridge = edgeA;
                    below = edgeB;
                } else if (reB == RoadEnvironment.BRIDGE && reA != RoadEnvironment.BRIDGE) {
                    bridge = edgeB;
                    below = edgeA;
                }

                if (bridge != null) {
                    RoadEnvironment belowRE = below.get(roadEnvEnc);
                    // a tunnel below a bridge does not need a max_height
                    if (types.contains(MISSING_MAXHEIGHT) && belowRE != RoadEnvironment.TUNNEL
                            && belowRE != RoadEnvironment.FORD && Double.isInfinite(below.get(maxHeightEnc))
                            && isMotorized(below.get(roadClassEnc)))
                        addFeature(features, reported, MISSING_MAXHEIGHT, at, below, bridge, wayIdEnc, roadClassEnc);
                } else if (types.contains(MISSING_BRIDGE) && reA != RoadEnvironment.BRIDGE
                        && reA != RoadEnvironment.TUNNEL && reB != RoadEnvironment.TUNNEL
                        && reA != RoadEnvironment.FORD && reB != RoadEnvironment.FORD) {
                    addFeature(features, reported, MISSING_BRIDGE, at, edgeA, edgeB, wayIdEnc, roadClassEnc);
                }
                if (features.size() >= limit) return features;
            }
        }
        return features;
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
