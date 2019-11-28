package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser;
import com.graphhopper.routing.util.parsers.OSMSurfaceParser;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMapTest;
import org.junit.Test;

import static org.junit.Assert.*;

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
    private final IntsRef relFlags;

    public DataFlagEncoderTest() {
        properties = new PMap();
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager.Builder().
                add(new OSMSurfaceParser()).
                add(new OSMMaxSpeedParser(carMaxSpeedEnc = MaxSpeed.create())).
                add(encoder).build();
        relFlags = encodingManager.createRelationFlags();
        roadEnvironmentEnc = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        roadAccessEnc = encodingManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        surfaceEnc = encodingManager.getEnumEncodedValue(Surface.KEY, Surface.class);
        map = new EncodingManager.AcceptWay().put(encoder.toString(), EncodingManager.Access.WAY);
        accessEnc = encoder.getAccessEnc();
    }

    @Test
    public void testSufficientEncoderBitLength() {
        try {
            EncodingManager em = new EncodingManager.Builder().add(new DataFlagEncoder(properties)).build();
            EncodingManager em1 = new EncodingManager.Builder().add(new DataFlagEncoder(properties)).build();
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
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("sand", edge.get(surfaceEnc).toString());
        assertEquals("tunnel", edge.get(roadEnvironmentEnc).toString());
        assertTrue(edge.get(accessEnc));
        assertTrue(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(edge.get(accessEnc));
        assertFalse(edge.getReverse(accessEnc));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("other", edge.get(roadClassEnc).toString());
    }

    @Test
    public void testTunnel() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("tunnel", edge.get(roadEnvironmentEnc).toString());
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("bridge", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
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
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", edge.get(roadClassEnc).toString());
        assertEquals("bridge", edge.get(roadEnvironmentEnc).toString());
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        osmWay.setTag("tunnel", "yes");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", edge.get(roadEnvironmentEnc).toString());
        assertFalse(edge.get(roadEnvironmentEnc) == RoadEnvironment.TUNNEL);
        assertTrue(edge.get(roadEnvironmentEnc) == RoadEnvironment.BRIDGE);
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
        IntsRef intsref = encodingManager.handleWayTags(way, map, relFlags);
        assertEquals(RoadAccess.YES, roadAccessEnc.getEnum(false, intsref));

        way.setTag("vehicle", "destination");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, relFlags);
        assertEquals(RoadAccess.DESTINATION, roadAccessEnc.getEnum(false, intsref));

        way.setTag("vehicle", "no");
        map = new EncodingManager.AcceptWay().put(encoder.toString(), encoder.getAccess(way));
        intsref = encodingManager.handleWayTags(way, map, relFlags);
        assertEquals(RoadAccess.NO, roadAccessEnc.getEnum(false, intsref));
    }

    @Test
    public void testMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(carMaxSpeedEnc), .1);
        assertEquals(10, edge.getReverse(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, edge.get(carMaxSpeedEnc), .1);
        assertEquals(Double.POSITIVE_INFINITY, edge.getReverse(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "50");
        osmWay.setTag("maxspeed:backward", "50");
        osmWay.setTag("maxspeed", "60");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(50, edge.get(carMaxSpeedEnc), .1);
        assertEquals(50, edge.getReverse(carMaxSpeedEnc), .1);
    }

    @Test
    public void testLargeMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "170");
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(MaxSpeed.UNLIMITED_SIGN_SPEED, edge.get(carMaxSpeedEnc), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encodingManager.handleWayTags(osmWay, map, relFlags);
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
        IntsRef flags = encodingManager.handleWayTags(osmWay, map, relFlags);
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
}
