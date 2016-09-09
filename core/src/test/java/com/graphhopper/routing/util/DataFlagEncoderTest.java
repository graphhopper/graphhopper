package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class DataFlagEncoderTest
{
    private final DataFlagEncoder encoder;
    private final EncodingManager encodingManager;
    private final int motorVehicleInt;

    public DataFlagEncoderTest()
    {
        encoder = new DataFlagEncoder();
        encodingManager = new EncodingManager(encoder);

        motorVehicleInt = encoder.getAccessType("motor_vehicle");
    }

    @Test
    public void testHighway()
    {
        OSMWay osmWay = new OSMWay(0);
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

        osmWay = new OSMWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("oneway", "yes");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertTrue(encoder.isForward(edge, motorVehicleInt));
        assertFalse(encoder.isBackward(edge, motorVehicleInt));

        osmWay = new OSMWay(0);
        osmWay.setTag("highway", "unknownX");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals("_default", encoder.getHighwayAsString(edge));
    }

    @Test
    public void testHighwaySpeed()
    {
        Map<String, Double> map = new HashMap<>();
        map.put("motorway", 100d);
        map.put("motorway_link", 100d);
        map.put("trunk", 90d);
        map.put("trunk_link", 90d);

        double[] arr = encoder.getHighwaySpeedMap(map);
        assertEquals("[0.0, 100.0, 100.0, 90.0, 90.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]", Arrays.toString(arr));
    }

    @Test
    public void testMaxspeed()
    {
        OSMWay osmWay = new OSMWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed", "10");
        long flags = encoder.handleWayTags(osmWay, 1, 0);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);

        osmWay = new OSMWay(0);
        osmWay.setTag("highway", "primary");
        osmWay.setTag("maxspeed:forward", "10");
        flags = encoder.handleWayTags(osmWay, 1, 0);
        edge = GHUtility.createMockedEdgeIteratorState(0, flags);
        assertEquals(10, encoder.getMaxspeed(edge, motorVehicleInt, false), .1);
        assertEquals(-1, encoder.getMaxspeed(edge, motorVehicleInt, true), .1);
    }

    @Test
    public void reverseEdge()
    {
        Graph graph = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        OSMWay osmWay = new OSMWay(0);
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
}
