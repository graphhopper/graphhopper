package com.graphhopper.routing;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class AlternativeDijkstraTest
{
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager(carFE);

    Graph createTestGraph()
    {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), em, false);
        graph.create(1000);

        /*   9
             /\
            1 2-3-4
            |  /  |
            5-6-7-8
         */
        graph.edge(1, 9, 1, true);
        graph.edge(9, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);

        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        graph.edge(7, 8, 1, true);

        graph.edge(1, 5, 2, true);
        graph.edge(6, 3, 1, true);
        graph.edge(4, 8, 1, true);
        return graph;
    }

    @Test
    public void testCalcPaths() throws Exception
    {
        // TODO TraversalMode tMode = TraversalMode.EDGE_BASED_2DIR;
        TraversalMode tMode = TraversalMode.NODE_BASED;
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph();
        AlternativeDijkstra altDijkstra = new AlternativeDijkstra(g, carFE, weighting, tMode);
        List<AlternativeDijkstra.AlternativeInfo> pathInfos = altDijkstra.calcPaths(5, 4, 2, 0.3, 2);
        checkAlternatives(pathInfos);
        assertEquals(2, pathInfos.size());

        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g, carFE, weighting, tMode);
        Path bestPath = dijkstra.calcPath(5, 4);

        Path bestAlt = pathInfos.get(0).getPath();
        Path secondAlt = pathInfos.get(1).getPath();

        assertEquals(bestPath.calcNodes(), bestAlt.calcNodes());

        assertEquals(Helper.createTList(5, 6, 3, 4), bestAlt.calcNodes());

        // Note: here plateau is longer, even longer than optimum, but path is longer
        // so which alternative is better? longer plateau.weight with bigger path.weight or smaller path.weight with smaller plateau.weight
        // assertEquals(Helper.createTList(5, 1, 9, 2, 3, 4), secondAlt.calcNodes());
        assertEquals(Helper.createTList(5, 6, 7, 8, 4), secondAlt.calcNodes());

        altDijkstra = new AlternativeDijkstra(g, carFE, weighting, tMode);
        pathInfos = altDijkstra.calcPaths(5, 4, 3, 0.3, 2);
        checkAlternatives(pathInfos);
        assertEquals(3, pathInfos.size());

        // result is sorted based on the plateau to full weight ratio
        assertEquals(Helper.createTList(5, 6, 3, 4), pathInfos.get(0).getPath().calcNodes());
        assertEquals(Helper.createTList(5, 6, 7, 8, 4), pathInfos.get(1).getPath().calcNodes());
        assertEquals(Helper.createTList(5, 1, 9, 2, 3, 4), pathInfos.get(2).getPath().calcNodes());
    }

    void checkAlternatives(List<AlternativeDijkstra.AlternativeInfo> alternativeInfos)
    {
        for (AlternativeDijkstra.AlternativeInfo a : alternativeInfos)
        {
            if (a.getPlateauWeight() > a.getPath().getWeight())
                assertTrue("plateau or sortby incorrect -> " + a, false);
        }
    }
}