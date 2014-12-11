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

public class OneWayTest extends AbstractOsItnReaderTest{


    @Test
    public void testReadSimpleOneWay() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-oneway.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkSimpleOneWayNetwork(graph, true);
        checkOneWay(graph, true);
    }

    @Test
    public void testReadSimpleOppositeDirectionOneWay() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-oneway-2.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkSimpleOneWayNetwork(graph, false);
        checkOneWay(graph, false);
    }
    
    /**
     * One Way for everything EXCEPT buses. One way should be present.
     * @throws IOException
     */
    @Test
    public void testOneWayExceptBusesTrue() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-oneway-except-for-buses-true.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkSimpleOneWayNetwork(graph, true);
        checkOneWay(graph, true);
    }
    
    //BusTrue, MotorVehicle False
    
    /**
     * One Way for buses but nothing else. No one way should be present.
     * @throws IOException
     */
    @Test
    public void testOneWayExceptBusesFalse() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-oneway-except-for-buses-false.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkForNoOneWayInNetwork(graph);
        
        //checkOneWay(graph, false);
    }
    /**
     * One Way for everything EXCEPT Motor Vehicles. No one way should be present.
     * @throws IOException
     */
    @Test
    public void testOneWayExceptMotorVehiclesTrue() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-oneway-except-for-motor-vehicles-true.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkForNoOneWayInNetwork(graph);
    }
    /**
     * One Way for Motor Vehicles but nothing else. One way should be present.
     * @throws IOException
     */
    @Test
    public void testOneWayExceptMotorVehiclesFalse() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-oneway-except-for-motor-vehicles-false.xml");
        readGraphFile(graph, file);

        assertEquals(5, graph.getNodes());
        checkSimpleOneWayNetwork(graph, true);
        checkOneWay(graph, true);
    }
    
    
    private void checkOneWay(GraphHopperStorage graph, boolean direction) {
        System.err.println(carEncoder.getClass());
        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 4, direction ? false : true, direction ? true : false, false);
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 2, true, true, false);
        evaluateRouting(iter, 1, true, true, true);
    }
   private void checkSimpleOneWayNetwork(GraphHopperStorage graph, boolean direction) {
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(direction ? 3 : 4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(direction ? 1 : 0, count(explorer.setBaseNode(4)));
    }
   private void checkForNoOneWayInNetwork(GraphHopperStorage graph) {
       EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
       assertEquals(4, count(explorer.setBaseNode(0)));
       assertEquals(1, count(explorer.setBaseNode(1)));
       assertEquals(1, count(explorer.setBaseNode(2)));
       assertEquals(1, count(explorer.setBaseNode(3)));
       assertEquals(1, count(explorer.setBaseNode(4)));
   }

}
