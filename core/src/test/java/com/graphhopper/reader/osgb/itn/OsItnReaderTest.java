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
package com.graphhopper.reader.osgb.itn;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osgb.AbstractOsItnReaderTest;
import com.graphhopper.reader.osgb.AbstractOsReader;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.AbstractGraphStorageTester;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

/**
 *
 * @author Peter Karich
 * @author Stuart Adam
 */
public class OsItnReaderTest extends AbstractOsItnReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(OsItnReaderTest.class);
    private static final InputStream COMPLEX_ITN_EXAMPLE = OsItnReader.class.getResourceAsStream("os-itn-sample.xml");

    @Test
    public void testReadItnNoEntryMultipointCrossroad() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-no-entry-multipoint-crossroad.xml");
        // File file = new
        // File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-crossroads.xml");
        // File file = new
        // File("./src/test/resources/com/graphhopper/reader/os-itn-noentry.xml");
        final OsItnReader osItnReader = readGraphFile(graph, file);

        logger.info("We have " + graph.getNodes() + " nodes");
        // Is 7 correct?
        // assertEquals(7, graph.getNodes());

        logger.info("80 " + osItnReader.getNodeMap().get(4000000025277880l));
        logger.info("81 " + osItnReader.getNodeMap().get(4000000025277881l));
        logger.info("82 " + osItnReader.getNodeMap().get(4000000025277882l));
        logger.info("83 " + osItnReader.getNodeMap().get(4000000025277883l));
        logger.info("84 " + osItnReader.getNodeMap().get(4000000025277884l));
        logger.info("85 " + osItnReader.getNodeMap().get(4000000025277885l));
        logger.info("86 " + osItnReader.getNodeMap().get(4000000025277886l));

        final boolean direction = true;
        // The asserts below work out whether we can visit the nodes from a
        // certain point

        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        logger.info("Node 0 " + count(explorer.setBaseNode(0)));
        logger.info("Node 1 " + count(explorer.setBaseNode(1)));
        logger.info("Node 2 " + count(explorer.setBaseNode(2)));
        logger.info("Node 3 " + count(explorer.setBaseNode(3)));
        logger.info("Node 4 " + count(explorer.setBaseNode(4)));
        logger.info("Node 5 " + count(explorer.setBaseNode(5)));
        logger.info("Node 6 " + count(explorer.setBaseNode(6)));
        logger.info("Node 7 " + count(explorer.setBaseNode(7)));
        logger.info("Node 8 " + count(explorer.setBaseNode(8)));

        // carAllExplorer = graph.createEdgeExplorer(new
        // DefaultEdgeFilter(carEncoder, true, true));

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

        GHUtility.printInfo(graph, 0, 20, EdgeFilter.ALL_EDGES);
        assertEquals(4, count(explorer.setBaseNode(0))); // Central Tower
        assertEquals(1, count(explorer.setBaseNode(1))); // Cross Road Vertex
        assertEquals(1, count(explorer.setBaseNode(4))); // Cross Road Vertex
        assertEquals(1, count(explorer.setBaseNode(5))); // Cross Road Vertex
        assertEquals(1, count(explorer.setBaseNode(6))); // Cross Road Vertex

        assertEquals(2, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3))); // No Entry part way
        // down one crossroad
        // branch

        // Assert that this is true
        iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(6, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(5, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(4);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(5);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = explorer.setBaseNode(6);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());
    }


    @Test
    public void testReadSimpleCrossRoads() throws IOException {
        final boolean turnRestrictionsImport = false;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-crossroad.xml");
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);
    }

    @Test
    public void testReadSimpleMultiPointCrossRoads() throws IOException {
        final boolean turnRestrictionsImport = false;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-multipoint-crossroad.xml");
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        checkMultiNodeNetwork(graph);
    }

    @Test
    public void testReadSimpleCrossRoadsWithTurnRestriction() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-restricted-crossroad.xml");
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        checkSimpleNodeNetwork(graph);

        carOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));

        final int n0 = AbstractGraphStorageTester.getIdOf(graph, node0Lat, node0Lon);
        final int n1 = AbstractGraphStorageTester.getIdOf(graph, node1Lat, node1Lon);
        final int n2 = AbstractGraphStorageTester.getIdOf(graph, node2Lat, node2Lon);
        final int n3 = AbstractGraphStorageTester.getIdOf(graph, node3Lat, node3Lon);
        final int n4 = AbstractGraphStorageTester.getIdOf(graph, node4Lat, node4Lon);

        final int edge0_1 = getEdge(n1, n0);
        final int edge1_2 = getEdge(n1, n2);
        final int edge1_3 = getEdge(n1, n3);
        final int edge1_4 = getEdge(n1, n4);

        TurnCostExtension tcStorage = (TurnCostExtension)graph.getExtension();

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

    private void checkMultiNodeNetwork(final GraphHopperStorage graph) {
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));

        final EdgeIterator iter = explorer.setBaseNode(0);
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
        final long flags = iter.getFlags();
        assertEquals(55.0, carEncoder.getSpeed(flags), 1e-1);
        assertFalse(iter.next());
    }


    @Test
    @Ignore
    public void testReadSample() throws IOException {
        final boolean turnRestrictionsImport = false;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final AbstractOsReader<Long> osItnReader = new OsItnReader(graph);
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-sample.xml");
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(new EncodingManager("CAR,FOOT"));
        osItnReader.readGraph();
        assertEquals(760, graph.getNodes());
    }

    @Test
    public void testRegex() {
        final String s1 = "123,123 123,123";
        final String s2 = " 123,123 123,123";
        final String s3 = "123,123 123,123 ";
        final String s4 = " 123,123 123,123 ";

        String[] parsed = s1.split(" ");
        assertEquals(2, parsed.length);
        assertEquals(2, s2.trim().split(" ").length);
        parsed = s3.split(" ");
        assertEquals(2, parsed.length);
        assertEquals("123,123", parsed[0]);
        assertEquals("123,123", parsed[1]);

        assertEquals(2, s4.trim().split(" ").length);

    }

    @Test
    //    @Ignore
    public void testItnGraphHopperWithHighwaysNetworkData() {
        String graphLoc = "./target/output/modified-exeter-gh";
        String inputFile = "/media/sf_/media/shared/modified-exeter/58096-SX9192-modified.xml";
        //        String inputFile = "./src/test/resources/com/graphhopper/reader/os-itn-wickham-direction-error.xml";

        Map<String, String> args = new HashMap<>();
        args.put("hn.data", "/data/Development/highways_network_full/");
        args.put("hn.graph.location", "./target/output/highways_network");
        args.put("graph.location", graphLoc);
        args.put("osmreader.osm", inputFile);
        args.put("config", "../config.properties");
        CmdArgs commandLineArguments = new CmdArgs(args);
        commandLineArguments = CmdArgs.readFromConfigAndMerge(commandLineArguments, "config", "graphhopper.config");

        GraphHopper graphHopper = new GraphHopper().setInMemory().setAsItnReader().init(commandLineArguments);
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();

    }
}
