package com.graphhopper.routing.util;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.Country;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.MaxSpeed;
import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.routing.profiles.Surface;
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.routing.util.parsers.OSMRoadClassParser;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.routing.util.parsers.OSMSurfaceParser;
import com.graphhopper.routing.util.parsers.SpatialRuleParser;
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

/**
 * @author Peter Karich
 */
public class DataFlagEncoderTest {
    private final EncodingManager.AcceptWay map;
    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final BooleanEncodedValue accessEnc;
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    private final EnumEncodedValue<RoadEnvironment> roadEnvironmentEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<Surface> surfaceEnc;
    private final DecimalEncodedValue carMaxSpeedEnc;
    private final EncodingManager encodingManager;

    public DataFlagEncoderTest() {
        properties = new PMap();
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager.Builder(8).
                add(new OSMRoadEnvironmentParser()).
                add(new OSMRoadClassParser()).
                add(new OSMRoadAccessParser()).
                add(new OSMSurfaceParser()).
                add(new OSMMaxSpeedParser(carMaxSpeedEnc = MaxSpeed.create())).
                add(encoder).build();
        roadEnvironmentEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        roadAccessEnc = encodingManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        surfaceEnc = encodingManager.getEnumEncodedValue(Surface.KEY, Surface.class);
        map = new EncodingManager.AcceptWay().put(encoder.toString(), EncodingManager.Access.WAY);
        accessEnc = encoder.getAccessEnc();
    }

    @Test(expected = IllegalStateException.class)
    public void testNoDefaultEncodedValues() {
        EncodingManager em = EncodingManager.create(Arrays.asList(new DataFlagEncoder(properties)));
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
        roadClassEnc.setEnum(false, edge.getFlags(), RoadClass.MOTORWAY);
        assertEquals(100, config.getSpeed(edge), 1);

        roadClassEnc.setEnum(false, edge.getFlags(), RoadClass.TRUNK);
        assertEquals(90, config.getSpeed(edge), 1);
    }

    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        IntsRef intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.YES, roadAccessEnc.getEnum(false, intsref));

        way.setTag("vehicle", "destination");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.DESTINATION, roadAccessEnc.getEnum(false, intsref));

        way.setTag("vehicle", "no");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, 0);
        assertEquals(RoadAccess.NO, roadAccessEnc.getEnum(false, intsref));
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
        assertEquals(Double.POSITIVE_INFINITY, edge.getReverse(carMaxSpeedEnc), .1);

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
        osmWay.setTag("maxspeed", "170");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(MaxSpeed.UNLIMITED_SIGN_SPEED, edge.get(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encodingManager.handleWayTags(osmWay, map, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(MaxSpeed.UNLIMITED_SIGN_SPEED, edge.get(carMaxSpeedEnc), .1);
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
        assertEquals(Double.POSITIVE_INFINITY, edge.getReverse(carMaxSpeedEnc), .1);

        edge = edge.detach(true);
        assertEquals(Double.POSITIVE_INFINITY, edge.get(carMaxSpeedEnc), .1);
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
            public SpatialRule getSpatialRule(int spatialId) {
                return SpatialRule.EMPTY;
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

        DataFlagEncoder tmpEncoder = new DataFlagEncoder(new PMap());
        EncodingManager em = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(4).add(new SpatialRuleParser(index))).add(tmpEncoder).build();
        IntEncodedValue countrySpatialIdEnc = em.getIntEncodedValue(Country.KEY);
        EnumEncodedValue<RoadAccess> tmpRoadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        DecimalEncodedValue tmpCarMaxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);

        Graph graph = new GraphBuilder(em).create();
        EdgeIteratorState e1 = graph.edge(0, 1, 1, true);
        EdgeIteratorState e2 = graph.edge(0, 2, 1, true);
        EdgeIteratorState e3 = graph.edge(0, 3, 1, true);
        EdgeIteratorState e4 = graph.edge(0, 4, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        updateDistancesFor(graph, 2, -0.01, -0.01);
        updateDistancesFor(graph, 3, 0.01, 0.01);
        updateDistancesFor(graph, 4, -0.01, -0.01);

        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "track");
        way.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e1.setFlags(em.handleWayTags(way, map, 0));
        assertEquals(RoadAccess.DESTINATION, e1.get(tmpRoadAccessEnc));

        ReaderWay way2 = new ReaderWay(28l);
        way2.setTag("highway", "track");
        way2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e2.setFlags(em.handleWayTags(way2, map, 0));
        assertEquals(RoadAccess.YES, e2.get(tmpRoadAccessEnc));

        assertEquals(index.getSpatialId(new GermanySpatialRule()), e1.get(countrySpatialIdEnc));
        assertEquals(index.getSpatialId(SpatialRule.EMPTY), e2.get(countrySpatialIdEnc));

        ReaderWay livingStreet = new ReaderWay(29l);
        livingStreet.setTag("highway", "living_street");
        livingStreet.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e3.setFlags(em.handleWayTags(livingStreet, map, 0));
        assertEquals(5, e3.get(tmpCarMaxSpeedEnc), .1);

        ReaderWay livingStreet2 = new ReaderWay(30l);
        livingStreet2.setTag("highway", "living_street");
        livingStreet2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e4.setFlags(em.handleWayTags(livingStreet2, map, 0));
        assertEquals(MaxSpeed.UNSET_SPEED, e4.get(tmpCarMaxSpeedEnc), .1);
    }
}
