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
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TurnCostsConfig;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.HilbertSort;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Memory comparison: JTS Delaunay+ContourBuilder (master) vs Tinfour IncrementalTin+HilbertSort.
 * Reports peak heap during the full build+contour and the retained footprint of the triangulation.
 * Run each engine in its own JVM: -Dwhich=jts|tinfour  -Dprofile=car_edge|car_node
 */
public class IsochroneMemBench {

    public static void main(String[] args) {
        String pbf = "/Users/Shared/Downloads/bayern-latest.osm.pbf";
        double lat = 49.0134, lon = 12.1016;
        long timeLimitSec = Long.getLong("limitSec", 2400);
        String which = System.getProperty("which", "jts");
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
        TraversalMode tmode = profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
        Weighting weighting = hopper.createWeighting(profile, new PMap());
        Snap snap = hopper.getLocationIndex().findClosest(lat, lon,
                new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName))));
        QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), snap);
        NodeAccess na = queryGraph.getNodeAccess();
        double limit = timeLimitSec * 1000d;
        double searchLimit = limit + Math.max(limit * 0.14, 200_000);
        ToDoubleFunction<ShortestPathTree.IsoLabel> fz = l -> l.time;
        int nBuckets = 5;
        double[] zLevels = new double[nBuckets];
        for (int i = 0; i < nBuckets; i++) zLevels[i] = (i + 1) * limit / nBuckets;

        // ---- collect sites (shared cost, held for both) ----
        ShortestPathTree tree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
        tree.setTimeLimit(searchLimit);
        ArrayList<Coordinate> sites = new ArrayList<>();
        tree.search(snap.getClosestNode(), l -> IsochroneBenchmark3.addSites(sites, na, queryGraph, l, fz, 0));

        long base = usedHeapAfterGc();
        resetPeak();

        Object retainedRef; // keep the triangulation alive across the retained measurement
        double area = 0;
        if ("tinfour".equals(which)) {
            List<org.tinfour.common.Vertex> tv = new ArrayList<>(sites.size());
            double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE, yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
            for (Coordinate c : sites) {
                tv.add(new org.tinfour.common.Vertex(c.x, c.y, c.z));
                xMin = Math.min(xMin, c.x); xMax = Math.max(xMax, c.x);
                yMin = Math.min(yMin, c.y); yMax = Math.max(yMax, c.y);
            }
            new HilbertSort().sort(tv);
            double spacing = Math.sqrt(Math.max(1e-12, (xMax - xMin) * (yMax - yMin)) / Math.max(1, tv.size()));
            IncrementalTin tin = new IncrementalTin(spacing);
            tin.add(tv, null);
            for (double z : zLevels) {
                ContourBuilderForTin cbt = new ContourBuilderForTin(tin, null, new double[]{z}, true);
                for (ContourRegion r : cbt.getRegions()) if (r.getRegionIndex() == 0) area += Math.abs(r.getArea());
            }
            retainedRef = tin;
        } else {
            DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();
            builder.setSites(sites);
            builder.setTolerance(0);
            builder.getEdges(new GeometryFactory());
            QuadEdgeSubdivision tin = builder.getSubdivision();
            for (Vertex v : (Collection<Vertex>) tin.getVertices(true))
                if (tin.isFrameVertex(v)) v.setZ(Double.MAX_VALUE);
            ReadableTriangulation rt = ReadableTriangulation.wrap(tin);
            var seed = rt.getEdges();
            ContourBuilder cb = new ContourBuilder(rt);
            for (double z : zLevels) {
                MultiPolygon iso = cb.computeIsoline(z, seed);
                area += iso.getArea();
            }
            retainedRef = tin;
        }

        long peak = peakHeapMB();
        long retained = usedHeapAfterGc() - base;

        System.out.printf("which=%-8s limit=%4ds sites=%8d | PEAK heap=%5d MB | peak/site=%4d bytes | area=%.5f | keep=%d%n",
                which, timeLimitSec, sites.size(), peak, (long) (peak * 1024L * 1024L / Math.max(1, sites.size())), area, System.identityHashCode(retainedRef));
    }

    static long usedHeapAfterGc() {
        for (int i = 0; i < 6; i++) {
            System.gc();
            try { Thread.sleep(60); } catch (InterruptedException ignored) {}
        }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    static void resetPeak() {
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.isUsageThresholdSupported() || p.getType().toString().contains("HEAP")) p.resetPeakUsage();
    }

    static long peakHeapMB() {
        long peak = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.getType() == java.lang.management.MemoryType.HEAP && p.getPeakUsage() != null)
                peak += p.getPeakUsage().getUsed();
        return peak / (1024 * 1024);
    }
}
