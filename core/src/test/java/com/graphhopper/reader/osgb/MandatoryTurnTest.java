package com.graphhopper.reader.osgb;

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
import com.graphhopper.util.GHUtility;

public class MandatoryTurnTest extends AbstractOsItnReaderTest{

    @Test
    public void testReadSimpleCrossRoadsWithMandatoryTurnRestrictionFrom17To19() throws IOException {
        runMandatoryMotorVehicleTurnFrom17To19Test("./src/test/resources/com/graphhopper/reader/os-itn-simple-mandatory-turn-restricted-crossroad.xml");
    }
    
    @Test
    public void testMandatoryTurnExceptBusTrueFrom17To19() throws IOException {
        runMandatoryMotorVehicleTurnFrom17To19Test("./src/test/resources/com/graphhopper/reader/os-itn-mandatory-turn-except-for-buses-true-crossroad.xml");
    }
    @Test
    public void testMandatoryTurnExceptBusFalseFrom17To19() throws IOException {
        runNonMandatoryMotorVehicleTurnFrom17To19Test("./src/test/resources/com/graphhopper/reader/os-itn-mandatory-turn-except-for-buses-false-crossroad.xml");
    }
    @Test
    public void testMandatoryTurnExceptMotorVehicleTrueFrom17To19() throws IOException {
        runNonMandatoryMotorVehicleTurnFrom17To19Test("./src/test/resources/com/graphhopper/reader/os-itn-mandatory-turn-except-for-motor-vehicles-true-crossroad.xml");
    }
    /**
     * Mandatory turn for Motor Vehicles
     * @throws IOException
     */
    @Test
    public void testMandatoryTurnExceptMotorVehicleFalseFrom17To19() throws IOException {
        runMandatoryMotorVehicleTurnFrom17To19Test("./src/test/resources/com/graphhopper/reader/os-itn-mandatory-turn-except-for-motor-vehicles-false-crossroad.xml");
    }
    
    private void runMandatoryMotorVehicleTurnFrom17To19Test(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);

        DefaultEdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
                carOutExplorer = graph.createEdgeExplorer(carOutFilter);

        GHUtility.printInfo(graph, 0, 20, carOutFilter);
        int n80 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
        int n81 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
        int n82 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
        int n83 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
        int n84 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

        int edge17_80_81 = getEdge(n81, n80);
        int edge18_81_82 = getEdge(n81, n82);
        int edge19_81_83 = getEdge(n81, n83);
        int edge20_81_84 = getEdge(n81, n84);

        TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage) graph).getExtendedStorage();

        // Check that there is no restriction from 17 to 19 (our Mandatory turn)
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge19_81_83)));

        // Check that 17 to 20 is restricted (high cost)
        long turnCostFlags = tcStorage.getTurnCostFlags(n81, edge17_80_81, edge20_81_84);
        double cost = carEncoder.getTurnCost(turnCostFlags);
        assertTrue(cost > 0.0);
        
        // Check that 17 to 18 is restricted (high cost)
        turnCostFlags = tcStorage.getTurnCostFlags(n81, edge17_80_81, edge18_81_82);
        cost = carEncoder.getTurnCost(turnCostFlags);
        assertTrue(cost > 0.0);


        // Every route from 19 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge18_81_82)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge20_81_84)));
        // Every route from 18 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge19_81_83)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge20_81_84)));
        // Every route from 20 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge18_81_82)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge19_81_83)));
    }
    
    private void runNonMandatoryMotorVehicleTurnFrom17To19Test(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);

        DefaultEdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
                carOutExplorer = graph.createEdgeExplorer(carOutFilter);

        GHUtility.printInfo(graph, 0, 20, carOutFilter);
        int n80 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
        int n81 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
        int n82 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
        int n83 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
        int n84 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

        int edge17_80_81 = getEdge(n81, n80);
        int edge18_81_82 = getEdge(n81, n82);
        int edge19_81_83 = getEdge(n81, n83);
        int edge20_81_84 = getEdge(n81, n84);

        TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage) graph).getExtendedStorage();

        // Check that there is no restriction from 17 to 19 (our Mandatory turn)
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge19_81_83)));

        // Check that 17 to 20 is NOT restricted
        long turnCostFlags = tcStorage.getTurnCostFlags(n81, edge17_80_81, edge20_81_84);
        assertFalse(carEncoder.isTurnRestricted(turnCostFlags));
        
        // Check that 17 to 18 is NOT restricted
        turnCostFlags = tcStorage.getTurnCostFlags(n81, edge17_80_81, edge18_81_82);
        assertFalse(carEncoder.isTurnRestricted(turnCostFlags));


        // Every route from 19 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge18_81_82)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge20_81_84)));
        // Every route from 18 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge19_81_83)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge20_81_84)));
        // Every route from 20 is not restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge17_80_81)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge18_81_82)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge19_81_83)));
    }
    
}
