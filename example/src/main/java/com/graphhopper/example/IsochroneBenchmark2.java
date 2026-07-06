package com.graphhopper.example;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ReadableTriangulation;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.TurnCostsConfig;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.ToDoubleFunction;

/**
 * Deep-dive benchmark: splits triangulation into (site collection) vs (Delaunay build),
 * counts raw vs unique sites, and tests precision-compromise variants for edge-based.
 *
 * Variants (bitmask via label):
 *   base       = current behavior (node coord + pillar midpoint per label)
 *   noPillar   = drop the pillar midpoint site
 *   nodeDedup  = for edge-based, collapse labels to one per node keeping min explore value
 */
public class IsochroneBenchmark2 {

    public static void main(String[] args) {
        String pbf = args.length >= 1 ? args[0] : "/Users/Shared/Downloads/bayern-latest.osm.pbf";
        double lat = Double.parseDouble(System.getProperty("lat", "48.1372"));
        double lon = Double.parseDouble(System.getProperty("lon", "11.5756"));
        long timeLimitSec = Long.getLong("limitSec", 1800);

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(pbf);
        hopper.setGraphHopperLocation("target/iso-bench-cache");
        hopper.setEncodedValuesString("car_access, road_environment, ferry_speed, car_average_speed, max_speed");
        hopper.setProfiles(
                new Profile("car_node").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")),
                new Profile("car_edge").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                        .setTurnCostsConfig(TurnCostsConfig.car())
        );
        hopper.importOrLoad();

        String only = System.getProperty("only", "");
        String[] profiles = only.isEmpty() ? new String[]{"car_node", "car_edge"} : new String[]{only};
        for (String profileName : profiles) {
            Profile profile = hopper.getProfile(profileName);
            boolean edgeBased = profile.hasTurnCosts();
            TraversalMode tmode = edgeBased ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            Weighting weighting = hopper.createWeighting(profile, new PMap());
            Snap snap = hopper.getLocationIndex().findClosest(lat, lon,
                    new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName))));
            QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), snap);
            NodeAccess na = queryGraph.getNodeAccess();
            double limit = timeLimitSec * 1000d;
            double searchLimit = limit + Math.max(limit * 0.14, 200_000);
            ToDoubleFunction<ShortestPathTree.IsoLabel> fz = l -> l.time;

            System.out.println("\n======== " + profileName + " (" + tmode + ") ========");

            // variant name may carry a JTS tolerance "tolNNN" or a pre-grid-snap "gridNNN" (meters)
            String[] variants = edgeBased
                    ? new String[]{"base", "grid25", "grid50", "grid100", "noPillar", "noPillar+grid50", "nodeDedup+noPillar"}
                    : new String[]{"base", "grid50", "grid100"};

            for (String variant : variants) {
                boolean noPillar = variant.contains("noPillar");
                boolean nodeDedup = variant.contains("nodeDedup");
                double tolMeters = 0, gridMeters = 0;
                for (String part : variant.split("\\+")) {
                    if (part.startsWith("tol")) tolMeters = Double.parseDouble(part.substring(3));
                    if (part.startsWith("grid")) gridMeters = Double.parseDouble(part.substring(4));
                }
                double tolerance = tolMeters / com.graphhopper.util.DistanceCalcEarth.METERS_PER_DEGREE;
                double gridDeg = gridMeters / com.graphhopper.util.DistanceCalcEarth.METERS_PER_DEGREE;

                for (int run = 0; run < 2; run++) {
                    ShortestPathTree tree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
                    tree.setTimeLimit(searchLimit);

                    // ---- site collection ----
                    final ArrayList<Coordinate> collected = new ArrayList<>();

                    StopWatch swCollect = new StopWatch().start();
                    if (nodeDedup) {
                        // first pass: find min explore value per node, and remember one representative edge
                        IntObjectHashMap<ShortestPathTree.IsoLabel> best = new IntObjectHashMap<>();
                        tree.search(snap.getClosestNode(), label -> {
                            double ev = fz.applyAsDouble(label);
                            ShortestPathTree.IsoLabel prev = best.get(label.node);
                            if (prev == null || ev < fz.applyAsDouble(prev))
                                best.put(label.node, label);
                        });
                        for (var c : best.values()) {
                            ShortestPathTree.IsoLabel label = c.value;
                            addSites(collected, na, queryGraph, label, fz, noPillar);
                        }
                    } else {
                        tree.search(snap.getClosestNode(), label ->
                                addSites(collected, na, queryGraph, label, fz, noPillar));
                    }
                    ArrayList<Coordinate> sites = collected;
                    // optional cheap O(n) grid-snap dedup keeping min z per cell
                    if (gridDeg > 0) {
                        java.util.HashMap<Long, Coordinate> cells = new java.util.HashMap<>(sites.size() * 2);
                        for (Coordinate c : sites) {
                            long gx = Math.round(c.x / gridDeg);
                            long gy = Math.round(c.y / gridDeg);
                            long key = (gx << 32) ^ (gy & 0xffffffffL);
                            Coordinate prev = cells.get(key);
                            if (prev == null || c.z < prev.z) cells.put(key, c);
                        }
                        sites = new ArrayList<>(cells.values());
                    }
                    swCollect.stop();
                    int rawSites = sites.size();

                    // ---- Delaunay build ----
                    StopWatch swDelaunay = new StopWatch().start();
                    DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();
                    builder.setSites(sites);
                    builder.setTolerance(tolerance);
                    Geometry convexHull = builder.getEdges(new GeometryFactory()).convexHull();
                    if (!(convexHull instanceof Polygon)) { System.out.println("degenerate"); continue; }
                    QuadEdgeSubdivision tin = builder.getSubdivision();
                    int uniqueVerts = 0;
                    for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
                        uniqueVerts++;
                        if (tin.isFrameVertex(vertex)) vertex.setZ(Double.MAX_VALUE);
                    }
                    ReadableTriangulation triangulation = ReadableTriangulation.wrap(tin);
                    var seedEdges = triangulation.getEdges();
                    swDelaunay.stop();

                    // ---- contour (5 buckets) ----
                    StopWatch swContour = new StopWatch().start();
                    ContourBuilder cb = new ContourBuilder(triangulation);
                    double totalArea = 0;
                    int nBuckets = 5;
                    double delta = limit / nBuckets;
                    for (int i = 0; i < nBuckets; i++) {
                        MultiPolygon iso = cb.computeIsoline((i + 1) * delta, seedEdges);
                        totalArea += iso.getArea();
                    }
                    swContour.stop();

                    System.out.printf("%-20s run%d | rawSites=%7d uniqVerts=%7d | collect=%5dms delaunay=%6dms contour=%5dms | TOTAL=%6dms | area5=%.6f%n",
                            variant, run, rawSites, uniqueVerts,
                            swCollect.getMillis(), swDelaunay.getMillis(), swContour.getMillis(),
                            swCollect.getMillis() + swDelaunay.getMillis() + swContour.getMillis(),
                            totalArea);
                }
            }
        }
    }

    static void addSites(ArrayList<Coordinate> sites, NodeAccess na, QueryGraph queryGraph,
                         ShortestPathTree.IsoLabel label, ToDoubleFunction<ShortestPathTree.IsoLabel> fz, boolean noPillar) {
        double exploreValue = fz.applyAsDouble(label);
        Coordinate site = new Coordinate(na.getLon(label.node), na.getLat(label.node));
        site.z = exploreValue;
        sites.add(site);
        if (!noPillar && label.parent != null) {
            EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
            PointList innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
            if (innerPoints.size() > 0) {
                int midIndex = innerPoints.size() / 2;
                if (innerPoints.size() % 2 == 0 && edge.get(EdgeIteratorState.REVERSE_STATE))
                    midIndex -= 1;
                Coordinate site2 = new Coordinate(innerPoints.getLon(midIndex), innerPoints.getLat(midIndex));
                site2.z = exploreValue;
                sites.add(site2);
            }
        }
    }
}
