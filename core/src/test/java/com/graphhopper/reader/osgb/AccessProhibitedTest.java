package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.AbstractGraphStorageTester;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class AccessProhibitedTest extends AbstractOsItnReaderTest{
    
    /**
     * Access Prohibited for everything except Buses (So motor vehicles can NOT access)
     * @throws IOException
     */
    @Test
    public void testAccessProhibitedExceptBusTrueFrom17To19() throws IOException {
        runAccessProhibitedToNode19Test("./src/test/resources/com/graphhopper/reader/os-itn-access-prohibited-except-for-buses-true-crossroad.xml");
    }
    /**
     * Access Prohibited for Buses and nothing else (So motor vehicles can access)
     * @throws IOException
     */
    @Test
    public void testAccessProhibitedExceptBusFalseFrom17To19() throws IOException {
        runNonAccessProhibitedToNode19Test("./src/test/resources/com/graphhopper/reader/os-itn-access-prohibited-except-for-buses-false-crossroad.xml");
    }
    /**
     * Access Prohibited except for Motor Vehicles (So motor vehicles can access)
     * @throws IOException
     */
    @Test
    public void testAccessProhibitedExceptMotorVehicleTrueFrom17To19() throws IOException {
        runNonAccessProhibitedToNode19Test("./src/test/resources/com/graphhopper/reader/os-itn-access-prohibited-except-for-motor-vehicles-true-crossroad.xml");
    }
    /**
     * Access Prohibited for Motor Vehicles and nothing else (So motor vehicles can NOT access)
     * @throws IOException
     */
    @Test
    public void testAccessProhibitedExceptMotorVehicleFalseFrom17To19() throws IOException {
        runAccessProhibitedToNode19Test("./src/test/resources/com/graphhopper/reader/os-itn-access-prohibited-except-for-motor-vehicles-false-crossroad.xml");
    }
    
    private void checkAccessProhibitedNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 4 nodes. We have lost one because of our prohibited route 
        assertEquals(4, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
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
        assertEquals(1, count(explorer.setBaseNode(3)));

        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 2, true, true, false);
        evaluateRouting(iter, 1, true, true, true);

        // Starting at node 1
        iter = carAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);
    }
    private void checkNonAccessProhibitedNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 4 nodes. We have lost one because of our prohibited route 
        assertEquals(5, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
        // the base node;
        assertEquals(4, count(explorer.setBaseNode(0)));

        GHUtility.printInfo(graph, 0, 20, carOutEdges);
        // Assert that when the explorer is on node 1 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(1)));

        // Assert that when the explorer is on node 2 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(2)));

        // Assert that when the explorer is on node 3 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is on node 4 it can travel one edges
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
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 2
        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 3
        iter = carAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);

        // Starting at node 4
        iter = carAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        // I should not be able to travel to node 0 in both directions
        evaluateRouting(iter, 0, true, true, true);
    }

    private void runAccessProhibitedToNode19Test(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);
        assertEquals(4, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(3, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("OTHER ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        assertFalse(iter.next());

        DefaultEdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
        carOutExplorer = graph.createEdgeExplorer(carOutFilter);
        
        checkAccessProhibitedNetwork(graph, carOutExplorer);
        
    }
    
    private void runNonAccessProhibitedToNode19Test(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("OTHER ROAD", iter.getName());
        iter.next();
        assertEquals("OTHER ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        assertFalse(iter.next());

        DefaultEdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
        carOutExplorer = graph.createEdgeExplorer(carOutFilter);
        
        checkNonAccessProhibitedNetwork(graph, carOutExplorer);
    }    
}
