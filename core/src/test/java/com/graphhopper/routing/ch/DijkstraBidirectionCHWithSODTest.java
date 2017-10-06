package com.graphhopper.routing.ch;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
import org.junit.Test;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI_SOD;
import static org.junit.Assert.assertEquals;

public class DijkstraBidirectionCHWithSODTest extends DijkstraBidirectionCHTest {

    @Override
    protected AlgorithmOptions createAlgorithmOptions() {
        return AlgorithmOptions.start().
                weighting(new ShortestWeighting(carEncoder))
                .algorithm(DIJKSTRA_BI_SOD).build();
    }

    // 7------8------.---9----0
    // |      | \    |   |
    // 6------   |   |   |
    // |      |  1   |   |
    // 5------   |   |  /
    // |  _,--|   2  | /
    // |/         |  |/
    // 4----------3--/
    @Test
    public void testStallingNodesReducesNumberOfVisitedNodes() {
        GraphHopperStorage graph = createGHStorage(false);
        graph.edge(8, 9, 100, false);
        graph.edge(8, 3, 2, false);
        graph.edge(8, 5, 1, false);
        graph.edge(8, 6, 1, false);
        graph.edge(8, 7, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(1, 8, 1, false);
        graph.edge(2, 3, 3, false);
        for (int i = 3; i < 7; ++i) {
            graph.edge(i, i + 1, 1, false);
        }
        graph.edge(9, 0, 1, false);
        graph.edge(3, 9, 200, false);
        CHGraph chGraph = graph.getGraph(CHGraph.class);

        // explicitly set the node levels equal to the node ids
        // the graph contraction with this ordering yields no shortcuts
        for (int i = 0; i < 10; ++i) {
            chGraph.setLevel(i, i);
        }
        graph.freeze();
        RoutingAlgorithm algo = createCHAlgo(graph, chGraph);
        Path p = algo.calcPath(1, 0);
        // node 3 will be stalled and nodes 4-7 won't be explored --> we visit 7 instead of 11 nodes
        // note that node 9 will be visited by both forward and backward searches
        assertEquals(7, algo.getVisitedNodes());
        assertEquals(102, p.getDistance(), 1. - 3);
        assertEquals(p.toString(), Helper.createTList(1, 8, 9, 0), p.calcNodes());
    }

    private RoutingAlgorithm createCHAlgo(GraphHopperStorage graph, CHGraph lg) {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(new GHDirectory("", DAType.RAM_INT),
                graph, lg,
                createAlgorithmOptions().getWeighting(), TraversalMode.NODE_BASED);
        return ch.createAlgo(lg, createAlgorithmOptions());
    }


}
