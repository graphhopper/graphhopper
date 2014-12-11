package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class NoEntryTest extends AbstractOsItnReaderTest {
    
    private static final Logger logger = LoggerFactory.getLogger(NoEntryTest.class);
    
    @Test
    public void testReadNoEntryOneDirection() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-onedirection.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        // Assert that our graph has 7 nodes
        assertEquals(7, graph.getNodes());

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
        // travel 2 edges
        assertEquals(2, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(4)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 6, true, true, false);
        evaluateRouting(iter, 5, true, true, false);
        evaluateRouting(iter, 4, true, true, false);
        evaluateRouting(iter, 3, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 0 in a forward and backward direction
        // and have exhausted all the edges
        evaluateRouting(iter, 2, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel back from node 3 but not to it
        // I should be able to travel to and from node 1 and have exhausted all
        // the edges
        evaluateRouting(iter, 3, false, true, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should not be able to travel to node 1 in a forward direction but
        // in backward direction
        evaluateRouting(iter, 0, true, true, false);
        // I should be able to travel to node 2 in forward direction but not
        // backward and have exhausted all the edges
        evaluateRouting(iter, 2, true, false, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

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

    @Test
    public void testReadNoEntryOppositeDirection() throws IOException {

        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-oppositedirection.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer outExplorer = graph.createEdgeExplorer(carOutEdges);
        GHUtility.printInfo(graph, 0, 20, carOutEdges);

        printNodes(outExplorer, 7);
        // ***********************************************************************

        // Assert that our graph has 7 nodes
        assertEquals(7, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
        // the base node;
        assertEquals(4, count(outExplorer.setBaseNode(0)));

        // Assert that when the explorer is on node 1 it can only travel one
        // edge
        assertEquals(1, count(outExplorer.setBaseNode(1)));

        // Assert that when the explorer is positioned on base 2 it can
        // travel two edges
        assertEquals(2, count(outExplorer.setBaseNode(2)));

        // Assert that when the explorer is positioned on node 3 it can only
        // travel 1 edge
        assertEquals(1, count(outExplorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(1, count(outExplorer.setBaseNode(4)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 6, true, true, false);
        evaluateRouting(iter, 5, true, true, false);
        evaluateRouting(iter, 4, true, true, false);
        evaluateRouting(iter, 3, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 2 forwards and backwards and have
        // exhausted all the edges as the way linking to node 3 is a one way
        // and should have have exhausted all the edges
        evaluateRouting(iter, 2, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel to node 3 forward but back is blocked by
        // no entry
        // I should be able to get back and forth to 1 and have exhausted edges
        evaluateRouting(iter, 3, true, false, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());

        // I should be able to get back to node 2 and forward onto node 0.
        evaluateRouting(iter, 0, true, true, false);
        evaluateRouting(iter, 2, false, true, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

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

    @Test
    public void testReadNoEntryMultipointCrossroadStartNeg() throws IOException {

        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-no-entry-multipoint-crossroad-start_neg.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer outExplorer = graph.createEdgeExplorer(carOutEdges);
        final EdgeExplorer inExplorer = graph.createEdgeExplorer(carInEdges);

        printNodes(outExplorer, 9);
        // ***********************************************************************
        // Assert that our graph has 7 nodes
        assertEquals(7, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
        // the base node;
        assertEquals(4, count(outExplorer.setBaseNode(0)));

        // Assert that when the explorer is on node 1 it cannot travel past the
        // no entry
        // edges
        assertEquals(0, count(outExplorer.setBaseNode(1)));

        // Assert that when the explorer is positioned on base 2 it can only
        // travel two edges
        assertEquals(2, count(outExplorer.setBaseNode(2)));

        // Assert that when the explorer is positioned on node 3 it can only
        // travel two edges
        assertEquals(2, count(outExplorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge in
        // Here we have to use a CarIn Explorer because its a one way simulating
        // a no entry
        assertEquals(1, count(inExplorer.setBaseNode(4)));

        // Assert that when the explorer is positioned on node 5 it can only
        // travel 1 edge out
        assertEquals(1, count(outExplorer.setBaseNode(5)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 6, true, true, false);
        evaluateRouting(iter, 5, true, true, false);
        evaluateRouting(iter, 4, true, true, false);
        evaluateRouting(iter, 3, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should not be able to get to node 2 but node 2 should be able to
        // get back to 0
        evaluateRouting(iter, 2, false, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel to node 3 forth and back and exhausted all
        // the edges
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 1, true, false, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should be able to travel to node 1 in a forward direction but not
        // be able to come back in a backward direction to node 3
        evaluateRouting(iter, 0, true, true, false);
        // I should be able to travel to node 2 in both a forward direction and
        // backward direction and have exhausted all the edges
        evaluateRouting(iter, 2, true, true, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should be able to travel to node 0 and back and that is all the
        // available edges
        evaluateRouting(iter, 0, true, true, true);

        // Starting at Node 5
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
    
    @Test
    public void testNoEntryExceptForBusesTrue() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-except-for-buses-true.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNoEntryNetwork(graph, explorer);
    }
   // @Test
    public void testLakeRoadSpicerStreet() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-lake-road-spicer-street.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNoEntryNetwork(graph, explorer);
    }
    @Test
    public void testNoEntryExceptForBusesFalse() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-except-for-buses-false.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNonNoEntryNetwork(graph, explorer);
    }
    @Test
    public void testNoEntryExceptForMotorVehiclesTrue() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-except-for-motor-vehicles-true.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNonNoEntryNetwork(graph, explorer);
    }
    @Test
    public void testNoEntryExceptForMotorVehiclesFalse() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-except-for-motor-vehicles-false.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************
        checkNoEntryNetwork(graph, explorer);
    }
    
    
    private void printNodes(EdgeExplorer outExplorer, int numNodes) {
        for (int i = 0; i < numNodes; i++) {
//            logger.info("Node " + i + " " + count(outExplorer.setBaseNode(i)));
            System.out.println("Node " + i + " " + count(outExplorer.setBaseNode(i)));
        }

        EdgeIterator iter = null; 
        for (int i = 0; i < numNodes; i++) {
            iter = outExplorer.setBaseNode(i);
            while (iter.next()) {
//                logger.info(i+" Adj node is " + iter.getAdjNode());
                System.out.println(i+" Adj node is " + iter.getAdjNode());
            }
        }
    }
    private void checkNoEntryNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 7 nodes
        assertEquals(7, graph.getNodes());

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
        // travel 2 edges
        assertEquals(2, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(4)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 6, true, true, false);
        evaluateRouting(iter, 5, true, true, false);
        evaluateRouting(iter, 4, true, true, false);
        evaluateRouting(iter, 3, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 0 in a forward and backward direction
        // and have exhausted all the edges
        evaluateRouting(iter, 2, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel back from node 3 but not to it
        // I should be able to travel to and from node 1 and have exhausted all
        // the edges
        evaluateRouting(iter, 3, false, true, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should not be able to travel to node 1 in a forward direction but
        // in backward direction
        evaluateRouting(iter, 0, true, true, false);
        // I should be able to travel to node 2 in forward direction but not
        // backward and have exhausted all the edges
        evaluateRouting(iter, 2, true, false, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should be able to travel to node 0 back and forth and have
        // exhausted all the edges
        evaluateRouting(iter, 0, true, true, true);

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
    private void checkNonNoEntryNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
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
