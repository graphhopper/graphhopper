package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class BampfyldeStreetCheekeStreetNoEntryTest extends AbstractOsItnReaderTest {
    
    // 6216 292811.000,92750.000 SE
    double node0Longitude = -3.51994881876;
    double node0Latitude = 50.7244536431;

    // 6127 292764.000,92812.000 NE
    double node1Longitude = -3.52063248078;
    double node1Latitude = 50.7250023098;

    // 1253 292666.000,92818.000 NW
    double nodeNWLongitude = -3.52202212301;
    double nodeNWLatitude = 50.7250381316;

    // 1264 292734.428,92739.063 SW
    double nodeSWLongitude = -3.5210300491;
    double nodeSWLatitude = 50.7243411788;

    // 9154 292749.000,92779.000 CENTER
    double node4Longitude = -3.52083530674;
    double node4Latitude = 50.724702885;

    
    @Test
    public void testNoEntryExceptForBusesTrue() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-bampfylde-street-cheeke-street.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNoEntryNetwork(graph, explorer);
    }
    
    protected void checkNoEntryNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 7 nodes
        assertEquals(7, graph.getNodes());

        // Assert that there are three links/roads/edges that can be seen from
        // the base node;
        assertEquals(3, count(explorer.setBaseNode(0)));

        GHUtility.printInfo(graph, 0, 20, carOutEdges);
        // Assert that when the explorer is on node 1 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(1)));

        // Assert that when the explorer is positioned on base 2 it can only
        // travel one edge
        assertEquals(1, count(explorer.setBaseNode(2)));

        // Assert that when the explorer is positioned on node 3 it can
        // travel 2 edges
        assertEquals(2, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(2, count(explorer.setBaseNode(4)));

        // Assert that when the explorer is positioned on node 5 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(5)));

        // Assert that when the explorer is positioned on node 6 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(6)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 6, true, true, false);
        evaluateRouting(iter, 5, true, true, false);
        evaluateRouting(iter, 4, false, true, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 0 in a forward and backward direction
        // and have exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel back from node 3 but not to it
        // I should be able to travel to and from node 1 and have exhausted all
        // the edges
        evaluateRouting(iter, 3, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should not be able to travel to node 1 in a forward direction but
        // in backward direction
        evaluateRouting(iter, 4, true, true, false);
        // I should be able to travel to node 2 in forward direction but not
        // backward and have exhausted all the edges
        evaluateRouting(iter, 2, true, true, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, false, false);
        evaluateRouting(iter, 3, true, true, true);

        // Given Node 5
        iter = carAllExplorer.setBaseNode(5);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

        // Given Node 6
        iter = carAllExplorer.setBaseNode(6);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

    }
    protected void checkNonNoEntryNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 7 nodes
        assertEquals(5, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
        // the base node;
        assertEquals(4, count(explorer.setBaseNode(0)));

        GHUtility.printInfo(graph, 0, 20, carOutEdges);
        // Assert that when the explorer is on node 1 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(1)));

        // Assert that when the explorer is positioned on base 2 it can only
        // travel one edge
        assertEquals(1, count(explorer.setBaseNode(2)));

        // Assert that when the explorer is positioned on node 3 it can
        // travel 1 edges
        assertEquals(1, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(4)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 4, true, true, false);
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 2, true, true, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 0 in a forward and backward direction
        // and have exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel back from node 3 but not to it
        // I should be able to travel to and from node 1 and have exhausted all
        // the edges
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        evaluateRouting(iter, 0, true, true, true);
    }

}
