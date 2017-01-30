package com.graphhopper.routing.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.graphhopper.util.PMap;
import org.junit.Ignore;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class DataFlagEncoderTest {
    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final EncodingManager encodingManager;
    private final int motorVehicleInt;

    private final double DELTA = 0.1;

    public DataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", true);
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager(Arrays.asList(encoder), 8);

        motorVehicleInt = encoder.getAccessType("motor_vehicle");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsufficientEncoderBitLength() {
        EncodingManager em = new EncodingManager(Arrays.asList(new DataFlagEncoder(properties)));
    }

    @Test
    public void testSufficientEncoderBitLength() {
        try {
            EncodingManager em = new EncodingManager(Arrays.asList(new DataFlagEncoder(properties)), 8);
            EncodingManager em1 = new EncodingManager(Arrays.asList(new DataFlagEncoder()));
        }
        catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void testHighway() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("surface", "sand");
        osmWay.setTag("tunnel", "yes");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", encoder.getHighwayAsString(edge));
        assertEquals("sand", encoder.getSurfaceAsString(edge));
        assertEquals("tunnel", encoder.getTransportModeAsString(edge));
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertTrue(encoder.isBackward(edge, motorVehicleInt));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertFalse(encoder.isBackward(edge, motorVehicleInt));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("_default", encoder.getHighwayAsString(edge));
    }

    @Test
    public void testTunnel() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", encoder.getHighwayAsString(edge));
        assertEquals("tunnel", encoder.getTransportModeAsString(edge));
        assertTrue(encoder.isTransportModeTunnel(edge));
        assertFalse(encoder.isTransportModeBridge(edge));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("tunnel", "yes");
        osmWay.setTag("bridge", "yes");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", encoder.getTransportModeAsString(edge));
        assertFalse(encoder.isTransportModeTunnel(edge));
        assertTrue(encoder.isTransportModeBridge(edge));
    }

    @Test
    public void testBridge() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("primary", encoder.getHighwayAsString(edge));
        assertEquals("bridge", encoder.getTransportModeAsString(edge));
        assertFalse(encoder.isTransportModeTunnel(edge));
        assertTrue(encoder.isTransportModeBridge(edge));

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("bridge", "yes");
        osmWay.setTag("tunnel", "yes");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("bridge", encoder.getTransportModeAsString(edge));
        assertFalse(encoder.isTransportModeTunnel(edge));
        assertTrue(encoder.isTransportModeBridge(edge));
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
        assertEquals("[0.0, 100.0, 100.0, 90.0, 90.0, 90.0]", Helper.createDoubleList(arr).subList(0, 6).toString());
    }

    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        assertEquals(DataFlagEncoder.AccessValue.ACCESSIBLE, encoder.getEdgeAccessValue(encoder.handleWayTags(way, encoder.acceptWay(way), 0)));

        way.setTag("vehicle", "destination");
        assertEquals(DataFlagEncoder.AccessValue.EVENTUALLY_ACCESSIBLE, encoder.getEdgeAccessValue(encoder.handleWayTags(way, encoder.acceptWay(way), 0)));

        way.setTag("vehicle", "no");
        assertEquals(DataFlagEncoder.AccessValue.NOT_ACCESSIBLE, encoder.getEdgeAccessValue(encoder.handleWayTags(way, encoder.acceptWay(way), 0)));
    }

    @Test
    public void testMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(-1, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);
    }

    @Test
    public void testLargeMaxspeed() {
        ReaderWay osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "145");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(140, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new ReaderWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "1000");
        flags = encoder.handleWayTags(osmWay, 1, 0);
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
        long flags = encoder.handleWayTags(osmWay, 1, 0);
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

        edge.setFlags(encoder.setAccess(edge.getFlags(), true, true));
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertTrue(encoder.isBackward(edge, motorVehicleInt));

        edge.setFlags(encoder.setAccess(edge.getFlags(), true, false));
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertFalse(encoder.isBackward(edge, motorVehicleInt));

        edge = edge.detach(true);
        assertFalse(encoder.isForward(edge, motorVehicleInt));
        assertTrue(encoder.isBackward(edge, motorVehicleInt));
        edge = edge.detach(true);
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertFalse(encoder.isBackward(edge, motorVehicleInt));

        edge.setFlags(encoder.setAccess(edge.getFlags(), false, false));
        assertFalse(encoder.isForward(edge, motorVehicleInt));
        assertFalse(encoder.isBackward(edge, motorVehicleInt));
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
}
