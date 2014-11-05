/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.RelationCarFlagEncoder;
import com.graphhopper.storage.AbstractGraphStorageTester;
import com.graphhopper.storage.ExtendedStorage;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * 
 * @author Peter Karich
 * @author Stuart Adam
 */
public class OsItnReaderTest {
	
	private static final Logger logger = LoggerFactory
			.getLogger(OsItnReaderTest.class);
	private static final InputStream COMPLEX_ITN_EXAMPLE = OsItnReader.class
			.getResourceAsStream("os-itn-sample.xml");
	private EncodingManager encodingManager;// = new EncodingManager("CAR");//"car:com.graphhopper.routing.util.RelationCarFlagEncoder");
	private RelationCarFlagEncoder carEncoder;// = (RelationCarFlagEncoder) encodingManager
			//.getEncoder("CAR");
	private EdgeFilter carOutEdges;// = new DefaultEdgeFilter(
			//carEncoder, false, true);


	private double node0Lat = 50.69919585809061d;
	private double node0Lon = -3.558952191414606d;

	private double node1Lat = 50.6972183871447d;
	private double node1Lon = -3.7004874033793294d;

	private double node2Lat = 50.695067824972014d;
	private double node2Lon = -3.8420070965997613d;

	private double node3Lat = 50.652274361836156d;
	private double node3Lon = -3.698863205562225d;

	private double node4Lat = 50.74216177664155d;
	private double node4Lon = -3.702115749998877d;
	private boolean turnCosts = true;
	private EdgeExplorer carOutExplorer;
	private EdgeExplorer carAllExplorer;
	private BikeFlagEncoder bikeEncoder;
	private FootFlagEncoder footEncoder;
	
	@Before
	public void initEncoding() {
		if (turnCosts)
        {
            carEncoder = new RelationCarFlagEncoder(5, 5, 3);
            bikeEncoder = new BikeFlagEncoder(4, 2, 3);
        } else
        {
            carEncoder = new RelationCarFlagEncoder();
            bikeEncoder = new BikeFlagEncoder();
        }

        footEncoder = new FootFlagEncoder();
        carOutEdges = new DefaultEdgeFilter(carEncoder, false, true);
        encodingManager = new EncodingManager(footEncoder, carEncoder, bikeEncoder);
    
	}

