package com.graphhopper.example;

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
import com.graphhopper.util.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.standard.IncrementalTin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Head-to-head: JTS Delaunay+ContourBuilder vs Tinfour IncrementalTin+ContourBuilderForTin,
 * over the identical isochrone site set. Options: grid-snap (meters), min edge length for midpoint (meters).
 */
public class IsochroneBenchmark3 {

    public static void main(String[] args) {
        String pbf = "/Users/Shared/Downloads/bayern-latest.osm.pbf";
        double lat = Double.parseDouble(System.getProperty("lat", "49.0134"));
        double lon = Double.parseDouble(System.getProperty("lon", "12.1016"));
        long timeLimitSec = Long.getLong("limitSec", 2400);
        String profileName = System.getProperty("profile", "car_edge");

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

        int nBuckets = 5;
        double delta = limit / nBuckets;
        double[] zLevels = new double[nBuckets];
        for (int i = 0; i < nBuckets; i++) zLevels[i] = (i + 1) * delta;

        System.out.println("profile=" + profileName + " (" + tmode + ") point=" + lat + "," + lon + " limit=" + timeLimitSec + "s");

        // variant: gridNN (snap meters), minlenNN (min edge length in m for midpoint), hilbert (presort for Tinfour)
        String[] variants = {"base", "hilbert", "sptdfs", "grid100+hilbert"};

        for (String variant : variants) {
            double gridM = 0, minLenM = 0;
            boolean hilbert = variant.contains("hilbert");
            boolean sptdfs = variant.contains("sptdfs");
            for (String p : variant.split("\\+")) {
                if (p.startsWith("grid")) gridM = Double.parseDouble(p.substring(4));
                if (p.startsWith("minlen")) minLenM = Double.parseDouble(p.substring(6));
            }
            double gridDeg = gridM / DistanceCalcEarth.METERS_PER_DEGREE;

            for (int run = 0; run < 3; run++) {
                // ---- collect labels, then build sites in the chosen order ----
                final ArrayList<ShortestPathTree.IsoLabel> labels = new ArrayList<>();
                final double minLen = minLenM;
                ShortestPathTree tree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
                tree.setTimeLimit(searchLimit);
                StopWatch swCollect = new StopWatch().start();
                tree.search(snap.getClosestNode(), labels::add);
                // sptdfs: feed Tinfour in shortest-path-tree pre-order (each node right after its parent = spatially adjacent)
                StopWatch swReorder = new StopWatch().start();
                List<ShortestPathTree.IsoLabel> ordered = sptdfs ? dfsOrder(labels) : labels;
                swReorder.stop();
                long reorderMs = swReorder.getMillis();
                ArrayList<Coordinate> sites = new ArrayList<>(ordered.size());
                for (ShortestPathTree.IsoLabel l : ordered) addSites(sites, na, queryGraph, l, fz, minLen);
                if (gridDeg > 0) {
                    java.util.HashMap<Long, Coordinate> cells = new java.util.HashMap<>(sites.size() * 2);
                    for (Coordinate c : sites) {
                        long gx = Math.round(c.x / gridDeg), gy = Math.round(c.y / gridDeg);
                        long key = (gx << 32) ^ (gy & 0xffffffffL);
                        Coordinate prev = cells.get(key);
                        if (prev == null || c.z < prev.z) cells.put(key, c);
                    }
                    sites = new ArrayList<>(cells.values());
                }
                swCollect.stop();
                int nSites = sites.size();
                // count exact-duplicate coordinates (same lon/lat)
                java.util.HashSet<Long> distinctXY = new java.util.HashSet<>(nSites * 2);
                for (Coordinate c : sites)
                    distinctXY.add((Double.doubleToLongBits(c.x) * 31) ^ Double.doubleToLongBits(c.y));
                int exactDupes = nSites - distinctXY.size();

                // ================= JTS =================
                StopWatch swJtsBuild = new StopWatch().start();
                DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();
                builder.setSites(sites);
                builder.setTolerance(0);
                Geometry hull = builder.getEdges(new GeometryFactory()).convexHull();
                QuadEdgeSubdivision tin = builder.getSubdivision();
                for (Vertex v : (Collection<Vertex>) tin.getVertices(true))
                    if (tin.isFrameVertex(v)) v.setZ(Double.MAX_VALUE);
                ReadableTriangulation rt = ReadableTriangulation.wrap(tin);
                var seed = rt.getEdges();
                swJtsBuild.stop();
                StopWatch swJtsContour = new StopWatch().start();
                ContourBuilder cb = new ContourBuilder(rt);
                double jtsOuterArea = 0, jtsSumArea = 0;
                for (int i = 0; i < nBuckets; i++) {
                    MultiPolygon iso = cb.computeIsoline(zLevels[i], seed);
                    jtsSumArea += iso.getArea();
                    if (i == nBuckets - 1) jtsOuterArea = iso.getArea();
                }
                swJtsContour.stop();

                // ================= Tinfour =================
                StopWatch swTinBuild = new StopWatch().start();
                List<org.tinfour.common.Vertex> tv = new ArrayList<>(sites.size());
                double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE, yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
                for (Coordinate c : sites) {
                    tv.add(new org.tinfour.common.Vertex(c.x, c.y, c.z));
                    xMin = Math.min(xMin, c.x); xMax = Math.max(xMax, c.x);
                    yMin = Math.min(yMin, c.y); yMax = Math.max(yMax, c.y);
                }
                double spacing = Math.sqrt(Math.max(1e-12, (xMax - xMin) * (yMax - yMin)) / Math.max(1, tv.size()));
                IncrementalTin itin = new IncrementalTin(spacing);
                if (hilbert) new org.tinfour.utils.HilbertSort().sort(tv);
                itin.add(tv, null);
                swTinBuild.stop();
                StopWatch swTinContour = new StopWatch().start();
                // single pass builds ALL levels + closed regions (this is Tinfour's advantage)
                ContourBuilderForTin cbt = new ContourBuilderForTin(itin, null, zLevels, true);
                List<ContourRegion> regionsAll = cbt.getRegions();
                swTinContour.stop();

                // ---- correct outer-area check: single-level build at z=limit ----
                // regions carry signed area (getArea): outer shells positive, holes negative when summed by side.
                ContourBuilderForTin cbtOuter = new ContourBuilderForTin(itin, null, new double[]{limit}, true);
                double tinOuterArea = 0;
                for (ContourRegion r : cbtOuter.getRegions()) {
                    // regionIndex 0 = below limit (reachable). getArea() is nesting-adjusted signed area.
                    if (r.getRegionIndex() == 0) tinOuterArea += Math.abs(r.getArea());
                }

                System.out.printf("%-16s run%d | sites=%7d dupes=%6d | reorderOnly=%4d collect=%5d | JTS: build=%5d contour=%5d tot=%5d areaOut=%.5f | TIN: build=%5d contour=%5d tot=%5d areaOut=%.5f | TIN/JTS=%.2fx%n",
                        variant, run, nSites, exactDupes, reorderMs, swCollect.getMillis(),
                        swJtsBuild.getMillis(), swJtsContour.getMillis(), swJtsBuild.getMillis() + swJtsContour.getMillis(), jtsOuterArea,
                        swTinBuild.getMillis(), swTinContour.getMillis(), swTinBuild.getMillis() + swTinContour.getMillis(), tinOuterArea,
                        (double) (swJtsBuild.getMillis() + swJtsContour.getMillis()) / Math.max(1, swTinBuild.getMillis() + swTinContour.getMillis()));
            }
        }
    }

