package com.graphhopper.routing;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;

import org.junit.Test;

import java.util.List;

import static com.graphhopper.routing.AbstractRoutingAlgorithmTester.updateDistancesFor;
import com.graphhopper.routing.AlternativeRoute.AltDijkstraBidirectionRef;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 */
public class AlternativeRouteTest
{
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager(carFE);
    // TODO private final TraversalMode tMode = TraversalMode.EDGE_BASED_2DIR;
    private final TraversalMode tMode = TraversalMode.NODE_BASED;

    GraphStorage createTestGraph( boolean fullGraph )
    {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), em, false);
        graph.create(1000);

        // fullGraph=false => only 3-8
        /* 9
         _/\
         1  2-3-4-10
         \  /   \
         5-6-7---8
        
         11
         */
        graph.edge(1, 9, 1, true);
        graph.edge(9, 2, 1, true);
        if (fullGraph)
            graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        if (fullGraph)
            graph.edge(4, 10, 1, true);

        graph.edge(5, 6, 1, true);

        graph.edge(6, 7, 1, true);
        graph.edge(7, 8, 1, true);

        if (fullGraph)
            graph.edge(1, 5, 2, true);
        graph.edge(6, 3, 1, true);
        graph.edge(4, 8, 1, true);

        updateDistancesFor(graph, 5, 0.00, 0.05);
        updateDistancesFor(graph, 6, 0.00, 0.10);
        updateDistancesFor(graph, 7, 0.00, 0.15);
        updateDistancesFor(graph, 8, 0.00, 0.25);

        updateDistancesFor(graph, 1, 0.05, 0.00);
        updateDistancesFor(graph, 9, 0.10, 0.05);
        updateDistancesFor(graph, 2, 0.05, 0.10);
        updateDistancesFor(graph, 3, 0.05, 0.15);
        updateDistancesFor(graph, 4, 0.05, 0.25);
        updateDistancesFor(graph, 10, 0.05, 0.30);
        return graph;
    }

    @Test
    public void testCalcAlternatives() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4, 2, 0.3, 2);
        checkAlternatives(pathInfos);
        assertEquals(2, pathInfos.size());

        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g, carFE, weighting, tMode);
        Path bestPath = dijkstra.calcPath(5, 4);

        Path bestAlt = pathInfos.get(0).getPath();
        Path secondAlt = pathInfos.get(1).getPath();

        assertEquals(bestPath.calcNodes(), bestAlt.calcNodes());
        assertEquals(bestPath.getWeight(), bestAlt.getWeight(), 1e-3);

        assertEquals(Helper.createTList(5, 6, 3, 4), bestAlt.calcNodes());

        // Note: here plateau is longer, even longer than optimum, but path is longer
        // so which alternative is better? longer plateau.weight with bigger path.weight or smaller path.weight with smaller plateau.weight
        // assertEquals(Helper.createTList(5, 1, 9, 2, 3, 4), secondAlt.calcNodes());
        assertEquals(Helper.createTList(5, 6, 7, 8, 4), secondAlt.calcNodes());
        assertEquals(1667.9, secondAlt.getWeight(), .1);
    }

    @Test
    public void testCalcAlternatives2() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4, 3, 0.3, 2);
        checkAlternatives(pathInfos);
        assertEquals(3, pathInfos.size());

        // result is sorted based on the plateau to full weight ratio
        assertEquals(Helper.createTList(5, 6, 3, 4), pathInfos.get(0).getPath().calcNodes());
        assertEquals(Helper.createTList(5, 6, 7, 8, 4), pathInfos.get(1).getPath().calcNodes());
        assertEquals(Helper.createTList(5, 1, 9, 2, 3, 4), pathInfos.get(2).getPath().calcNodes());
        assertEquals(2416.0, pathInfos.get(2).getPath().getWeight(), .1);
    }

    @Test
    public void testCalcRoundTrip() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        List<Path> paths = altDijkstra.calcRoundTrips(5, maxDist, 2, 1.2);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3), paths.get(0).calcNodes());

        // no plateau filter => prefer the longer path
        // assertEquals(Helper.createTList(10, 4, 8, 7, 6, 5), paths.get(1).calcNodes());
        assertEquals(Helper.createTList(3, 2, 9, 1, 5), paths.get(1).calcNodes());

        altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        paths = altDijkstra.calcRoundTrips(6, maxDist, 2, 1.2);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(3, 2, 9, 1, 5, 6), paths.get(1).calcNodes());

        altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        paths = altDijkstra.calcRoundTrips(6, maxDist, 2, 1);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    // TODO how to select alternative when the second best is the 'bestForwardPath' backwards?
    @Ignore
    public void testCalcRoundTripWithBiggerPenalty() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        AlternativeRoute altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        List<Path> paths = altDijkstra.calcRoundTrips(6, maxDist, 2, 2);
        assertEquals(2, paths.size());
        // here we get 6,3,4,10 as best forward and 10,4,8,7,6 as best backward but 10,4,3,6 is selected as it looks like the 'alternative'
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    @Test
    public void testCalcRoundTripWhereAlreadyPlateauStartIsDifferentToBestRoute() throws Exception
    {
        // exception occured for 51.074194,13.705444
        Weighting weighting = new FastestWeighting(carFE);

        // now force that start of plateau of alternative is already different edge than optimal route
        GraphStorage g = createTestGraph(false);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, carFE, weighting, tMode);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        List<Path> paths = altDijkstra.calcRoundTrips(5, maxDist, 2, 1);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6, 5), paths.get(1).calcNodes());
    }

    void checkAlternatives( List<AlternativeRoute.AlternativeInfo> alternativeInfos )
    {
        for (AlternativeRoute.AlternativeInfo a : alternativeInfos)
        {
            if (a.getPlateauWeight() > a.getPath().getWeight())
                assertTrue("plateau or sortby incorrect -> " + a, false);
        }
    }

    @Test
    public void testDisconnectedAreas()
    {
        GraphStorage g = createTestGraph(true);

        // one single disconnected node
        updateDistancesFor(g, 20, 0.00, -0.01);

        Weighting weighting = new FastestWeighting(carFE);
        AltDijkstraBidirectionRef altDijkstra = new AltDijkstraBidirectionRef(g, carFE, weighting, tMode);
        Path path = altDijkstra.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(1, altDijkstra.getVisitedNodes());
    }
}
