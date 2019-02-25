package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.parsers.*;
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
    private final EncodingManager.AcceptWay map;
    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final BooleanEncodedValue accessEnc;
    private final ObjectEncodedValue roadAccessEnc;
    private final ObjectEncodedValue roadEnvironmentEnc;
    private final ObjectEncodedValue roadClassEnc;
    private final ObjectEncodedValue surfaceEnc;
    private final DecimalEncodedValue carMaxSpeedEnc;
    private final EncodingManager encodingManager;

    private final double DELTA = 0.1;

    public DataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", true);
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager.Builder(8).
                add(new OSMRoadEnvironmentParser(roadEnvironmentEnc = RoadEnvironment.create())).
                add(new OSMRoadClassParser(roadClassEnc = RoadClass.create())).
                add(new OSMRoadAccessParser(roadAccessEnc = RoadAccess.create())).
                add(new OSMSurfaceParser(surfaceEnc = Surface.create())).
                add(new OSMCarMaxSpeedParser(carMaxSpeedEnc = CarMaxSpeed.create())).
                add(encoder).build();
        map = new EncodingManager.AcceptWay().put(encoder.toString(), EncodingManager.Access.WAY);
        accessEnc = encoder.getAccessEnc();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoDefaultEncodedValues() {
        EncodingManager em = EncodingManager.create(Arrays.asList(new DataFlagEncoder(properties)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsufficientEncoderBitLength() {
        EncodingManager em1 = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(4)).add(new DataFlagEncoder(properties)).build();
    }

    @Test
    public void testSufficientEncoderBitLength() {
        try {
            EncodingManager em = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(8)).add(new DataFlagEncoder(properties)).build();
            EncodingManager em1 = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(12)).add(new DataFlagEncoder(properties)).build();
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    @Test
    public void testHighway() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("surface", "sand");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("sand", edge.get(surfaceEnc).toString());
        assertEquals("tunnel", edge.get(roadEnvironmentEnc).toString());
        assertTrue(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("other", edge.get(roadClassEnc).toString());
    }

    @Test
    public void testTunnel() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("tunnel", edge.get(roadEnvironmentEnc).toString());
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("bridge", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", edge.get(roadEnvironmentEnc).toString());
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);
    }

    @Test
    public void testBridge() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("bridge", edge.get(roadEnvironmentEnc).toString());
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        osmWay.setTag("tunnel", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", edge.get(roadEnvironmentEnc).toString());
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);
    }

    @Test
    public void testFord() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unclassified");
        osmWay.setTag("ford", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("ford", edge.get(roadEnvironmentEnc).toString());
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.FORD);
        assertTrue(encoder.getAnnotation(edge.getFlags(), TranslationMapTest.SINGLETON.get("en")).getMessage().contains("ford"));
    }

    @Test
    public void testHighwaySpeed() {
        PMap map = new PMap();
        map.put("motorway", 100d);
        map.put("motorway_link", 100d);
        map.put("motorroad", 90d);
        map.put("trunk", 90d);
        map.put("trunk_link", 90d);

        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, encodingManager.createEdgeFlags());
        DataFlagEncoder.WeightingConfig config = encoder.createWeightingConfig(map);
        roadClassEnc.setObject(false, edge.getFlags(), RoadClass.MOTORWAY);
        assertEquals(100, config.getSpeed(edge), 1);

        roadClassEnc.setObject(false, edge.getFlags(), RoadClass.TRUNK);
        assertEquals(90, config.getSpeed(edge), 1);
    }

    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        IntsRef intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.UNLIMITED, roadAccessEnc.getObject(false, intsref));

        way.setTag("vehicle", "destination");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.DESTINATION, roadAccessEnc.getObject(false, intsref));

        way.setTag("vehicle", "no");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.NO, roadAccessEnc.getObject(false, intsref));
    }

    @Test
    public void testMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(carMaxSpeedEnc), .1);
        assertEquals(10, edge.getReverse(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(carMaxSpeedEnc), .1);
        assertEquals(0, edge.getReverse(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "50");
        osmWay.setTag("maxspeed:backward", "50");
        osmWay.setTag("maxspeed", "60");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(50, edge.get(carMaxSpeedEnc), .1);
        assertEquals(50, edge.getReverse(carMaxSpeedEnc), .1);
    }

    @Test
    public void testLargeMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "145");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, edge.get(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, edge.get(carMaxSpeedEnc), .1);
    }

    @Test
    public void reverseEdge() {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge.setFlags(flags);

        assertEquals(10, edge.get(carMaxSpeedEnc), .1);
        assertEquals(0, edge.getReverse(carMaxSpeedEnc), .1);

        edge = edge.detach(true);
        assertEquals(0, edge.get(carMaxSpeedEnc), .1);
        assertEquals(10, edge.getReverse(carMaxSpeedEnc), .1);
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
        assertTrue(encoder.getAccess(osmWay).isWay());

        // important to filter out illegal highways to reduce the number of edges before adding them to the graph
        osmWay.setTag("highway", "building");
        assertTrue(encoder.getAccess(osmWay).canSkip());
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
        EncodingManager em = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(4)).add(encoder).build();

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

        e1.setFlags(em.handleWayTags(way, map, 0));
        e2.setFlags(em.handleWayTags(way2, map, 0));
        e3.setFlags(em.handleWayTags(livingStreet, map, 0));
        e4.setFlags(em.handleWayTags(livingStreet2, map, 0));

        assertEquals(index.getSpatialId(new GermanySpatialRule()), encoder.getSpatialId(e1.getFlags()));
        assertEquals(index.getSpatialId(SpatialRule.EMPTY), encoder.getSpatialId(e2.getFlags()));

        assertEquals(SpatialRule.Access.CONDITIONAL, e1.get(roadAccessEnc));
        assertEquals(SpatialRule.Access.YES, e2.get(roadAccessEnc));

        assertEquals(5, e3.get(carMaxSpeedEnc), .1);
        assertEquals(-1, e4.get(carMaxSpeedEnc), .1);
    }
}
