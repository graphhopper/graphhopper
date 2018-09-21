package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.util.Helper;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RoadClassParserTest {

    @Test
    public void testHighwaySpeed() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("motorway", 100d);
        map.put("motorway_link", 100d);
        map.put("motorroad", 90d);
        map.put("trunk", 90d);
        map.put("trunk_link", 90d);

        RoadClassParser roadClassParser = new RoadClassParser();
        double[] arr = roadClassParser.getHighwaySpeedMap(map);
        assertEquals("[0.0, 100.0, 100.0, 90.0, 90.0, 90.0]", Helper.createDoubleList(arr).subList(0, 6).toString());
    }
}