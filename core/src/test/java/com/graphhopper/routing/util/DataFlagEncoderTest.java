package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.spatialrules.AccessValue;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMapTest;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.Polygon;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class DataFlagEncoderTest {
    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final BooleanEncodedValue accessEnc;
    private final EncodingManager encodingManager;
    private final int motorVehicleInt;
    private final ObjectEncodedValue roadClassEnc;
    private final ObjectEncodedValue roadEnvEnc;
    private final double DELTA = 0.1;

    public DataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", true);
        encoder = new DataFlagEncoder(properties);
        encodingManager = EncodingManager.start(8).add(encoder).build();
        accessEnc = encoder.getAccessEnc();
        roadClassEnc = encodingManager.getObjectEncodedValue(EncodingManager.ROAD_CLASS);
        roadEnvEnc = encodingManager.getObjectEncodedValue(EncodingManager.ROAD_ENV);
        motorVehicleInt = encoder.getAccessType("motor_vehicle");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsufficientEncoderBitLength() {
        EncodingManager em = EncodingManager.create(Arrays.asList(new DataFlagEncoder(properties)));
    }

    @Test
    public void testSufficientEncoderBitLength() {
        try {
            EncodingManager em = EncodingManager.create(Arrays.asList(new DataFlagEncoder(properties)), 8);
            EncodingManager em1 = EncodingManager.create(Arrays.asList(new DataFlagEncoder()));
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void testHighway() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("surface", "sand");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadClass.PRIMARY, edge.get(roadClassEnc));
        assertEquals(Surface.SAND, edge.get(encoder.getObjectEncodedValue("surface")));
        assertEquals(RoadEnvironment.TUNNEL, edge.get(roadEnvEnc));
        assertTrue(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadClass.DEFAULT, edge.get(roadClassEnc));
    }

    @Test
    public void testTunnel() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadClass.PRIMARY, edge.get(roadClassEnc));
        assertEquals(RoadEnvironment.TUNNEL, edge.get(roadEnvEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("bridge", "yes");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadEnvironment.BRIDGE, edge.get(roadEnvEnc));
    }

    @Test
    public void testBridge() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadClass.PRIMARY, edge.get(roadClassEnc));
        assertEquals(RoadEnvironment.BRIDGE, edge.get(roadEnvEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        osmWay.setTag("tunnel", "yes");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadEnvironment.BRIDGE, edge.get(roadEnvEnc));
    }

    @Test
    public void testFord() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unclassified");
        osmWay.setTag("ford", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(RoadEnvironment.FORD, edge.get(roadEnvEnc));
        String msg = encoder.getAnnotation(edge.getFlags(), TranslationMapTest.SINGLETON.get("en")).getMessage();
        assertTrue(msg, msg.contains("ford"));
    }

    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        IntsRef intsref = encodingManager.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(AccessValue.ACCESSIBLE, encoder.getAccessValue(intsref));

        way.setTag("vehicle", "destination");
        intsref = encodingManager.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, encoder.getAccessValue(intsref));

        way.setTag("vehicle", "no");
        intsref = encodingManager.handleWayTags(way, encoder.acceptWay(way), 0);
        assertEquals(AccessValue.NOT_ACCESSIBLE, encoder.getAccessValue(intsref));
    }

    @Test
    public void testMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(-1, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "50");
        osmWay.setTag("maxspeed:backward", "50");
        osmWay.setTag("maxspeed", "60");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(50, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(50, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);
    }

    @Test
    public void testLargeMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "145");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);
    }

    @Test
    public void reverseEdge() {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        IntsRef flags = encodingManager.handleWayTags(osmWay, 1, 0);
        edge.setFlags(flags);

        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(-1, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        edge = edge.detach(true);
        assertEquals(-1, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);
    }

    @Test
    public void setAccess() {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);

        edge.set(accessEnc, true).setReverse(accessEnc, true);
        edge.setFlags(edge.getFlags());
        assertTrue(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        edge.set(accessEnc, true).setReverse(accessEnc, false);
        edge.setFlags(edge.getFlags());
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        edge = edge.detach(true);
        assertFalse(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));
        edge = edge.detach(true);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        edge.set(accessEnc, false).setReverse(accessEnc, false);
        edge.setFlags(edge.getFlags());
        assertFalse(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));
    }

    @Test
    public void acceptWay() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        assertTrue(encoder.acceptWay(osmWay) != 0);

        // important to filter out illegal highways to reduce the number of edges before adding them to the graph
        osmWay.setTag("highway", "building");
        assertTrue(encoder.acceptWay(osmWay) == 0);
    }

    @Test
    public void stringToMeter() {
        assertEquals(1.5, DataFlagEncoder.stringToMeter("1.5"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("1.5m"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("1.5 m"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("1.5   m"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("1.5 meter"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("4 ft 11 in"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToMeter("4'11''"), DELTA);


        assertEquals(3, DataFlagEncoder.stringToMeter("3 m."), DELTA);
        assertEquals(3, DataFlagEncoder.stringToMeter("3meters"), DELTA);
        assertEquals(0.8 * 3, DataFlagEncoder.stringToMeter("~3"), DELTA);
        assertEquals(3 * 0.8, DataFlagEncoder.stringToMeter("3 m approx"), DELTA);

        // 2.743 + 0.178
        assertEquals(2.921, DataFlagEncoder.stringToMeter("9 ft 7in"), DELTA);
        assertEquals(2.921, DataFlagEncoder.stringToMeter("9'7\""), DELTA);
        assertEquals(2.921, DataFlagEncoder.stringToMeter("9'7''"), DELTA);
        assertEquals(2.921, DataFlagEncoder.stringToMeter("9' 7\""), DELTA);

        assertEquals(2.743, DataFlagEncoder.stringToMeter("9'"), DELTA);
        assertEquals(2.743, DataFlagEncoder.stringToMeter("9 feet"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToMeterException() {
        // Unexpected values
        DataFlagEncoder.stringToMeter("height limit 1.5m");
    }

    @Test
    public void stringToTons() {
        assertEquals(1.5, DataFlagEncoder.stringToTons("1.5"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToTons("1.5 t"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToTons("1.5   t"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToTons("1.5 tons"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToTons("1.5 ton"), DELTA);
        assertEquals(1.5, DataFlagEncoder.stringToTons("3306.9 lbs"), DELTA);
        assertEquals(3, DataFlagEncoder.stringToTons("3 T"), DELTA);
        assertEquals(3, DataFlagEncoder.stringToTons("3ton"), DELTA);

        // maximum gross weight
        assertEquals(6, DataFlagEncoder.stringToTons("6t mgw"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToTonsException() {
        // Unexpected values
        DataFlagEncoder.stringToTons("weight limit 1.5t");
    }

    @Test
    public void testSpatialId() {
        final GermanySpatialRule germany = new GermanySpatialRule();
        germany.setBorders(Collections.singletonList(new Polygon(new double[]{0, 0, 1, 1}, new double[]{0, 1, 1, 0})));

        SpatialRuleLookup index = new SpatialRuleLookup() {
            @Override
            public SpatialRule lookupRule(double lat, double lon) {
                for (Polygon polygon : germany.getBorders()) {
                    if (polygon.contains(lat, lon)) {
                        return germany;
                    }
                }
                return SpatialRule.EMPTY;
            }

            @Override
            public SpatialRule lookupRule(GHPoint point) {
                return lookupRule(point.lat, point.lon);
            }

            @Override
            public int getSpatialId(SpatialRule rule) {
                if (germany.equals(rule)) {
                    return 1;
                } else {
                    return 0;
                }
            }

            @Override
            public int size() {
                return 2;
            }

            @Override
            public BBox getBounds() {
                return new BBox(-180, 180, -90, 90);
            }
        };

        DataFlagEncoder encoder = new DataFlagEncoder(new PMap());
        encoder.setSpatialRuleLookup(index);
        EncodingManager em = EncodingManager.create(encoder);

        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "track");
        way.setTag("estimated_center", new GHPoint(0.005, 0.005));

        ReaderWay way2 = new ReaderWay(28l);
        way2.setTag("highway", "track");
        way2.setTag("estimated_center", new GHPoint(-0.005, -0.005));

        ReaderWay livingStreet = new ReaderWay(29l);
        livingStreet.setTag("highway", "living_street");
        livingStreet.setTag("estimated_center", new GHPoint(0.005, 0.005));

        ReaderWay livingStreet2 = new ReaderWay(30l);
        livingStreet2.setTag("highway", "living_street");
        livingStreet2.setTag("estimated_center", new GHPoint(-0.005, -0.005));

        Graph graph = new GraphBuilder(em).create();
        EdgeIteratorState e1 = graph.edge(0, 1, 1, true);
        EdgeIteratorState e2 = graph.edge(0, 2, 1, true);
        EdgeIteratorState e3 = graph.edge(0, 3, 1, true);
        EdgeIteratorState e4 = graph.edge(0, 4, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, -0.01, -0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, -0.01, -0.01);

        e1.setFlags(encoder.handleWayTags(em.createEdgeFlags(), way, 1, 0));
        e2.setFlags(encoder.handleWayTags(em.createEdgeFlags(), way2, 1, 0));
        e3.setFlags(encoder.handleWayTags(em.createEdgeFlags(), livingStreet, 1, 0));
        e4.setFlags(encoder.handleWayTags(em.createEdgeFlags(), livingStreet2, 1, 0));

        assertEquals(index.getSpatialId(new GermanySpatialRule()), encoder.getSpatialId(e1.getFlags()));
        assertEquals(index.getSpatialId(SpatialRule.EMPTY), encoder.getSpatialId(e2.getFlags()));

        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, encoder.getAccessValue(e1.getFlags()));
        assertEquals(AccessValue.ACCESSIBLE, encoder.getAccessValue(e2.getFlags()));

        assertEquals(5, encoder.getMaxspeed(e3, -1, false), .1);
        assertEquals(-1, encoder.getMaxspeed(e4, -1, false), .1);
    }

}
