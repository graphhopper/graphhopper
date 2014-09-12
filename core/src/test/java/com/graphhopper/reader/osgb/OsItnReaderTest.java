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
import java.util.HashSet;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.osgb.dpn.OsDpnInputFile;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
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
	private EncodingManager encodingManager = new EncodingManager("car:com.graphhopper.routing.util.RelationCarFlagEncoder");
	private RelationCarFlagEncoder carEncoder = (RelationCarFlagEncoder) encodingManager
			.getEncoder("CAR");
	private EdgeFilter carOutEdges = new DefaultEdgeFilter(
			carEncoder, false, true);


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
	private EdgeExplorer carOutExplorer;
	private EdgeExplorer carAllExplorer;

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
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge0_1, edge1_2)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_2, edge0_1)));
		
		assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge0_1, edge1_3)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_3, edge0_1)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge0_1, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_4, edge0_1)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_2, edge1_3)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_3, edge1_2)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_2, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_4, edge1_2)));
		
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_3, edge1_4)));
		assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCosts(n1, edge1_4, edge1_3)));
		
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
		checkSimpleOneWayNetwork(graph);
		checkOneWay(graph);
	}

	private void checkOneWay(GraphHopperStorage graph) {
		System.err.println(carEncoder.getClass());
		carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
		EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
		evaluateRouting(iter, 4, true, false, false);
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
	
	private void checkSimpleOneWayNetwork(GraphHopperStorage graph) {
		EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
		assertEquals(4, count(explorer.setBaseNode(0)));
		assertEquals(1, count(explorer.setBaseNode(1)));
		assertEquals(1, count(explorer.setBaseNode(2)));
		assertEquals(1, count(explorer.setBaseNode(3)));
		assertEquals(0, count(explorer.setBaseNode(4)));
		
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
        System.err.println("FLAGS:" + flags);
        assertEquals(100, carEncoder.getSpeed(flags), 1e-1);
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

	
	@Test
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
