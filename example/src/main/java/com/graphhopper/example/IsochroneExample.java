package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.index.Snap;

import java.util.concurrent.atomic.AtomicInteger;

public class IsochroneExample {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
        // get encoder from GraphHopper instance
        EncodingManager encodingManager = hopper.getEncodingManager();
        FlagEncoder encoder = encodingManager.getEncoder("car");

        // snap some GPS coordinates to the routing graph and build a query graph
        Snap snap = hopper.getLocationIndex().findClosest(42.508679, 1.532078, DefaultEdgeFilter.allEdges(encoder));
        QueryGraph queryGraph = QueryGraph.create(hopper.getGraphHopperStorage(), snap);

        // run the isochrone calculation
        ShortestPathTree tree = new ShortestPathTree(queryGraph, new FastestWeighting(encoder), false, TraversalMode.NODE_BASED);
        // find all nodes that are within a radius of 120s
        tree.setTimeLimit(120_000);

        AtomicInteger counter = new AtomicInteger(0);
        // you need to specify a callback to define what should be done
        tree.search(snap.getClosestNode(), label -> {
            // see IsoLabel.java for more properties
            // System.out.println("node: " + label.node + ", time: " + label.time + ", distance: " + label.distance);
            counter.incrementAndGet();
        });
        assert counter.get() > 200;
    }

    /**
     * See {@link RoutingExample#createGraphHopperInstance} for more comments on creating the GraphHopper instance.
     */
    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopperOSM();
        hopper.setDataReaderFile(ghLoc);
        hopper.setGraphHopperLocation("target/isochrone-graph-cache");
        hopper.setEncodingManager(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));
        hopper.importOrLoad();
        return hopper;
    }
}
