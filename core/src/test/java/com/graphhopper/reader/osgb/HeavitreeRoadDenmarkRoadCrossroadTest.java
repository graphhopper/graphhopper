package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.AbstractGraphStorageTester;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.GHPoint;

public class HeavitreeRoadDenmarkRoadCrossroadTest extends AbstractOsItnReaderTest {

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

    private static final String FILENAME = "./src/test/resources/com/graphhopper/reader/os-itn-heavitree-road-denmark-road.xml";

    // private static final String FILENAME =
    // "./src/test/resources/com/graphhopper/reader/os-itn-simple-mandatory-turn-restricted-crossroad.xml";

    @Test
    public void testNoTurnFromNWToSW() throws IOException {
        runNoMotorVehicleTurnFromNWToSWTest(FILENAME);
    }

    @Test
    public void testMandatoryTurnFromSWToNW() throws IOException {
        runMandatoryMotorVehicleTurnFromSWToNWTest(FILENAME);
    }

    private void runMandatoryMotorVehicleTurnFromSWToNWTest(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);

        DefaultEdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);
        carOutExplorer = graph.createEdgeExplorer(carOutFilter);

        GHUtility.printInfo(graph, 0, 20, EdgeFilter.ALL_EDGES);
//        GHUtility.printInfo(graph, 2, 20, EdgeFilter.ALL_EDGES);
//        GHUtility.printInfo(graph, 3, 20, EdgeFilter.ALL_EDGES);

        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);

        int nCenter = AbstractGraphStorageTester.getIdOf(graph, node4Latitude, node4Longitude);
        int nNW = AbstractGraphStorageTester.getIdOf(graph, nodeNWLatitude, nodeNWLongitude);
        int nSW = AbstractGraphStorageTester.getIdOf(graph, nodeSWLatitude, nodeSWLongitude);
        int nNE = AbstractGraphStorageTester.getIdOf(graph, node1Latitude, node1Longitude);
        int nSE = AbstractGraphStorageTester.getIdOf(graph, node0Latitude, node0Longitude);

        int edge1253_NW_CENTER = getEdge(nNW, nCenter);
        int edge1264_SW_CENTER = getEdge(nSW, nCenter);
        int edge6127_NE_CENTER = getEdge(nNE, nCenter);
        int edge6216_SE_CENTER = getEdge(nSE, nCenter);

        TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage) graph).getExtendedStorage();

        // Check that there is no restriction from SW to NW (our Mandatory turn)
        long turnCostFlags = tcStorage.getTurnCostFlags(nCenter, edge1264_SW_CENTER, edge1253_NW_CENTER);
        double cost = carEncoder.getTurnCost(turnCostFlags);
        assertFalse(carEncoder.isTurnRestricted(turnCostFlags));
        assertTrue(cost == 0.0);

        // Check that SW to NE is restricted (high cost)
        turnCostFlags = tcStorage.getTurnCostFlags(nCenter, edge1264_SW_CENTER, edge6127_NE_CENTER);
        cost = carEncoder.getTurnCost(turnCostFlags);
        assertTrue(cost > 0.0);

        // Check that SW to SE is restricted (high cost)
        turnCostFlags = tcStorage.getTurnCostFlags(nCenter, edge1264_SW_CENTER, edge6216_SE_CENTER);
        cost = carEncoder.getTurnCost(turnCostFlags);
        assertTrue(cost > 0.0);

         // Every route from 19 is not restricted
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,         edge19_81_83, edge17_80_81)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,         edge19_81_83, edge18_81_82)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,         edge19_81_83, edge20_81_84)));
////         Every route from 18 is not restricted
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge18_81_82, edge17_80_81)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge18_81_82, edge19_81_83)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge18_81_82, edge20_81_84)));
////         Every route from 20 is not restricted
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge20_81_84, edge17_80_81)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge20_81_84, edge18_81_82)));
//         assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,         edge20_81_84, edge19_81_83)));
    }

    private void runNoMotorVehicleTurnFromNWToSWTest(String filename) throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File(filename);
        readGraphFile(graph, file);

        // EdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false,
        // true);
        EdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, true, false);
        // EdgeFilter carOutFilter = EdgeFilter.ALL_EDGES;
        carOutExplorer = graph.createEdgeExplorer(carOutFilter);

        // GHUtility.printInfo(graph, 0, 20, new DefaultEdgeFilter(carEncoder,
        // true, false));
        GHUtility.printInfo(graph, 0, 20, EdgeFilter.ALL_EDGES);
        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);

        int nCenter = AbstractGraphStorageTester.getIdOf(graph, node4Latitude, node4Longitude);
        int nNW = AbstractGraphStorageTester.getIdOf(graph, nodeNWLatitude, nodeNWLongitude);
        int nSW = AbstractGraphStorageTester.getIdOf(graph, nodeSWLatitude, nodeSWLongitude);
        int nNE = AbstractGraphStorageTester.getIdOf(graph, node1Latitude, node1Longitude);
        int nSE = AbstractGraphStorageTester.getIdOf(graph, node0Latitude, node0Longitude);

        int edge1253_NW_CENTER = getEdge(nNW, nCenter);
        int edge1264_SW_CENTER = getEdge(nSW, nCenter);
        int edge6127_NE_CENTER = getEdge(nNE, nCenter);
        int edge6216_SE_CENTER = getEdge(nSE, nCenter);

        TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage) graph).getExtendedStorage();

        // Check that NW to SW is restricted (high cost)
        long turnCostFlags = tcStorage.getTurnCostFlags(nCenter, edge1253_NW_CENTER, edge1264_SW_CENTER);
        double cost = carEncoder.getTurnCost(turnCostFlags);
        assertTrue(cost > 0.0);

        // We don't care about whether 17 to 20 is restricted (high cost) but it
        // won't be in this example
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,
        // edge17_80_81, edge20_81_84)));

        // We don't care about whether 17 to 18 is restricted (high cost)
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(nCenter,
        // edge17_80_81, edge18_81_82)));

        // Every route from 19 is not restricted
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge19_81_83, edge17_80_81)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge19_81_83, edge18_81_82)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge19_81_83, edge20_81_84)));
        // Every route from 18 is not restricted
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge18_81_82, edge17_80_81)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge18_81_82, edge19_81_83)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge18_81_82, edge20_81_84)));
        // Every route from 20 is not restricted
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge20_81_84, edge17_80_81)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge20_81_84, edge18_81_82)));
        // assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81,
        // edge20_81_84, edge19_81_83)));
    }

    protected void checkSimpleNodeNetwork(GraphHopperStorage graph) {
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("DENMARK ROAD", iter.getName());
        iter.next();
        assertEquals("HEAVITREE ROAD", iter.getName());
        iter.next();
        assertEquals("DENMARK ROAD", iter.getName());
        iter.next();
        assertEquals("HEAVITREE ROAD", iter.getName());
        assertFalse(iter.next());
    }

    //@Test
    public void testActualGraph() {
        String graphLoc = "/home/phopkins/Documents/graphhopper/core/58096-SX9192-2c1";
        String inputFile = "/home/phopkins/Development/geoserver-service-test/geoservertest/itn-sample-data/58096-SX9192-2c1.xml";
         EncodingManager enc = new EncodingManager(new CarFlagEncoder(5, 5, 3));
        GraphHopper graphHopper = new GraphHopper().setInMemory().setGraphHopperLocation(graphLoc).setOSMFile(inputFile).setCHEnable(false).setEncodingManager(enc);
        graphHopper.importOrLoad();
        outputRoute(graphHopper, nodeNWLatitude, nodeNWLongitude, nodeSWLatitude, nodeSWLongitude);

        outputRoute(graphHopper, 50.723729, -3.51897, 50.723975, -3.518228);

        outputRoute(graphHopper, 50.727204, -3.523927, 50.726631, -3.524159);
    }

    private void outputRoute(GraphHopper graphHopper, double lat1, double lon1, double lat2, double lon2) {
        GHPoint start = new GHPoint(lat1, lon1);
        GHPoint end = new GHPoint(lat2, lon2);
        System.out.println("Route from " + start + " to " + end);
        GHRequest ghRequest = new GHRequest(start, end);

        GHResponse ghResponse = graphHopper.route(ghRequest);
        System.err.println("ghResponse.getPoints() " + ghResponse.getPoints());
        System.err.println("ghResponse.getDebugInfo() " + ghResponse.getDebugInfo());
    }

    @Test
    public void testIngest() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("/home/phopkins/Development/OSMMITN/data");
//        File file = new File("/home/phopkins/Development/geoserver-service-test/geoservertest/itn-sample-data/58096-SX9192-2c1.xml");
        readGraphFile(graph, file);
    }

}
