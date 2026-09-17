package com.graphhopper.routing.util;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.util.PointValueImporter.Config;
import com.graphhopper.routing.util.PointValueImporter.Pick;
import com.graphhopper.routing.util.PointValueImporter.Point;
import com.graphhopper.search.KVStorage.KValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PointValueImporterTest {

    private static final double INF = Double.POSITIVE_INFINITY;

    private final DecimalEncodedValue heightEnc = new DecimalEncodedValueImpl("max_height", 7, 0, 0.1, false, false, true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 7, 0, 1, false, true, false);
    private final BaseGraph graph = new BaseGraph.Builder(EncodingManager.start().add(heightEnc).add(speedEnc).build()).create();

    /**
     * two parallel west-east edges 20m apart: 0-1 "Main Street" in the south and 2-3 "Side Street" in the north
     */
    private LocationIndexTree createGraph() {
        graph.getNodeAccess().setNode(0, 50.0, 10.0);
        graph.getNodeAccess().setNode(1, 50.0, 10.01);
        graph.getNodeAccess().setNode(2, 50.00018, 10.0);
        graph.getNodeAccess().setNode(3, 50.00018, 10.01);
        graph.edge(0, 1).set(heightEnc, 3.1).setKeyValues(Map.of("street_name", new KValue("Main Street")));
        graph.edge(2, 3).set(heightEnc, INF).setKeyValues(Map.of("street_name", new KValue("Side Street")));
        LocationIndexTree index = new LocationIndexTree(graph, new GHDirectory("", DAType.RAM));
        index.prepareIndex();
        return index;
    }

    private static Config config(String ev, Pick pick, boolean overwrite) {
        return new Config("", ev, pick, overwrite, 15, 30);
    }

    @Test
    void pickMinRoundsDownAndOverwriteReplaces() {
        LocationIndexTree index = createGraph();
        // 2.65 is below the OSM value 3.1 and has to be stored as 2.6, not 2.7
        PointValueImporter.apply(graph, index, heightEnc, config("max_height", Pick.MIN, false),
                List.of(new Point(50.0, 10.005, 2.65, "", Double.NaN), new Point(50.00018, 10.005, 4.5, "", Double.NaN)));
        assertEquals(2.6, graph.getEdgeIteratorState(0, 1).get(heightEnc), 1e-6);
        assertEquals(4.5, graph.getEdgeIteratorState(1, 3).get(heightEnc), 1e-6);

        PointValueImporter.apply(graph, index, heightEnc, config("max_height", Pick.MIN, true),
                List.of(new Point(50.0, 10.005, 5, "", Double.NaN)));
        assertEquals(5, graph.getEdgeIteratorState(0, 1).get(heightEnc), 1e-6);

        // pick max rounds the other way
        PointValueImporter.apply(graph, index, heightEnc, config("max_height", Pick.MAX, true),
                List.of(new Point(50.0, 10.005, 4.55, "", Double.NaN)));
        assertEquals(4.6, graph.getEdgeIteratorState(0, 1).get(heightEnc), 1e-6);
    }

    @Test
    void nameAndMaxDistance() {
        LocationIndexTree index = createGraph();
        // closer to Main Street, but the name says Side Street. The last point is too far away.
        PointValueImporter.apply(graph, index, heightEnc, config("max_height", Pick.MIN, true),
                List.of(new Point(50.00007, 10.005, 4, "Side Str.", Double.NaN), new Point(50.001, 10.005, 2, "", Double.NaN)));
        assertEquals(3.1, graph.getEdgeIteratorState(0, 1).get(heightEnc), 1e-6);
        assertEquals(4, graph.getEdgeIteratorState(1, 3).get(heightEnc), 1e-6);
    }

    @Test
    void headingSelectsDirection() {
        LocationIndexTree index = createGraph();
        // heading 270 is westbound, i.e. against the direction 0->1
        PointValueImporter.apply(graph, index, speedEnc, config("speed", Pick.MAX, true),
                List.of(new Point(50.0, 10.005, 30, "", 270)));
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        assertEquals(0, edge.get(speedEnc));
        assertEquals(30, edge.getReverse(speedEnc));
    }

    @Test
    void importWithNewEncodedValue(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("points.json");
        Files.writeString(file, """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","geometry":{"type":"Point","coordinates":[7.4204,43.7334]},"properties":{"value":42}}]}""");
        GraphHopperConfig config = new GraphHopperConfig();
        config.putObject("datareader.file", "../core/files/monaco.osm.gz");
        config.putObject("graph.location", dir.resolve("gh").toString());
        config.putObject("import.osm.ignored_highways", "");
        config.putObject("graph.encoded_values", "car_access, bridge_class|max=200|default=infinity");
        config.putObject("import.point_values", List.of(Map.of("file", file.toString(), "encoded_value", "bridge_class", "pick", "min", "max_distance", 50)));
        config.setProfiles(List.of(new Profile("car").setCustomModel(new com.graphhopper.util.CustomModel()
                .addToPriority(If("!car_access", MULTIPLY, "0")).addToSpeed(If("true", com.graphhopper.json.Statement.Op.LIMIT, "100")))));
        GraphHopper hopper = new GraphHopper().init(config).importOrLoad();

        DecimalEncodedValue enc = hopper.getEncodingManager().getDecimalEncodedValue("bridge_class");
        Snap snap = hopper.getLocationIndex().findClosest(43.7334, 7.4204, EdgeFilter.ALL_EDGES);
        assertEquals(42, snap.getClosestEdge().get(enc));
        assertEquals(INF, hopper.getBaseGraph().getEdgeIteratorState(0, Integer.MIN_VALUE).get(enc));
        hopper.close();
    }
}
