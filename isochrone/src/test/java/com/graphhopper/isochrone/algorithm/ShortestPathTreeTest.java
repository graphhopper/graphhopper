package com.graphhopper.isochrone.algorithm;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class ShortestPathTreeTest {

    private final EncodingManager encodingManager = EncodingManager.create("car");
    private final FlagEncoder carEncoder = encodingManager.getEncoder("car");
    private GraphHopperStorage graph;

    @BeforeEach
    public void setUp() {
        graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false);
        graph.create(1000);
    }

    @AfterEach
    public void tearDown() {
        graph.close();
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    private void fillTestGraph(Graph graph) {
        GHUtility.setProperties(graph.edge(0, 1).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(0, 4).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(1, 4).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 5).setDistance(70), carEncoder, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 2).setDistance(200), carEncoder, 10, true, true);

        GHUtility.setProperties(graph.edge(5, 2).setDistance(50), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(2, 3).setDistance(50), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 3).setDistance(110), carEncoder, 20, true, false);
        GHUtility.setProperties(graph.edge(3, 7).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(4, 6).setDistance(50), carEncoder, 20, true, false);
        GHUtility.setProperties(graph.edge(5, 4).setDistance(70), carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 6).setDistance(70), carEncoder, 10, true, false);
        GHUtility.setProperties(graph.edge(7, 5).setDistance(50), carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(6, 7).setDistance(50), carEncoder, 20, true, true);
    }

    @Test
    public void testSearch25Seconds() {
        fillTestGraph(graph);
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false);
        instance.setTimeLimit(26_000);
        instance.search(0, result::add);
        result.sort(comparing(label -> label.adjNode));
        assertEquals(5, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(25200, result.get(1).time),
                () -> assertEquals(9000, result.get(2).time),
                () -> assertEquals(18000, result.get(3).time),
                () -> assertEquals(27000, result.get(4).time)
        );
    }

    @Test
    public void testSearch60Seconds() {
        fillTestGraph(graph);
        List<ShortestPathTree.IsoLabel> result = new ArrayList<>();
        ShortestPathTree instance = new ShortestPathTree(graph, new FastestWeighting(carEncoder, new PMap()), false);
        instance.setTimeLimit(60_000);
        instance.search(0, result::add);
        result.sort(comparing(label -> label.adjNode));
        assertEquals(8, result.size());
        assertAll(
                () -> assertEquals(0, result.get(0).time),
                () -> assertEquals(25200, result.get(1).time),
                () -> assertEquals(54000, result.get(2).time),
                () -> assertEquals(55800, result.get(3).time),
                () -> assertEquals(9000, result.get(4).time),
                () -> assertEquals(36000, result.get(5).time),
                () -> assertEquals(18000, result.get(6).time),
                () -> assertEquals(27000, result.get(7).time)
        );
    }

}
