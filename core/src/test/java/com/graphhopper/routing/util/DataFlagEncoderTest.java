package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.spatialrules.Polygon;
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
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class DataFlagEncoderTest {
    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final EncodingManager encodingManager;
    private final StringEncodedValue roadClassEnc;
    private final double DELTA = 0.1;
    private final StringEncodedValue surfaceEnc;
    private final IntEncodedValue accessClassEnc;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue maxSpeedEnc;
    private final StringEncodedValue roadEnvEnc;

    public DataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", true);
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(Arrays.asList(encoder), 8).build();
        roadClassEnc = encodingManager.getStringEncodedValue(TagParserFactory.ROAD_CLASS);
        // misusing average as max speed
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(encoder.getPrefix() + "average_speed");
        surfaceEnc = encodingManager.getStringEncodedValue(TagParserFactory.SURFACE);
        accessClassEnc = encodingManager.getIntEncodedValue("access_class");
        accessEnc = encodingManager.getBooleanEncodedValue(encoder.getPrefix() + "access");
        roadEnvEnc = encodingManager.getStringEncodedValue(TagParserFactory.ROAD_ENVIRONMENT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsufficientEncoderBitLength() {
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(Arrays.asList(new DataFlagEncoder(properties)), 4).build();
    }

    @Test
    public void testSufficientEncoderBitLength() {
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(Arrays.asList(new DataFlagEncoder(properties)), 8).build();
        EncodingManager em1 = new EncodingManager.Builder().addGlobalEncodedValues().addAll(Arrays.asList(new DataFlagEncoder()), 4).build();
    }

    @Test
    public void testHighway() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("surface", "sand");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc));
        assertEquals("sand", edge.get(surfaceEnc));
        assertEquals("tunnel", edge.get(roadEnvEnc));
        assertTrue(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("_default", edge.get(roadClassEnc));
    }

    @Test
    public void testTunnel() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc));
        assertEquals("tunnel", edge.get(roadEnvEnc));
        assertTrue(encoder.isTunnel(edge.getData()));
        assertFalse(encoder.isBridge(edge.getData()));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("bridge", "yes");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", edge.get(roadEnvEnc));
        assertFalse(encoder.isTunnel(edge.getData()));
        assertTrue(encoder.isBridge(edge.getData()));
    }

    @Test
    public void testBridge() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc));
        assertEquals("bridge", edge.get(roadEnvEnc));
        assertFalse(encoder.isTunnel(edge.getData()));
        assertTrue(encoder.isBridge(edge.getData()));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        osmWay.setTag("tunnel", "yes");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", edge.get(roadEnvEnc));
        assertFalse(encoder.isTunnel(edge.getData()));
        assertTrue(encoder.isBridge(edge.getData()));
    }

    @Test
    public void testFord() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unclassified");
        osmWay.setTag("ford", "yes");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("ford", edge.get(roadEnvEnc));
        assertTrue(encoder.isFord(edge.getData()));
        assertTrue(encoder.getAnnotation(edge.getData(), TranslationMapTest.SINGLETON.get("en")).getMessage().contains("ford"));
    }

    @Test
    public void testHighwaySpeed() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("motorway", 100d);
        map.put("motorway_link", 100d);
        map.put("motorroad", 90d);
        map.put("trunk", 90d);
        map.put("trunk_link", 90d);

        double[] arr = encoder.getHighwaySpeedMap(map);
        assertEquals(100, arr[roadClassEnc.indexOf("motorway")], .1);
        assertEquals(90, arr[roadClassEnc.indexOf("trunk")], .1);
        assertEquals(0, arr[roadClassEnc.indexOf("secondary")], .1);
    }

    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        IntsRef ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        int idx = accessClassEnc.getInt(false, ints);
        assertEquals(SpatialRule.Access.YES, SpatialRule.Access.values()[idx]);

        way.setTag("vehicle", "destination");
        ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        idx = accessClassEnc.getInt(false, ints);
        assertEquals(SpatialRule.Access.CONDITIONAL, SpatialRule.Access.values()[idx]);

        way.setTag("vehicle", "no");
        ints = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        idx = accessClassEnc.getInt(false, ints);
        assertEquals(SpatialRule.Access.NO, SpatialRule.Access.values()[idx]);
    }

    @Test
    public void testMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(maxSpeedEnc), .1);
        assertEquals(10, edge.getReverse(maxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(maxSpeedEnc), .1);
        assertEquals(0, edge.getReverse(maxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "50");
        osmWay.setTag("maxspeed:backward", "50");
        osmWay.setTag("maxspeed", "60");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(50, edge.get(maxSpeedEnc), .1);
        assertEquals(50, edge.getReverse(maxSpeedEnc), .1);
    }

    @Test
    public void testLargeMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "145");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, edge.get(maxSpeedEnc), .1);
        assertEquals(140, edge.getReverse(maxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, edge.get(maxSpeedEnc), .1);
        assertEquals(140, edge.getReverse(maxSpeedEnc), .1);
    }

    @Test
    public void reverseEdge() {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), osmWay, new EncodingManager.AcceptWay(), 0);
        edge.setData(flags);

        assertEquals(10, edge.get(maxSpeedEnc), .1);
        assertEquals(0, edge.getReverse(maxSpeedEnc), .1);

        edge = edge.detach(true);
        assertEquals(0, edge.get(maxSpeedEnc), .1);
        assertEquals(10, edge.getReverse(maxSpeedEnc), .1);
    }

    @Test
    public void setAccess() {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);

        edge.set(accessEnc, true);
        edge.setReverse(accessEnc, true);
        assertTrue(accessEnc.getBool(false, edge.getData()));
        assertTrue(accessEnc.getBool(true, edge.getData()));

        edge.set(accessEnc, true);
        edge.setReverse(accessEnc, false);
        assertTrue(accessEnc.getBool(false, edge.getData()));
        assertFalse(accessEnc.getBool(true, edge.getData()));

        edge = edge.detach(true);
        assertFalse(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        edge = edge.detach(true);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        edge.set(accessEnc, false);
        edge.setReverse(accessEnc, false);
        assertFalse(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));
    }

    @Test
    public void acceptWay() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        assertTrue(encoder.getAccess(osmWay).isWay());

        // important to filter out illegal highways to reduce the number of edges before adding them to the graph
        osmWay.setTag("highway", "building");
        assertTrue(encoder.getAccess(osmWay).canSkip());
    }

    @Test
    public void stringToMeter() {
        assertEquals(1.5, TagParserFactory.stringToMeter("1.5"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("1.5m"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("1.5 m"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("1.5   m"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("1.5 meter"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("4 ft 11 in"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToMeter("4'11''"), DELTA);


        assertEquals(3, TagParserFactory.stringToMeter("3 m."), DELTA);
        assertEquals(3, TagParserFactory.stringToMeter("3meters"), DELTA);
        assertEquals(0.8 * 3, TagParserFactory.stringToMeter("~3"), DELTA);
        assertEquals(3 * 0.8, TagParserFactory.stringToMeter("3 m approx"), DELTA);

        // 2.743 + 0.178
        assertEquals(2.921, TagParserFactory.stringToMeter("9 ft 7in"), DELTA);
        assertEquals(2.921, TagParserFactory.stringToMeter("9'7\""), DELTA);
        assertEquals(2.921, TagParserFactory.stringToMeter("9'7''"), DELTA);
        assertEquals(2.921, TagParserFactory.stringToMeter("9' 7\""), DELTA);

        assertEquals(2.743, TagParserFactory.stringToMeter("9'"), DELTA);
        assertEquals(2.743, TagParserFactory.stringToMeter("9 feet"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToMeterException() {
        // Unexpected values
        TagParserFactory.stringToMeter("height limit 1.5m");
    }

    @Test
    public void stringToTons() {
        assertEquals(1.5, TagParserFactory.stringToTons("1.5"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToTons("1.5 t"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToTons("1.5   t"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToTons("1.5 tons"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToTons("1.5 ton"), DELTA);
        assertEquals(1.5, TagParserFactory.stringToTons("3306.9 lbs"), DELTA);
        assertEquals(3, TagParserFactory.stringToTons("3 T"), DELTA);
        assertEquals(3, TagParserFactory.stringToTons("3ton"), DELTA);

        // maximum gross weight
        assertEquals(6, TagParserFactory.stringToTons("6t mgw"), DELTA);
    }

    @Test(expected = NumberFormatException.class)
    public void stringToTonsException() {
        // Unexpected values
        TagParserFactory.stringToTons("weight limit 1.5t");
    }

    @Test
    public void testSpatialRuleId() {
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
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(encoder).build();
        BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(encoder.getPrefix() + "access");
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(encoder.getPrefix() + "average_speed");
        IntEncodedValue spatialIdEnc = em.getIntEncodedValue(TagParserFactory.SPATIAL_RULE_ID);

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
        EdgeIteratorState e1 = GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        EdgeIteratorState e2 = GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 2, true, 1);
        EdgeIteratorState e3 = GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 3, true, 1);
        EdgeIteratorState e4 = GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 4, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, -0.01, -0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, -0.01, -0.01);

        e1.setData(em.handleWayTags(em.createIntsRef(), way, new EncodingManager.AcceptWay(), 0));
        e2.setData(em.handleWayTags(em.createIntsRef(), way2, new EncodingManager.AcceptWay(), 0));
        e3.setData(em.handleWayTags(em.createIntsRef(), livingStreet, new EncodingManager.AcceptWay(), 0));
        e4.setData(em.handleWayTags(em.createIntsRef(), livingStreet2, new EncodingManager.AcceptWay(), 0));

        assertEquals(index.getSpatialId(new GermanySpatialRule()), e1.get(spatialIdEnc));
        assertEquals(index.getSpatialId(SpatialRule.EMPTY), e2.get(spatialIdEnc));

        assertEquals(SpatialRule.Access.CONDITIONAL, encoder.getAccess(e1));
        assertEquals(SpatialRule.Access.YES, encoder.getAccess(e2));

        assertEquals(5, e3.get(maxSpeedEnc), .1);
        assertEquals(0, e4.get(maxSpeedEnc), .1);
    }
}