    // shortest-path-tree pre-order (DFS): each node emitted right after its parent
    static List<ShortestPathTree.IsoLabel> dfsOrder(List<ShortestPathTree.IsoLabel> labels) {
        java.util.IdentityHashMap<ShortestPathTree.IsoLabel, List<ShortestPathTree.IsoLabel>> children = new java.util.IdentityHashMap<>();
        ShortestPathTree.IsoLabel root = null;
        for (ShortestPathTree.IsoLabel l : labels) {
            if (l.parent == null) root = l;
            else children.computeIfAbsent(l.parent, k -> new ArrayList<>()).add(l);
        }
        List<ShortestPathTree.IsoLabel> out = new ArrayList<>(labels.size());
        if (root == null) return labels;
        java.util.ArrayDeque<ShortestPathTree.IsoLabel> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ShortestPathTree.IsoLabel cur = stack.pop();
            out.add(cur);
            List<ShortestPathTree.IsoLabel> ch = children.get(cur);
            if (ch != null) for (ShortestPathTree.IsoLabel c : ch) stack.push(c);
        }
        return out;
    }

    static void addSites(ArrayList<Coordinate> sites, NodeAccess na, QueryGraph queryGraph,
                         ShortestPathTree.IsoLabel label, ToDoubleFunction<ShortestPathTree.IsoLabel> fz, double minLenMeters) {
        double ev = fz.applyAsDouble(label);
        Coordinate site = new Coordinate(na.getLon(label.node), na.getLat(label.node));
        site.z = ev;
        sites.add(site);
        if (label.parent != null) {
            EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
            if (minLenMeters > 0 && edge.getDistance() < minLenMeters) return; // skip midpoint for short edges
            PointList inner = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
            if (inner.size() > 0) {
                int mid = inner.size() / 2;
                if (inner.size() % 2 == 0 && edge.get(EdgeIteratorState.REVERSE_STATE)) mid -= 1;
                Coordinate s2 = new Coordinate(inner.getLon(mid), inner.getLat(mid));
                s2.z = ev;
                sites.add(s2);
            }
        }
    }

    // signed shoelace area of a closed [x0,y0,x1,y1,...] ring (degrees^2)
    static double shoelaceDeg(double[] xy) {
        double a = 0;
        int n = xy.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = xy[2 * i], yi = xy[2 * i + 1], xj = xy[2 * j], yj = xy[2 * j + 1];
            a += (xj * yi - xi * yj);
        }
        return a / 2.0;
    }
}
