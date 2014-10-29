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
        
		int n80 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
		int n81 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
		int n82 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
		int n83 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
		int n84 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

		int edge17_80_81 = getEdge(n81, n80);
		int edge18_81_82 = getEdge(n81, n82);
		int edge19_81_83 = getEdge(n81, n83);
		int edge20_81_84 = getEdge(n81, n84);
		
		TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage)graph).getExtendedStorage();
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge18_81_82)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge17_80_81)));
		
		long turnCostFlags = tcStorage.getTurnCostFlags(n81, edge17_80_81, edge19_81_83);
		double cost = carEncoder.getTurnCost(turnCostFlags);
		assertTrue( cost > 0.0 );
		//assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge19_81_83)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge17_80_81)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge20_81_84)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge17_80_81)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge19_81_83)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge18_81_82)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge20_81_84)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge18_81_82)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge20_81_84)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge19_81_83)));
		
	}
	@Test
	public void testReadSimpleCrossRoadsWithMandatoryTurnRestriction()
			throws IOException {
		boolean turnRestrictionsImport = true;
		boolean is3D = false;
		GraphHopperStorage graph = configureStorage(turnRestrictionsImport,
				is3D);

		File file = new File(
				"./src/test/resources/com/graphhopper/reader/os-itn-simple-mandatory-turn-restricted-crossroad.xml");
		readGraphFile(graph, file);
		assertEquals(5, graph.getNodes());
		checkSimpleNodeNetwork(graph);
		
		
		carOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));
        
		int n80 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
		int n81 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
		int n82 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
		int n83 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
		int n84 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

		int edge17_80_81 = getEdge(n81, n80);
		int edge18_81_82 = getEdge(n81, n82);
		int edge19_81_83 = getEdge(n81, n83);
		int edge20_81_84 = getEdge(n81, n84);
		
		TurnCostStorage tcStorage = (TurnCostStorage) ((GraphHopperStorage)graph).getExtendedStorage();

		// Check that 19 to 20 is restricted (high cost)
		long turnCostFlags = tcStorage.getTurnCostFlags(n81, edge19_81_83, edge20_81_84);
		double cost = carEncoder.getTurnCost(turnCostFlags);
		assertTrue( cost > 0.0 );

		// Check that 19 to 18 is restricted (high cost)
		turnCostFlags = tcStorage.getTurnCostFlags(n81, edge19_81_83, edge18_81_82);
		cost = carEncoder.getTurnCost(turnCostFlags);
		assertTrue( cost > 0.0 );
		
		// Check that there is no restriction from 19 to 17 (our Mandatory turn)
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge19_81_83, edge17_80_81)));
		
		// Every route from 17 is not restricted
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge18_81_82)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge19_81_83)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge17_80_81, edge20_81_84)));
		// Every route from 18 is not restricted
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge17_80_81)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge19_81_83)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge18_81_82, edge20_81_84)));
		// Every route from 20 is not restricted
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge17_80_81)));		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge18_81_82)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n81, edge20_81_84, edge19_81_83)));
		
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

	private void readGraphFile(GraphHopperStorage graph, File file)
			throws IOException {
		OsItnReader osItnReader = new OsItnReader(graph);
		osItnReader.setOSMFile(file);
		osItnReader.setEncodingManager(encodingManager);
		osItnReader.readGraph();
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
