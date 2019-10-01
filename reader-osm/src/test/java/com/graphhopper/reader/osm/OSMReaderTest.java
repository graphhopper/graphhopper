/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.LongIndexedContainer;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperIT;
import com.graphhopper.reader.*;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.parsers.OSMMaxHeightParser;
import com.graphhopper.routing.util.parsers.OSMMaxWeightParser;
import com.graphhopper.routing.util.parsers.OSMMaxWidthParser;
import com.graphhopper.routing.util.parsers.OSMRoadClassParser;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests the OSMReader with the normal helper initialized.
 *
 * @author Peter Karich
 */
public class OSMReaderTest {
    private final String file1 = "test-osm.xml";
    private final String file2 = "test-osm2.xml";
    private final String file3 = "test-osm3.xml";
    private final String file4 = "test-osm4.xml";
    // test-osm6.pbf was created by running "osmconvert test-osm6.xml --timestamp=2014-01-02T00:10:14Z -o=test-osm6.pbf"
    // The osmconvert tool can be found here: http://wiki.openstreetmap.org/wiki/Osmconvert
    private final String file6 = "test-osm6.pbf";
    private final String file7 = "test-osm7.xml";
    private final String fileNegIds = "test-osm-negative-ids.xml";
    private final String fileBarriers = "test-barriers.xml";
    private final String fileTurnRestrictions = "test-restrictions.xml";
    private final String fileRoadAttributes = "test-road-attributes.xml";
    private final String fileConditionalTurnRestrictions = "test-conditional-turn-restrictions.xml";
    private final String fileMultipleConditionalTurnRestrictions = "test-multiple-conditional-turn-restrictions.xml";
    private final String dir = "./target/tmp/test-db";
    private CarFlagEncoder carEncoder;
    private BooleanEncodedValue carAccessEnc;
    private BikeFlagEncoder bikeEncoder;
    private FlagEncoder footEncoder;
    private EdgeExplorer carOutExplorer;
    private EdgeExplorer carAllExplorer;

