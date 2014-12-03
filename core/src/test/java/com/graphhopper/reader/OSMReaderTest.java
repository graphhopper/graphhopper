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
package com.graphhopper.reader;

import static org.junit.Assert.*;
import gnu.trove.list.TLongList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.AbstractGraphStorageTester;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Tests the OSMReader with the normal helper initialized.
 * <p/>
 * @author Peter Karich
 */
public class OSMReaderTest
{
    private final String file1 = "test-osm.xml";
    private final String file2 = "test-osm2.xml";
    private final String file3 = "test-osm3.xml";
    private final String file4 = "test-osm4.xml";
    private final String fileNegIds = "test-osm-negative-ids.xml";
    private final String fileBarriers = "test-barriers.xml";
    private final String fileTurnRestrictions = "test-restrictions.xml";
    private final String dir = "./target/tmp/test-db";
    private CarFlagEncoder carEncoder;
    private BikeFlagEncoder bikeEncoder;
    private FlagEncoder footEncoder;
    private EdgeExplorer carOutExplorer;
    private EdgeExplorer carAllExplorer;

    @Before
    public void setUp()
    {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(dir));
    }

    GraphStorage newGraph( String directory, EncodingManager encodingManager, boolean is3D, boolean turnRestrictionsImport )
    {
        return new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager,
                is3D, turnRestrictionsImport ? new TurnCostExtension() : new GraphExtension.NoExtendedStorage());
    }

    class GraphHopperTest extends GraphHopper
    {
        public GraphHopperTest( String osmFile )
        {
            this(osmFile, false);
        }

        public GraphHopperTest( String osmFile, boolean turnCosts )
        {
            setStoreOnFlush(false);
            setOSMFile(osmFile);
            setGraphHopperLocation(dir);
            setEncodingManager(new EncodingManager("CAR,FOOT"));
            setCHEnable(false);

            if (turnCosts)
            {
                carEncoder = new CarFlagEncoder(5, 5, 3);
                bikeEncoder = new BikeFlagEncoder(4, 2, 3);
            } else
            {
                carEncoder = new CarFlagEncoder();
                bikeEncoder = new BikeFlagEncoder();
            }

            footEncoder = new FootFlagEncoder();

            setEncodingManager(new EncodingManager(footEncoder, carEncoder, bikeEncoder));
        }

        @Override
        protected DataReader createReader( GraphStorage tmpGraph )
        {
            return initOSMReader(new OSMReader(tmpGraph));
        }

        @Override
        protected DataReader importData() throws IOException
        {
            GraphStorage tmpGraph = newGraph(dir, getEncodingManager(), hasElevation(), getEncodingManager().needsTurnCostsSupport());
            setGraph(tmpGraph);

            DataReader osmReader = createReader(tmpGraph);
            try
            {
                ((OSMReader) osmReader).setOSMFile(new File(getClass().getResource(getOSMFile()).toURI()));
            } catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
            osmReader.readGraph();
            carOutExplorer = getGraph().createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));
            carAllExplorer = getGraph().createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
            return osmReader;
        }
    }

    InputStream getResource( String file )
    {
        return getClass().getResourceAsStream(file);
    }

    @Test
    public void testMain()
    {
        GraphHopper hopper = new GraphHopperTest(file1).importOrLoad();
        GraphStorage graph = (GraphStorage) hopper.getGraph();

        assertNotNull(graph.getProperties().get("osmreader.import.date"));
        assertNotEquals("", graph.getProperties().get("osmreader.import.date"));

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
        FlagEncoder flags = carEncoder;
        assertTrue(flags.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertTrue(flags.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        assertTrue(flags.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertTrue(flags.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));
        assertFalse(iter.next());

        // get third added location id=30
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);

        NodeAccess na = graph.getNodeAccess();
        assertEquals(9.4, na.getLongitude(hopper.getLocationIndex().findID(51.2, 9.4)), 1e-3);
        assertEquals(10, na.getLongitude(hopper.getLocationIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, na.getLatitude(hopper.getLocationIndex().findID(51.2492152, 9.4317166)), 1e-3);

        // node 40 is on the way between 30 and 50 => 9.0
        assertEquals(9, na.getLongitude(hopper.getLocationIndex().findID(51.25, 9.43)), 1e-3);
    }

    @Test
    public void testSort()
    {
        GraphHopper hopper = new GraphHopperTest(file1).setSortGraph(true).importOrLoad();
        Graph graph = hopper.getGraph();
        NodeAccess na = graph.getNodeAccess();
        assertEquals(10, na.getLongitude(hopper.getLocationIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, na.getLatitude(hopper.getLocationIndex().findID(51.2492152, 9.4317166)), 1e-3);
    }

    @Test
    public void testWithBounds()
    {
        GraphHopper hopper = new GraphHopperTest(file1)
        {
            @Override
            protected DataReader createReader( GraphStorage tmpGraph )
            {
                return new OSMReader(tmpGraph)
                {
                    @Override
                    public boolean isInBounds( OSMNode node )
                    {
                        return node.getLat() > 49 && node.getLon() > 8;
                    }
                }.setEncodingManager(getEncodingManager());
            }
        };

        hopper.importOrLoad();

        Graph graph = hopper.getGraph();
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
    public void testOneWay()
    {
        GraphHopper hopper = new GraphHopperTest(file2).importOrLoad();
        Graph graph = hopper.getGraph();

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

        FlagEncoder encoder = carEncoder;
        iter = carAllExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n23, iter.getAdjNode());
        assertTrue(encoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));

        assertTrue(iter.next());
        assertEquals(n22, iter.getAdjNode());
        assertFalse(encoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));

        assertTrue(iter.next());
        assertFalse(encoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertTrue(encoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertFalse(encoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(encoder.isBool(iter.getFlags(), FlagEncoder.K_FORWARD));
        assertTrue(encoder.isBool(iter.getFlags(), FlagEncoder.K_BACKWARD));
    }

    @Test
    public void testFerry()
    {
        GraphHopper hopper = new GraphHopperTest(file2)
        {
            @Override
            public void cleanUp()
            {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraph();

        int n40 = AbstractGraphStorageTester.getIdOf(graph, 54.0);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55.0);
        assertEquals(GHUtility.asSet(n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n50)));

        // no duration is given => slow speed only!
        int n80 = AbstractGraphStorageTester.getIdOf(graph, 54.1);
        EdgeIterator iter = carOutExplorer.setBaseNode(n80);
        iter.next();
        assertEquals(5, carEncoder.getSpeed(iter.getFlags()), 1e-1);

        // duration 01:10 is given => more precise speed calculation! 
        // ~111km (from 54.0,10.1 to 55.0,10.2) in duration=70 minutes => 95km/h => / 1.4 => 71km/h        
        iter = carOutExplorer.setBaseNode(n40);
        iter.next();
        assertEquals(70, carEncoder.getSpeed(iter.getFlags()), 1e-1);
    }

    @Test
    public void testMaxSpeed()
    {
        GraphHopper hopper = new GraphHopperTest(file2)
        {
            @Override
            public void cleanUp()
            {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraph();

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56.0);
        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        iter.next();
        assertEquals(35, carEncoder.getSpeed(iter.getFlags()), 1e-1);
    }

    @Test
    public void testWayReferencesNotExistingAdjNode()
    {
        GraphHopper hopper = new GraphHopperTest(file4).importOrLoad();
        Graph graph = hopper.getGraph();

        assertEquals(2, graph.getNodes());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(GHUtility.asSet(n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
    }

    @Test
    public void testFoot()
    {
        GraphHopper hopper = new GraphHopperTest(file3).importOrLoad();
        Graph graph = hopper.getGraph();

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 11.1);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 12);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 11.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 11.3);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 10);

        assertEquals(GHUtility.asSet(n20, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n30, n40), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n20)));

        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, false, true));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n20)));
    }

    @Test
    public void testNegativeIds()
    {
        GraphHopper hopper = new GraphHopperTest(fileNegIds).importOrLoad();
        Graph graph = hopper.getGraph();
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
    public void testBarriers()
    {
        GraphHopper hopper = new GraphHopperTest(fileBarriers).importOrLoad();
        Graph graph = hopper.getGraph();
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

        assertEquals(n20, hopper.getLocationIndex().findID(52, 9.4));

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
    public void testBarriersOnTowerNodes()
    {
        GraphHopper hopper = new GraphHopperTest(fileBarriers).importOrLoad();
        Graph graph = hopper.getGraph();
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
    public void testRelation()
    {
        EncodingManager manager = new EncodingManager("bike");
        OSMReader reader = new OSMReader(new GraphHopperStorage(new RAMDirectory(), manager, false)).
                setEncodingManager(manager);
        OSMRelation osmRel = new OSMRelation(1);
        osmRel.getMembers().add(new OSMRelation.Member(OSMRelation.WAY, 1, ""));
        osmRel.getMembers().add(new OSMRelation.Member(OSMRelation.WAY, 2, ""));

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
    public void testTurnRestrictions()
    {
        GraphHopper hopper = new GraphHopperTest(fileTurnRestrictions, true).
                importOrLoad();
        GraphStorage graph = hopper.getGraph();
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
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(n3, edge2_3, edge3_8)) > 0);
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(n3, edge4_3, edge3_8)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n3, edge2_3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n3, edge2_3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n3, edge2_3, edge3_4)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n3, edge4_3, edge3_2)));
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n3, edge8_3, edge3_2)));

        // u-turn restriction for (6-1)->(1-6) but not for (1-6)->(6-1)
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(n1, edge1_6, edge1_6)) > 0);
        assertFalse(carEncoder.isTurnRestricted(tcStorage.getTurnCostFlags(n6, edge1_6, edge1_6)));

        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_6 = GHUtility.getEdge(graph, n5, n6).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (4-5)->(5-1) right_turn_only = (4-5)->(5-6) restricted 
        long costsFlags = tcStorage.getTurnCostFlags(n5, edge4_5, edge5_6);
        assertFalse(carEncoder.isTurnRestricted(costsFlags));
        assertTrue(carEncoder.getTurnCost(tcStorage.getTurnCostFlags(n5, edge4_5, edge5_1)) > 0);

        // for bike
        assertFalse(bikeEncoder.isTurnRestricted(costsFlags));

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 40, 10);
        int n11 = AbstractGraphStorageTester.getIdOf(graph, 40, 11);
        int n14 = AbstractGraphStorageTester.getIdOf(graph, 39, 11);

        int edge10_11 = GHUtility.getEdge(graph, n10, n11).getEdge();
        int edge11_14 = GHUtility.getEdge(graph, n11, n14).getEdge();

        assertEquals(0, tcStorage.getTurnCostFlags(n11, edge11_14, edge10_11));

        costsFlags = tcStorage.getTurnCostFlags(n11, edge10_11, edge11_14);
        assertFalse(carEncoder.isTurnRestricted(costsFlags));
        assertTrue(bikeEncoder.isTurnRestricted(costsFlags));
    }

    @Test
    public void testEstimatedCenter()
    {
        final CarFlagEncoder encoder = new CarFlagEncoder()
        {
            private EncodedValue objectEncoder;

            @Override
            public int defineNodeBits( int index, int shift )
            {
                shift = super.defineNodeBits(index, shift);
                objectEncoder = new EncodedValue("oEnc", shift, 2, 1, 0, 3, true);
                return shift + 2;
            }

            @Override
            public long handleNodeTags( OSMNode node )
            {
                if (node.hasTag("test", "now"))
                    return -objectEncoder.setValue(0, 1);
                return 0;
            }
        };
        EncodingManager manager = new EncodingManager(encoder);
        GraphStorage graph = newGraph(dir, manager, false, false);
        final Map<Integer, Double> latMap = new HashMap<Integer, Double>();
        final Map<Integer, Double> lonMap = new HashMap<Integer, Double>();
        latMap.put(1, 1.1d);
        latMap.put(2, 1.2d);

        lonMap.put(1, 1.0d);
        lonMap.put(2, 1.0d);
        final AtomicInteger increased = new AtomicInteger(0);
        OSMReader osmreader = new OSMReader(graph)
        {
            // mock data access
            @Override
            double getTmpLatitude( int id )
            {
                return latMap.get(id);
            }

            @Override
            double getTmpLongitude( int id )
            {
                return lonMap.get(id);
            }

            @Override
            Collection<EdgeIteratorState> addOSMWay( TLongList osmNodeIds, long wayFlags, long osmId )
            {
                return Collections.emptyList();
            }
        };
        osmreader.setEncodingManager(manager);
        // save some node tags for first node
        OSMNode osmNode = new OSMNode(1, 1.1d, 1.0d);
        osmNode.setTag("test", "now");
        osmreader.getNodeFlagsMap().put(1, encoder.handleNodeTags(osmNode));

        OSMWay way = new OSMWay(1L);
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
    public void testReadEleFromCustomOSM()
    {
        GraphHopper hopper = new GraphHopperTest("custom-osm-ele.xml")
        {
            @Override
            protected DataReader createReader( GraphStorage tmpGraph )
            {
                return initOSMReader(new OSMReader(tmpGraph)
                {
                    @Override
                    protected double getElevation( OSMNode node )
                    {
                        return node.getEle();
                    }
                });
            }
        }.setElevation(true).importOrLoad();

        Graph graph = hopper.getGraph();
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49);

        EdgeIteratorState edge = GHUtility.getEdge(graph, n20, n50);
        assertEquals(Helper.createPointList3D(52, 9, -10, 51.25, 9.43, 100, 49, 10, -30), edge.fetchWayGeometry(3));
    }

    @Test
    public void testReadEleFromDataProvider()
    {
        GraphHopper hopper = new GraphHopperTest("test-osm5.xml");
        // get N10E046.hgt.zip
        ElevationProvider provider = new SRTMProvider();
        provider.setCacheDir(new File("./files"));
        hopper.setElevationProvider(provider);
        hopper.importOrLoad();

        Graph graph = hopper.getGraph();
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
}
