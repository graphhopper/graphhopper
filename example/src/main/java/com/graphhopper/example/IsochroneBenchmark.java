package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.JTSTriangulator;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.routing.RouterConfig;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

/**
 * Standalone benchmark to compare node-based vs edge-based isochrone timings,
 * splitting SPT search from post-processing (triangulation + contour building).
 */
public class IsochroneBenchmark {

    public static void main(String[] args) {
        String pbf = args.length >= 1 ? args[0] : "/Users/Shared/Downloads/bayern-latest.osm.pbf";
        // Munich center
        double lat = args.length >= 3 ? Double.parseDouble(args[1]) : 48.1372;
        double lon = args.length >= 3 ? Double.parseDouble(args[2]) : 11.5756;
        long timeLimitSec = 1800; // 30 min isochrone -> big

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

        Triangulator triangulator = new JTSTriangulator(new RouterConfig());

        for (String profileName : new String[]{"car_node", "car_edge"}) {
            Profile profile = hopper.getProfile(profileName);
            boolean edgeBased = profile.hasTurnCosts();
            TraversalMode tmode = edgeBased ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            Weighting weighting = hopper.createWeighting(profile, new PMap());
            Snap snap = hopper.getLocationIndex().findClosest(lat, lon,
                    new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName))));
            QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), snap);

            System.out.println("\n=== " + profileName + " (" + tmode + ") limit=" + timeLimitSec + "s ===");

            for (int run = 0; run < 3; run++) {
                // ---- Phase A: pure SPT search (count only) ----
                ShortestPathTree treeA = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
                double limit = timeLimitSec * 1000d;
                treeA.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
                AtomicInteger cnt = new AtomicInteger();
                StopWatch swA = new StopWatch().start();
                treeA.search(snap.getClosestNode(), l -> cnt.incrementAndGet());
                swA.stop();

                // ---- Phase B: triangulate (SPT search + site collection + Delaunay) ----
                ShortestPathTree treeB = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
                treeB.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
                ToDoubleFunction<ShortestPathTree.IsoLabel> fz = l -> l.time;
                StopWatch swB = new StopWatch().start();
                Triangulator.Result result = triangulator.triangulate(snap, queryGraph, treeB, fz, IsochroneBenchmark.degreesFromMeters(0));
                swB.stop();

                // ---- Phase C: contour building (5 buckets) ----
                StopWatch swC = new StopWatch().start();
                ContourBuilder contourBuilder = new ContourBuilder(result.triangulation);
                ArrayList<Geometry> isos = new ArrayList<>();
                int nBuckets = 5;
                double delta = limit / nBuckets;
                for (int i = 0; i < nBuckets; i++) {
                    MultiPolygon iso = contourBuilder.computeIsoline((i + 1) * delta, result.seedEdges);
                    isos.add(iso);
                }
                swC.stop();

                System.out.printf("run %d | labels(A)=%d | A_search=%5d ms | B_triangulate=%5d ms | C_contour(5)=%5d ms%n",
                        run, cnt.get(), swA.getMillis(), swB.getMillis(), swC.getMillis());
            }
        }
    }

    static double degreesFromMeters(double m) {
        return m / com.graphhopper.util.DistanceCalcEarth.METERS_PER_DEGREE;
    }
}