    @Before
    public void setUp() {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(dir));
    }

    GraphHopperStorage newGraph(String directory, EncodingManager encodingManager, boolean is3D, boolean turnRestrictionsImport) {
        return new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager, is3D,
                turnRestrictionsImport ? new TurnCostExtension() : new GraphExtension.NoOpExtension());
    }

    @Test
    public void testMain() {
        GraphHopper hopper = new GraphHopperFacade(file1).importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();

        assertNotNull(graph.getProperties().get("datareader.import.date"));
        assertNotEquals("", graph.getProperties().get("datareader.import.date"));

        assertEquals("2013-01-02T01:10:14Z", graph.getProperties().get("datareader.data.date"));

        assertEquals(4, graph.getNodes());
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49);
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals("street 123, B 122", iter.getName());
        assertEquals(n50, iter.getAdjNode());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(51.25, 9.43), iter.fetchWayGeometry(0));
        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));
        assertFalse(iter.next());

        // get third added location id=30
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);

        NodeAccess na = graph.getNodeAccess();
        assertEquals(9.4, na.getLongitude(findID(hopper.getLocationIndex(), 51.2, 9.4)), 1e-3);
        assertEquals(10, na.getLongitude(findID(hopper.getLocationIndex(), 49, 10)), 1e-3);
        assertEquals(51.249, na.getLatitude(findID(hopper.getLocationIndex(), 51.2492152, 9.4317166)), 1e-3);

        // node 40 is on the way between 30 and 50 => 9.0
        assertEquals(9, na.getLongitude(findID(hopper.getLocationIndex(), 51.25, 9.43)), 1e-3);
    }

    protected int findID(LocationIndex index, double lat, double lon) {
        return index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
    }

    @Test
    public void testSort() {
        GraphHopper hopper = new GraphHopperFacade(file1).setSortGraph(true).importOrLoad();
        NodeAccess na = hopper.getGraphHopperStorage().getNodeAccess();
        assertEquals(10, na.getLongitude(findID(hopper.getLocationIndex(), 49, 10)), 1e-3);
        assertEquals(51.249, na.getLatitude(findID(hopper.getLocationIndex(), 51.2492152, 9.4317166)), 1e-3);
    }

    @Test
    public void testWithBounds() {
        GraphHopper hopper = new GraphHopperFacade(file1) {
            @Override
            protected DataReader createReader(GraphHopperStorage tmpGraph) {
                return new OSMReader(tmpGraph) {
                    @Override
                    public boolean isInBounds(ReaderNode node) {
                        return node.getLat() > 49 && node.getLon() > 8;
                    }
                };
            }
        };

        hopper.importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(4, graph.getNodes());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 51.25);

        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n40, iter.getAdjNode());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(), iter.fetchWayGeometry(0));
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);
        assertTrue(iter.next());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(), iter.fetchWayGeometry(0));
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        // get third added location => 2
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);
        assertFalse(iter.next());
    }

    @Test
    public void testOneWay() {
        GraphHopper hopper = new GraphHopperFacade(file2).importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();

        assertEquals("2014-01-02T01:10:14Z", graph.getProperties().get("datareader.data.date"));

        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52.0);
        int n22 = AbstractGraphStorageTester.getIdOf(graph, 52.133);
        int n23 = AbstractGraphStorageTester.getIdOf(graph, 52.144);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(n10)));
        assertEquals(2, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());

        iter = carAllExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n23, iter.getAdjNode());
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n22, iter.getAdjNode());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));
    }

    @Test
    public void testFerry() {
        GraphHopper hopper = new GraphHopperFacade(file2) {
            @Override
            public void cleanUp() {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();

        int n40 = AbstractGraphStorageTester.getIdOf(graph, 54.0);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55.0);
        assertEquals(GHUtility.asSet(n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n50)));

        // no duration is given => slow speed only!
        int n80 = AbstractGraphStorageTester.getIdOf(graph, 54.1);
        EdgeIterator iter = carOutExplorer.setBaseNode(n80);
        iter.next();
        assertEquals(5, iter.get(carEncoder.getAverageSpeedEnc()), 1e-1);

        // duration 01:10 is given => more precise speed calculation!
        // ~111km (from 54.0,10.1 to 55.0,10.2) in duration=70 minutes => 95km/h => / 1.4 => 71km/h
        iter = carOutExplorer.setBaseNode(n40);
        iter.next();
        assertEquals(70, iter.get(carEncoder.getAverageSpeedEnc()), 1e-1);
    }

    @Test
    public void testMaxSpeed() {
        GraphHopper hopper = new GraphHopperFacade(file2) {
            @Override
            public void cleanUp() {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56.0);
        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        iter.next();
        assertEquals(35, iter.get(carEncoder.getAverageSpeedEnc()), 1e-1);
    }

    @Test
    public void testWayReferencesNotExistingAdjNode() {
        GraphHopper hopper = new GraphHopperFacade(file4).importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();

        assertEquals(2, graph.getNodes());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(GHUtility.asSet(n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
    }

    @Test
    public void testFoot() {
        GraphHopper hopper = new GraphHopperFacade(file3).importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 11.1);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 12);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 11.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 11.3);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 10);

        assertEquals(GHUtility.asSet(n20, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n30, n40), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n20)));

        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(footEncoder));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n20)));
    }

    @Test
    public void testNegativeIds() {
        GraphHopper hopper = new GraphHopperFacade(fileNegIds).importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(4, graph.getNodes());
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);
    }

    @Test
    public void testBarriers() {
        GraphHopper hopper = new GraphHopperFacade(fileBarriers).
                setMinNetworkSize(0, 0).
                importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(8, graph.getNodes());

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 53);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55);

        // separate id
        int new20 = 4;
        assertNotEquals(n20, new20);
        NodeAccess na = graph.getNodeAccess();
        assertEquals(na.getLatitude(n20), na.getLatitude(new20), 1e-5);
        assertEquals(na.getLongitude(n20), na.getLongitude(new20), 1e-5);

        assertEquals(n20, findID(hopper.getLocationIndex(), 52, 9.4));

        assertEquals(GHUtility.asSet(n20, n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(new20, n10, n50), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(new20);
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void avoidsLoopEdges_1525() {
        // loops in OSM should be avoided by adding additional tower node (see #1525, #1531)
        //     C - D
        //      \ /
        //   A - B - E
        GraphHopper hopper = new GraphHopperFacade("test-avoid-loops.xml").importOrLoad();
        checkLoop(hopper);
    }

    void checkLoop(GraphHopper hopper) {
        GraphHopperStorage graph = hopper.getGraphHopperStorage();

        // A, B, E and one of C or D should be tower nodes, in any case C and D should not be collapsed entirely
        // into a loop edge from B to B.
        assertEquals(4, graph.getNodes());
        // two edges going to A and E and two edges going to C or D
        AllEdgesIterator iter = graph.getAllEdges();
        assertEquals(4, iter.length());
        while (iter.next()) {
            assertTrue("found a loop", iter.getAdjNode() != iter.getBaseNode());
        }
        int nodeB = AbstractGraphStorageTester.getIdOf(graph, 12);
        assertTrue("could not find OSM node B", nodeB > -1);
        assertEquals(4, GHUtility.count(graph.createEdgeExplorer().setBaseNode(nodeB)));
    }

    @Test
    public void avoidsLoopEdgesIdenticalLatLon_1533() {
        checkLoop(new GraphHopperFacade("test-avoid-loops2.xml").importOrLoad());
    }

    @Test
    public void avoidsLoopEdgesIdenticalNodeIds_1533() {
        // We can handle the following case with the proper result:
        checkLoop(new GraphHopperFacade("test-avoid-loops3.xml").importOrLoad());
        // We cannot handle the following case, i.e. no loop is created. so we only check that there are no loops
        GraphHopper hopper = new GraphHopperFacade("test-avoid-loops4.xml").importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();
        AllEdgesIterator iter = graph.getAllEdges();
        assertEquals(2, iter.length());
        while (iter.next()) {
            assertTrue("found a loop", iter.getAdjNode() != iter.getBaseNode());
        }
        int nodeB = AbstractGraphStorageTester.getIdOf(graph, 12);
        assertTrue("could not find OSM node B", nodeB > -1);
        assertEquals(2, GHUtility.count(graph.createEdgeExplorer().setBaseNode(nodeB)));
    }

    @Test
    public void testBarriersOnTowerNodes() {
        GraphHopper hopper = new GraphHopperFacade(fileBarriers).
                setMinNetworkSize(0, 0).
                importOrLoad();
        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(8, graph.getNodes());

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56);
        int newId = 5;
        assertEquals(GHUtility.asSet(newId), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n60)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        assertTrue(iter.next());
        assertEquals(newId, iter.getAdjNode());
        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(newId);
        assertTrue(iter.next());
        assertEquals(n60, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testRelation() {
        EncodingManager manager = EncodingManager.create("bike");
        GraphHopperStorage ghStorage = new GraphHopperStorage(new RAMDirectory(), manager, false, new GraphExtension.NoOpExtension());
        OSMReader reader = new OSMReader(ghStorage);
        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.add(new ReaderRelation.Member(ReaderRelation.WAY, 1, ""));
        osmRel.add(new ReaderRelation.Member(ReaderRelation.WAY, 2, ""));

        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        reader.prepareWaysWithRelationInfo(osmRel);

        long flags = reader.getRelFlagsMap().get(1);
        assertTrue(flags != 0);

        // do NOT overwrite with UNCHANGED
        osmRel.setTag("network", "mtb");
        reader.prepareWaysWithRelationInfo(osmRel);
        long flags2 = reader.getRelFlagsMap().get(1);
        assertEquals(flags, flags2);

        // overwrite with outstanding
        osmRel.setTag("network", "ncn");
        reader.prepareWaysWithRelationInfo(osmRel);
        long flags3 = reader.getRelFlagsMap().get(1);
        assertTrue(flags != flags3);
    }

    @Test
    public void testTurnRestrictions() {
        GraphHopper hopper = new GraphHopperFacade(fileTurnRestrictions, true, "").
                importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(15, graph.getNodes());
        assertTrue(graph.getExtension() instanceof TurnCostExtension);
        TurnCostExtension tcStorage = (TurnCostExtension) graph.getExtension();

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);
        int n6 = AbstractGraphStorageTester.getIdOf(graph, 51, 11);
        int n8 = AbstractGraphStorageTester.getIdOf(graph, 54, 11);

        int edge1_6 = GHUtility.getEdge(graph, n1, n6).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge3_8 = GHUtility.getEdge(graph, n3, n8).getEdge();

        int edge3_2 = GHUtility.getEdge(graph, n3, n2).getEdge();
        int edge4_3 = GHUtility.getEdge(graph, n4, n3).getEdge();
        int edge8_3 = GHUtility.getEdge(graph, n8, n3).getEdge();

        // (2-3)->(3-4) only_straight_on = (2-3)->(3-8) restricted
        // (4-3)->(3-8) no_right_turn = (4-3)->(3-8) restricted
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_8)) > 0);
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_8)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge8_3, n3, edge3_2)));

        // u-turn restriction for (6-1)->(1-6) but not for (1-6)->(6-1)
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge1_6, n1, edge1_6)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_6, n6, edge1_6)));

        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_6 = GHUtility.getEdge(graph, n5, n6).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (4-5)->(5-1) right_turn_only = (4-5)->(5-6) restricted
        long costsFlags = tcStorage.getTurnCostFlags(edge4_5, n5, edge5_6);
        assertFalse(carEncoder.isTurnRestricted(costsFlags));
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_1)) > 0);

        // for bike
        assertFalse(bikeEncoder.isTurnRestricted(costsFlags));

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 40, 10);
        int n11 = AbstractGraphStorageTester.getIdOf(graph, 40, 11);
        int n14 = AbstractGraphStorageTester.getIdOf(graph, 39, 11);

        int edge10_11 = GHUtility.getEdge(graph, n10, n11).getEdge();
        int edge11_14 = GHUtility.getEdge(graph, n11, n14).getEdge();

        assertEquals(0, tcStorage.getTurnCostFlags(edge11_14, n11, edge10_11));

        costsFlags = tcStorage.getTurnCostFlags(edge10_11, n11, edge11_14);
        assertFalse(carEncoder.isTurnRestricted(costsFlags));
        assertTrue(bikeEncoder.isTurnRestricted(costsFlags));
    }

    @Test
    public void testRoadAttributes() {
        GraphHopper hopper = new GraphHopperFacade(fileRoadAttributes);
        DataFlagEncoder dataFlagEncoder = new DataFlagEncoder();
        hopper.setEncodingManager(GHUtility.addDefaultEncodedValues(new EncodingManager.Builder()).
                add(new OSMMaxWidthParser()).add(new OSMMaxHeightParser()).add(new OSMMaxWeightParser()).
                add(dataFlagEncoder).build());
        hopper.importOrLoad();

        DecimalEncodedValue widthEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxWidth.KEY);
        DecimalEncodedValue heightEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxHeight.KEY);
        DecimalEncodedValue weightEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxWeight.KEY);

        Graph graph = hopper.getGraphHopperStorage();
        DataFlagEncoder encoder = (DataFlagEncoder) hopper.getEncodingManager().getEncoder("generic");
        assertEquals(5, graph.getNodes());

        int na = AbstractGraphStorageTester.getIdOf(graph, 11.1, 50);
        int nb = AbstractGraphStorageTester.getIdOf(graph, 12, 51);
        int nc = AbstractGraphStorageTester.getIdOf(graph, 11.2, 52);
        int nd = AbstractGraphStorageTester.getIdOf(graph, 11.3, 51);
        int ne = AbstractGraphStorageTester.getIdOf(graph, 10, 51);

        EdgeIteratorState edge_ab = GHUtility.getEdge(graph, na, nb);
        EdgeIteratorState edge_ad = GHUtility.getEdge(graph, na, nd);
        EdgeIteratorState edge_ae = GHUtility.getEdge(graph, na, ne);
        EdgeIteratorState edge_bc = GHUtility.getEdge(graph, nb, nc);
        EdgeIteratorState edge_bd = GHUtility.getEdge(graph, nb, nd);
        EdgeIteratorState edge_cd = GHUtility.getEdge(graph, nc, nd);
        EdgeIteratorState edge_ce = GHUtility.getEdge(graph, nc, ne);
        EdgeIteratorState edge_de = GHUtility.getEdge(graph, nd, ne);

        assertEquals(4.0, edge_ab.get(heightEnc), 1e-5);
        assertEquals(2.5, edge_ab.get(widthEnc), 1e-5);
        assertEquals(4.4, edge_ab.get(weightEnc), 1e-5);

        assertEquals(4.0, edge_bc.get(heightEnc), 1e-5);
        assertEquals(2.5, edge_bc.get(widthEnc), 1e-5);
        assertEquals(4.4, edge_bc.get(weightEnc), 1e-5);

        assertEquals(4.4, edge_ad.get(heightEnc), 1e-5);
        assertEquals(3.5, edge_ad.get(widthEnc), 1e-5);
        assertEquals(17.5, edge_ad.get(weightEnc), 1e-5);

        assertEquals(4.4, edge_cd.get(heightEnc), 1e-5);
        assertEquals(3.5, edge_cd.get(widthEnc), 1e-5);
        assertEquals(17.5, edge_cd.get(weightEnc), 1e-5);
    }

    @Test
    public void testEstimatedCenter() {
        final CarFlagEncoder encoder = new CarFlagEncoder() {
            private EncodedValueOld objectEncoder;

            @Override
            public int defineNodeBits(int index, int shift) {
                shift = super.defineNodeBits(index, shift);
                objectEncoder = new EncodedValueOld("oEnc", shift, 2, 1, 0, 3, true);
                return shift + 2;
            }

            @Override
            public long handleNodeTags(ReaderNode node) {
                if (node.hasTag("test", "now"))
                    return -objectEncoder.setValue(0, 1);
                return 0;
            }
        };
        EncodingManager manager = EncodingManager.create(encoder);
        GraphHopperStorage ghStorage = newGraph(dir, manager, false, false);
        final Map<Integer, Double> latMap = new HashMap<>();
        final Map<Integer, Double> lonMap = new HashMap<>();
        latMap.put(1, 1.1d);
        latMap.put(2, 1.2d);

        lonMap.put(1, 1.0d);
        lonMap.put(2, 1.0d);

        OSMReader osmreader = new OSMReader(ghStorage) {
            // mock data access
            @Override
            double getTmpLatitude(int id) {
                return latMap.get(id);
            }

            @Override
            double getTmpLongitude(int id) {
                return lonMap.get(id);
            }

            @Override
            Collection<EdgeIteratorState> addOSMWay(LongIndexedContainer osmNodeIds, IntsRef wayFlags, long osmId) {
                return Collections.emptyList();
            }
        };

        // save some node tags for first node
        ReaderNode osmNode = new ReaderNode(1, 1.1d, 1.0d);
        osmNode.setTag("test", "now");
        osmreader.getNodeFlagsMap().put(1, encoder.handleNodeTags(osmNode));

        ReaderWay way = new ReaderWay(1L);
        way.getNodes().add(1);
        way.getNodes().add(2);
        way.setTag("highway", "motorway");
        osmreader.getNodeMap().put(1, 1);
        osmreader.getNodeMap().put(2, 2);
        osmreader.processWay(way);

        GHPoint p = way.getTag("estimated_center", null);
        assertEquals(1.15, p.lat, 1e-3);
        assertEquals(1.0, p.lon, 1e-3);
        Double d = way.getTag("estimated_distance", null);
        assertEquals(11119.5, d, 1e-1);
    }

    @Test
    public void testReadEleFromCustomOSM() {
        GraphHopper hopper = new GraphHopperFacade("custom-osm-ele.xml") {
            @Override
            protected DataReader createReader(GraphHopperStorage tmpGraph) {
                return initDataReader(new OSMReader(tmpGraph) {
                    @Override
                    protected double getElevation(ReaderNode node) {
                        return node.getEle();
                    }
                });
            }
        }.setElevation(true).importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49);

        EdgeIteratorState edge = GHUtility.getEdge(graph, n20, n50);
        assertEquals(Helper.createPointList3D(52, 9, -10, 51.25, 9.43, 100, 49, 10, -30), edge.fetchWayGeometry(3));
    }

    @Test
    public void testReadEleFromDataProvider() {
        GraphHopper hopper = new GraphHopperFacade("test-osm5.xml");
        // get N10E046.hgt.zip
        ElevationProvider provider = new SRTMProvider(GraphHopperIT.DIR);
        hopper.setElevationProvider(provider);
        hopper.importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 49.501);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 49.5011);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49.5001);

        EdgeIteratorState edge = GHUtility.getEdge(graph, n50, n30);
        assertEquals(Helper.createPointList3D(49.5001, 11.501, 426, 49.5002, 11.5015, 441, 49.5011, 11.502, 410.0),
                edge.fetchWayGeometry(3));

        edge = GHUtility.getEdge(graph, n10, n50);
        assertEquals(Helper.createPointList3D(49.501, 11.5001, 383.0, 49.5001, 11.501, 426.0),
                edge.fetchWayGeometry(3));
    }

    /**
     * Tests the combination of different turn cost flags by different encoders.
     */
    @Test
    public void testTurnFlagCombination() {
        final OSMReader.TurnCostTableEntry turnCostEntry_car = new OSMReader.TurnCostTableEntry();
        final OSMReader.TurnCostTableEntry turnCostEntry_foot = new OSMReader.TurnCostTableEntry();
        final OSMReader.TurnCostTableEntry turnCostEntry_bike = new OSMReader.TurnCostTableEntry();

        final OSMTurnRelation osmTurnRelation = new OSMTurnRelation(1, 1,1, OSMTurnRelation.Type.NOT);

        CarFlagEncoder car = new CarFlagEncoder(5, 5, 24);
        FootFlagEncoder foot = new FootFlagEncoder();
        BikeFlagEncoder bike = new BikeFlagEncoder(4, 2, 24);
        EncodingManager manager = EncodingManager.create(Arrays.asList(bike, foot, car));

        GraphHopperStorage ghStorage = new GraphBuilder(manager).create();
        OSMReader reader = new OSMReader(ghStorage) {
            @Override
            public Collection<OSMReader.TurnCostTableEntry> analyzeTurnRelation(FlagEncoder encoder,
                                                                                      OSMTurnRelation turnRelation) {
                // simulate by returning one turn cost entry directly
                if (encoder.toString().equalsIgnoreCase("car")) {

                    return Collections.singleton(turnCostEntry_car);
                } else if (encoder.toString().equalsIgnoreCase("foot")) {
                    return Collections.singleton(turnCostEntry_foot);
                } else if (encoder.toString().equalsIgnoreCase("bike")) {
                    return Collections.singleton(turnCostEntry_bike);
                } else {
                    throw new IllegalArgumentException("illegal encoder " + encoder.toString());
                }
            }
        };

        // turn cost entries for car and foot are for the same relations (same viaNode, edgeFrom and edgeTo),
        // turn cost entry for bike is for another relation (different viaNode)
        turnCostEntry_car.edgeFrom = 1;
        turnCostEntry_foot.edgeFrom = 1;
        turnCostEntry_bike.edgeFrom = 2;

        // calculating arbitrary flags using the encoders
        turnCostEntry_car.flags = car.getTurnFlags(true, 0);
        turnCostEntry_foot.flags = foot.getTurnFlags(true, 0);
        turnCostEntry_bike.flags = bike.getTurnFlags(false, 10);

        // we expect two different entries: the first one is a combination of turn flags of car and foot,
        // since they provide the same relation, the other one is for bike only
        long assertFlag1 = turnCostEntry_car.flags | turnCostEntry_foot.flags;
        long assertFlag2 = turnCostEntry_bike.flags;

        // combine flags of all encoders
        Collection<OSMReader.TurnCostTableEntry> entries = reader.analyzeTurnRelation(osmTurnRelation);

        // we expect two different turnCost entries
        assertEquals(2, entries.size());

        for (OSMReader.TurnCostTableEntry entry : entries) {
            if (entry.edgeFrom == 1) {
                // the first entry provides turn flags for car and foot only
                assertEquals(assertFlag1, entry.flags);
                assertTrue(car.isTurnRestricted(entry.flags));
                assertFalse(foot.isTurnRestricted(entry.flags));
                assertFalse(bike.isTurnRestricted(entry.flags));

                assertTrue(Double.isInfinite(car.getTurnCost(entry.flags)));
                assertEquals(0, foot.getTurnCost(entry.flags), 1e-1);
                assertEquals(0, bike.getTurnCost(entry.flags), 1e-1);
            } else if (entry.edgeFrom == 2) {
                // the 2nd entry provides turn flags for bike only
                assertEquals(assertFlag2, entry.flags);
                assertFalse(car.isTurnRestricted(entry.flags));
                assertFalse(foot.isTurnRestricted(entry.flags));
                assertFalse(bike.isTurnRestricted(entry.flags));

                assertEquals(0, car.getTurnCost(entry.flags), 1e-1);
                assertEquals(0, foot.getTurnCost(entry.flags), 1e-1);
                assertEquals(10, bike.getTurnCost(entry.flags), 1e-1);
            }
        }
    }

    @Test
    public void testConditionalTurnRestriction() {
        GraphHopper hopper = new GraphHopperFacade(fileConditionalTurnRestrictions, true, "").
                importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(8, graph.getNodes());
        assertTrue(graph.getExtension() instanceof TurnCostExtension);
        TurnCostExtension tcStorage = (TurnCostExtension) graph.getExtension();

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);
        int n6 = AbstractGraphStorageTester.getIdOf(graph, 51, 11);
        int n8 = AbstractGraphStorageTester.getIdOf(graph, 54, 11);

        int edge1_6 = GHUtility.getEdge(graph, n1, n6).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge3_8 = GHUtility.getEdge(graph, n3, n8).getEdge();

        int edge3_2 = GHUtility.getEdge(graph, n3, n2).getEdge();
        int edge4_3 = GHUtility.getEdge(graph, n4, n3).getEdge();
        int edge8_3 = GHUtility.getEdge(graph, n8, n3).getEdge();

        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_6 = GHUtility.getEdge(graph, n5, n6).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (2-3)->(3-4) only_straight_on except bicycle = (2-3)->(3-8) restricted for car
        // (4-3)->(3-8) no_right_turn dedicated to motorcar = (4-3)->(3-8) restricted for car
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_8)) > 0);
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_8)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge8_3, n3, edge3_2)));

        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_8)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_8)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_2)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge2_3, n3, edge3_4)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_3, n3, edge3_2)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge8_3, n3, edge3_2)));

        // u-turn except bus;bicycle restriction for (6-1)->(1-6) but not for (1-6)->(6-1)
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge1_6, n1, edge1_6)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_6, n6, edge1_6)));

        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_6, n1, edge1_6)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_6, n6, edge1_6)));

        // (4-5)->(5-6) right_turn_only dedicated to motorcar = (4-5)->(5-1) restricted
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_6)));
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_1)) > 0);

        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_6)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_1)));
    }

    @Test
    public void testMultipleTurnRestrictions() {
        GraphHopper hopper = new GraphHopperFacade(fileMultipleConditionalTurnRestrictions, true, "").
                importOrLoad();

        Graph graph = hopper.getGraphHopperStorage();
        assertEquals(5, graph.getNodes());
        assertTrue(graph.getExtension() instanceof TurnCostExtension);
        TurnCostExtension tcStorage = (TurnCostExtension) graph.getExtension();

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);

        int edge1_2 = GHUtility.getEdge(graph, n1, n2).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (1-2)->(2-3) no_right_turn for motorcar and bus
        assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_2, n2, edge2_3)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge1_2, n2, edge2_3)));

        // (3-4)->(4-5) no_right_turn for motorcycle and motorcar
        assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge3_4, n4, edge4_5)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge3_4, n4, edge4_5)));

        // (5-1)->(1-2) no_right_turn for bus and psv except for motorcar and bicycle
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_1)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge4_5, n5, edge5_1)));

        // (5-1)->(1-2) no_right_turn for motorcar and motorcycle except for bus and bicycle
        assertTrue(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge5_1, n1, edge1_2)));
        assertFalse(bikeEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(edge5_1, n1, edge1_2)));
    }

    @Test
    public void testPreferredLanguage() {
        GraphHopper hopper = new GraphHopperFacade(file1, false, "de").importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals("straße 123, B 122", iter.getName());

        hopper = new GraphHopperFacade(file1, false, "el").importOrLoad();
        graph = hopper.getGraphHopperStorage();
        n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals("διαδρομή 666", iter.getName());
    }

    @Test
    public void testDataDateWithinPBF() {
        GraphHopper hopper = new GraphHopperFacade("test-osm6.pbf").importOrLoad();
        GraphHopperStorage graph = hopper.getGraphHopperStorage();

        assertEquals("2014-01-02T00:10:14Z", graph.getProperties().get("datareader.data.date"));
    }

    @Test
    public void testCrossBoundary_issue667() {
        GraphHopper hopper = new GraphHopperFacade("test-osm-waterway.xml").importOrLoad();
        QueryResult qr = hopper.getLocationIndex().findClosest(0.1, 179.5, EdgeFilter.ALL_EDGES);
        assertTrue(qr.isValid());
        assertEquals(0.1, qr.getSnappedPoint().lat, 0.1);
        assertEquals(179.5, qr.getSnappedPoint().lon, 0.1);
        assertEquals(11, qr.getClosestEdge().getDistance() / 1000, 1);

        qr = hopper.getLocationIndex().findClosest(0.1, -179.6, EdgeFilter.ALL_EDGES);
        assertTrue(qr.isValid());
        assertEquals(0.1, qr.getSnappedPoint().lat, 0.1);
        assertEquals(-179.6, qr.getSnappedPoint().lon, 0.1);
        assertEquals(56, qr.getClosestEdge().getDistance() / 1000, 1);
    }

    @Test
    public void testRoutingRequestFails_issue665() {
        GraphHopper hopper = new GraphHopperOSM()
                .setDataReaderFile(getClass().getResource(file7).getFile())
                .setEncodingManager(EncodingManager.create("car,motorcycle"))
                .setGraphHopperLocation(dir);
        hopper.getCHFactoryDecorator().setEnabled(false);
        hopper.importOrLoad();
        GHRequest req = new GHRequest(48.977277, 8.256896, 48.978876, 8.254884).
                setWeighting("curvature").
                setVehicle("motorcycle");

        GHResponse ghRsp = hopper.route(req);
        assertFalse(ghRsp.getErrors().toString(), ghRsp.hasErrors());
    }

    @Test
    public void testRoadClassInfo() {
        GraphHopper gh = new GraphHopperOSM() {

            @Override
            protected DataReader importData() {
                try {
                    DataReader reader = new OSMReader(getGraphHopperStorage()).setFile(new File(getClass().getResource(file2).toURI()));
                    reader.readGraph();
                    return reader;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.setEncodingManager(new EncodingManager.Builder().add(carEncoder = new CarFlagEncoder()).add(new OSMRoadClassParser()).build()).
                setGraphHopperLocation(dir).setCHEnabled(false).
                importOrLoad();

        GHResponse response = gh.route(new GHRequest(51.2492152, 9.4317166, 52.133, 9.1).setPathDetails(Arrays.asList(RoadClass.KEY)));
        List<PathDetail> list = response.getBest().getPathDetails().get(RoadClass.KEY);
        assertEquals(3, list.size());
        assertEquals(RoadClass.MOTORWAY.toString(), list.get(0).getValue());

        response = gh.route(new GHRequest(51.2492152, 9.4317166, 52.133, 9.1).setPathDetails(Arrays.asList(RoadAccess.KEY)));
        Throwable ex = response.getErrors().get(0);
        assertTrue(ex.getMessage(), ex.getMessage().contains("You requested the details [road_access]"));
    }

    class GraphHopperFacade extends GraphHopperOSM {
        public GraphHopperFacade(String osmFile) {
            this(osmFile, false, "");
        }

        public GraphHopperFacade(String osmFile, boolean turnCosts, String prefLang) {
            setStoreOnFlush(false);
            setOSMFile(osmFile);
            setGraphHopperLocation(dir);
            setCHEnabled(false);

            if (turnCosts) {
                carEncoder = new CarFlagEncoder(5, 5, 1);
                bikeEncoder = new BikeFlagEncoder(4, 2, 1);
            } else {
                carEncoder = new CarFlagEncoder();
                bikeEncoder = new BikeFlagEncoder();
            }

            footEncoder = new FootFlagEncoder();
            setEncodingManager(new EncodingManager.Builder().add(footEncoder).add(carEncoder).add(bikeEncoder).
                    setPreferredLanguage(prefLang).build());
            carAccessEnc = carEncoder.getAccessEnc();
        }

        @Override
        protected DataReader createReader(GraphHopperStorage tmpGraph) {
            return initDataReader(new OSMReader(tmpGraph));
        }

        @Override
        protected DataReader importData() throws IOException {
            GraphHopperStorage tmpGraph = newGraph(dir, getEncodingManager(), hasElevation(),
                    getEncodingManager().needsTurnCostsSupport());
            setGraphHopperStorage(tmpGraph);

            DataReader osmReader = createReader(tmpGraph);
            try {
                osmReader.setFile(new File(getClass().getResource(getOSMFile()).toURI()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            osmReader.readGraph();
            carOutExplorer = getGraphHopperStorage().createEdgeExplorer(DefaultEdgeFilter.outEdges(carEncoder));
            carAllExplorer = getGraphHopperStorage().createEdgeExplorer(DefaultEdgeFilter.allEdges(carEncoder));
            return osmReader;
        }
    }
}