    @Test
    public void testReadItnNoEntryMultipointCrossroad() throws IOException {
        boolean turnRestrictionsImport = true;
        boolean is3D = false;
        GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-no-entry-multipoint-crossroad.xml");
//        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-crossroads.xml");
//        File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry.xml");
        OsItnReader osItnReader = readGraphFile(graph, file);

        logger.info("We have " + graph.getNodes() + " nodes");
        // Is 7 correct?
//        assertEquals(7, graph.getNodes());

        
        logger.info("80 " + osItnReader.getNodeMap().get(4000000025277880l));
        logger.info("81 " + osItnReader.getNodeMap().get(4000000025277881l));
        logger.info("82 " + osItnReader.getNodeMap().get(4000000025277882l));
        logger.info("83 " + osItnReader.getNodeMap().get(4000000025277883l));
        logger.info("84 " + osItnReader.getNodeMap().get(4000000025277884l));
        logger.info("85 " + osItnReader.getNodeMap().get(4000000025277885l));
        logger.info("86 " + osItnReader.getNodeMap().get(4000000025277886l));
        
        boolean direction = true;
        // The asserts below work out whether we can visit the nodes from a certain point
        
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        logger.info("Node 0 " + count(explorer.setBaseNode(0)));
        logger.info("Node 1 " + count(explorer.setBaseNode(1)));
        logger.info("Node 2 " + count(explorer.setBaseNode(2)));
        logger.info("Node 3 " + count(explorer.setBaseNode(3)));
        logger.info("Node 4 " + count(explorer.setBaseNode(4)));
        logger.info("Node 5 " + count(explorer.setBaseNode(5)));
        logger.info("Node 6 " + count(explorer.setBaseNode(6)));
        logger.info("Node 7 " + count(explorer.setBaseNode(7)));
        logger.info("Node 8 " + count(explorer.setBaseNode(8)));
        
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(2, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
        assertEquals(2, count(explorer.setBaseNode(5)));
        assertEquals(1, count(explorer.setBaseNode(6)));
        assertEquals(1, count(explorer.setBaseNode(7)));
        assertEquals(1, count(explorer.setBaseNode(8)));
        
//        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));

        EdgeIterator iter = explorer.setBaseNode(0);
        while (iter.next()) {
            logger.info("0 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(1);
        while (iter.next()) {
            logger.info("1 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(2);
        while (iter.next()) {
            logger.info("2 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(3);
        while (iter.next()) {
            logger.info("3 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(4);
        while (iter.next()) {
            logger.info("4 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(5);
        while (iter.next()) {
            logger.info("5 Adj node is " + iter.getAdjNode());            
        }
        iter = explorer.setBaseNode(6);
        while (iter.next()) {
            logger.info("6 Adj node is " + iter.getAdjNode());            
        }
        
        // Assert that this is true
        iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(8, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(7, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(6, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        assertEquals(5, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertFalse(iter.next());
        
        iter = explorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(5, iter.getAdjNode());
        assertFalse(iter.next());
        
        iter = explorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertFalse(iter.next());
        
        iter = explorer.setBaseNode(4);
        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertFalse(iter.next());
        
        iter = explorer.setBaseNode(5);
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertFalse(iter.next());
        
        iter = explorer.setBaseNode(6);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());
        
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
        
//        iter = explorer.setBaseNode(1);
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
//        assertTrue(iter.next());
//        logger.info("0 Adj node is " + iter.getAdjNode());
        /*
        evaluateRouting(iter, 6, direction ? false : true, direction ? true : false, false);
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 2, true, true, false);
        evaluateRouting(iter, 1, true, true, true);

        
//        EdgeIterator iter = explorer.setBaseNode(0);
        
        assertEquals(direction ? 3 : 4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(direction ? 1 : 0, count(explorer.setBaseNode(4)));
        */
        // The Method below checks that we can actually travel/go through the nodes
        // above - I will also write the code later on Friday.
//        checkOneWay(graph, true);
    }
	@Test
	public void testReadSimpleCrossRoads() throws IOException {
		boolean turnRestrictionsImport = false;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-crossroad.xml");
		readGraphFile(graph, file);
		assertEquals(5, graph.getNodes());
		checkSimpleNodeNetwork(graph);
	}
	
	@Test
	public void testReadSimpleMultiPointCrossRoads() throws IOException {
		boolean turnRestrictionsImport = false;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-multipoint-crossroad.xml");
		readGraphFile(graph, file);
		assertEquals(6, graph.getNodes());
		checkMultiNodeNetwork(graph);
	}

	@Test
	public void testReadSimpleCrossRoadsWithTurnRestriction()
			throws IOException {
		boolean turnRestrictionsImport = true;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-restricted-crossroad.xml");
		readGraphFile(graph, file);
		assertEquals(5, graph.getNodes());
		checkSimpleNodeNetwork(graph);
		
		
		carOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));
        
		int n0 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
		int n1 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
		int n2 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
		int n3 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
		int n4 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

		int edge0_1 = getEdge(n1, n0);
		int edge1_2 = getEdge(n1, n2);
		int edge1_3 = getEdge(n1, n3);
		int edge1_4 = getEdge(n1, n4);
		
		TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage)graph).getExtendedStorage();
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge0_1, edge1_2)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_2, edge0_1)));
		
		assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge0_1, edge1_3)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_3, edge0_1)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge0_1, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_4, edge0_1)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_2, edge1_3)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_3, edge1_2)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_2, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_4, edge1_2)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_3, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n1, edge1_4, edge1_3)));
		
	}
	
	@Test
	public void testReadSimpleBridge()
			throws IOException {
		boolean turnRestrictionsImport = true;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-bridge.xml");
		readGraphFile(graph, file);
		assertEquals(7, graph.getNodes());
		checkBridgeNodeNetwork(graph);
	}
	
	@Test
	public void testReadSimpleOneWay()
			throws IOException {
		boolean turnRestrictionsImport = true;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-oneway.xml");
		readGraphFile(graph, file);
		
		assertEquals(5, graph.getNodes());
		checkSimpleOneWayNetwork(graph, true);
		checkOneWay(graph, true);
	}
	
	@Test
	public void testReadSimpleOppositeDirectionOneWay()
			throws IOException {
		boolean turnRestrictionsImport = true;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-oneway-2.xml");
		readGraphFile(graph, file);
		
		assertEquals(5, graph.getNodes());
		checkSimpleOneWayNetwork(graph, false);
		checkOneWay(graph, false);
	}

	private void checkOneWay(GraphHopperStorage graph, boolean direction) {
		System.err.println(carEncoder.getClass());
		carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
		EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
		evaluateRouting(iter, 4, direction?false:true, direction?true:false, false);
        evaluateRouting(iter, 3, true, true, false);
        evaluateRouting(iter, 2, true, true, false);
        evaluateRouting(iter, 1, true, true, true);
	}

	private void evaluateRouting(EdgeIterator iter, int node, boolean forward,
			boolean backward, boolean finished) {
		assertEquals(node, iter.getAdjNode());
        assertEquals(forward, carEncoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertEquals(backward, carEncoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));
        assertEquals(!finished, iter.next());
	}

	private OsItnReader readGraphFile(GraphHopperStorage graph, File file)
			throws IOException {
		OsItnReader osItnReader = new OsItnReader(graph);
		osItnReader.setOSMFile(file);
		osItnReader.setEncodingManager(encodingManager);
		osItnReader.readGraph();
		return osItnReader;
	}

	private GraphHopperStorage configureStorage(boolean turnRestrictionsImport,
			boolean is3D) {
		String directory = "/tmp";
		ExtendedStorage extendedStorage = turnRestrictionsImport ? new TurnCostStorage()
				: new ExtendedStorage.NoExtendedStorage();
		GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(
				directory, false), encodingManager, is3D, extendedStorage);
		return graph;
	}

	private int getEdge(int from, int to) {
		EdgeIterator iter = carOutExplorer.setBaseNode(from);
		while (iter.next()) {
			if (iter.getAdjNode() == to) {
				return iter.getEdge();
			}
		}
		return EdgeIterator.NO_EDGE;
	}

	private void checkSimpleNodeNetwork(GraphHopperStorage graph) {
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
	}
	
	private void checkSimpleOneWayNetwork(GraphHopperStorage graph, boolean direction) {
		EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
		assertEquals(direction?3:4, count(explorer.setBaseNode(0)));
		assertEquals(1, count(explorer.setBaseNode(1)));
		assertEquals(1, count(explorer.setBaseNode(2)));
		assertEquals(1, count(explorer.setBaseNode(3)));
		assertEquals(direction?1:0, count(explorer.setBaseNode(4)));
	}
	
	private void checkMultiNodeNetwork(GraphHopperStorage graph) {
		EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
		assertEquals(4, count(explorer.setBaseNode(0)));
		assertEquals(2, count(explorer.setBaseNode(1)));
		assertEquals(1, count(explorer.setBaseNode(2)));
		assertEquals(1, count(explorer.setBaseNode(3)));
		assertEquals(1, count(explorer.setBaseNode(4)));
		assertEquals(1, count(explorer.setBaseNode(5)));
		
		EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("OTHER ROAD (A337)", iter.getName());
        assertEquals(55, carEncoder.getSpeed(iter.getFlags()), 1e-1);
        iter.next();
        assertEquals("OTHER ROAD (A337)", iter.getName());
        assertEquals(55, carEncoder.getSpeed(iter.getFlags()), 1e-1);
        iter.next();
        assertEquals("BONHAY ROAD (A337)", iter.getName());
        assertEquals(55, carEncoder.getSpeed(iter.getFlags()), 1e-1);
        iter.next();
        assertEquals("BONHAY ROAD (A337)", iter.getName());
        long flags = iter.getFlags();
        assertEquals(55.0, carEncoder.getSpeed(flags), 1e-1);
        assertFalse(iter.next());
	}
	
	private void checkBridgeNodeNetwork(GraphHopperStorage graph) {
		EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
		assertEquals(2, count(explorer.setBaseNode(0)));
		assertEquals(2, count(explorer.setBaseNode(1)));
		assertEquals(2, count(explorer.setBaseNode(2)));
		assertEquals(2, count(explorer.setBaseNode(3)));
		assertEquals(2, count(explorer.setBaseNode(4)));
		assertEquals(1, count(explorer.setBaseNode(5)));
		assertEquals(1, count(explorer.setBaseNode(6)));
	}

	
	//@Test
	public void testReadSample() throws IOException {
		boolean turnRestrictionsImport = false;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		OsItnReader osItnReader = new OsItnReader(graph);
		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-sample.xml");
		osItnReader.setOSMFile(file);
		osItnReader.setEncodingManager(new EncodingManager("CAR,FOOT"));
		osItnReader.readGraph();
		assertEquals(760, graph.getNodes());
	}
}
