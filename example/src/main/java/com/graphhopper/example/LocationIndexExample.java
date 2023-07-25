package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;

public class LocationIndexExample {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        graphhopperLocationIndex(relDir);
        lowLevelLocationIndex();
    }

    public static void graphhopperLocationIndex(String relDir) {
        GraphHopper hopper = new GraphHopper();
        hopper.setProfiles(new CustomProfile("car").setVehicle("car"));
        hopper.setOSMFile(relDir + "core/files/andorra.osm.pbf");
        hopper.setGraphHopperLocation("./target/locationindex-graph-cache");
        hopper.importOrLoad();

        LocationIndex index = hopper.getLocationIndex();

        // now you can fetch the closest edge via:
        Snap snap = index.findClosest(42.508552, 1.532936, EdgeFilter.ALL_EDGES);
        EdgeIteratorState edge = snap.getClosestEdge();
        assert edge.getName().equals("Avinguda Meritxell");
    }

    public static void lowLevelLocationIndex() {
        // If you don't use the GraphHopper class you have to use the low level API:
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1).setKeyValues(KVStorage.KeyValue.createKV("name", "test edge"));
        graph.getNodeAccess().setNode(0, 12, 42);
        graph.getNodeAccess().setNode(1, 12.01, 42.01);

        LocationIndexTree index = new LocationIndexTree(graph.getBaseGraph(), graph.getDirectory());
        index.setResolution(300);
        index.setMaxRegionSearch(4);
        if (!index.loadExisting())
            index.prepareIndex();
        Snap snap = index.findClosest(12, 42, EdgeFilter.ALL_EDGES);
        EdgeIteratorState edge = snap.getClosestEdge();
        assert edge.getValue("name").equals("test edge");
    }
}
