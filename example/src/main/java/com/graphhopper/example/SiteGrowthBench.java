package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

/** Shows how the reachable set (labels/sites) grows with time_limit -- the driver of isochrone memory. */
public class SiteGrowthBench {
    public static void main(String[] args) {
        String pbf = "/Users/Shared/Downloads/bayern-latest.osm.pbf";
        double lat = Double.parseDouble(System.getProperty("lat", "49.45"));   // Nuremberg (central) by default
        double lon = Double.parseDouble(System.getProperty("lon", "11.08"));
        String profileName = System.getProperty("profile", "car_node");

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(pbf);
        hopper.setGraphHopperLocation("target/iso-bench-cache");
        hopper.setEncodedValuesString("car_access, road_environment, ferry_speed, car_average_speed, max_speed");
        hopper.setProfiles(
                new Profile("car_node").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")),
                new Profile("car_edge").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                        .setTurnCostsConfig(TurnCostsConfig.car()));
        hopper.importOrLoad();

        Profile profile = hopper.getProfile(profileName);
        TraversalMode tmode = profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
        Weighting weighting = hopper.createWeighting(profile, new PMap());
        Snap snap = hopper.getLocationIndex().findClosest(lat, lon,
                new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName))));
        QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), snap);
        NodeAccess na = queryGraph.getNodeAccess();
        ToDoubleFunction<ShortestPathTree.IsoLabel> fz = l -> l.time;

        System.out.println("point=" + lat + "," + lon + " profile=" + profileName + " (" + tmode + ")");
        System.out.printf("%8s | %10s | %10s | %12s | %12s%n", "t_limit", "labels", "sites", "~SPTmem(MB)", "~sitesMem(MB)");
        long prevSites = 0, prevT = 0;
        for (int minutes : new int[]{30, 45, 60, 75, 90, 120, 150}) {
            long limit = minutes * 60_000L;
            double searchLimit = limit + Math.max(limit * 0.14, 200_000);
            ShortestPathTree tree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, tmode);
            tree.setTimeLimit(searchLimit);
            AtomicInteger labels = new AtomicInteger();
            ArrayList<Coordinate> sites = new ArrayList<>();
            tree.search(snap.getClosestNode(), l -> {
                labels.incrementAndGet();
                IsochroneBenchmark3.addSites(sites, na, queryGraph, l, fz, 0);
            });
            // rough: IsoLabel ~64B held in fromMap + list; each also has a queue slot. site Coordinate ~40B.
            double sptMemMB = labels.get() * 64.0 * 2 / (1024 * 1024);      // fromMap + label objs (approx, x2 for queue refs)
            double sitesMemMB = sites.size() * 40.0 / (1024 * 1024);
            double growth = prevSites == 0 ? 0 : (double) sites.size() / prevSites;
            System.out.printf("%6dmin | %10d | %10d | %12.0f | %12.0f  %s%n",
                    minutes, labels.get(), sites.size(), sptMemMB, sitesMemMB,
                    prevSites == 0 ? "" : String.format("(x%.2f vs %dmin)", growth, prevT));
            prevSites = sites.size();
            prevT = minutes;
        }
    }
}
