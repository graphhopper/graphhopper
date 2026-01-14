package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.search.KVStorage.KValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.Map;

public class LocationIndexExample {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        graphhopperLocationIndex(relDir);
        lowLevelLocationIndex();
    }

    public static void graphhopperLocationIndex(String relDir) {
        GraphHopper hopper = new GraphHopper();
        hopper.setEncodedValuesString("car_access, car_average_speed, road_access, max_speed, road_environment, ferry_speed");
        hopper.setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")));
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
        BaseGraph graph = new BaseGraph.Builder(4).create();
        graph.edge(0, 1).setKeyValues(Map.of("name", new KValue( "test edge")));
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
