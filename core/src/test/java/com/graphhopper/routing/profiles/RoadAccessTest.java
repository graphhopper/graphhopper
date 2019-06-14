package com.graphhopper.routing.profiles;

import org.junit.Test;

import static com.graphhopper.routing.profiles.RoadAccess.NO;
import static com.graphhopper.routing.profiles.RoadAccess.YES;
import static org.junit.Assert.assertEquals;

public class RoadAccessTest {
    @Test
    public void testBasics() {
        assertEquals(YES, RoadAccess.find("unknown"));
        assertEquals(NO, RoadAccess.find("no"));
    }

}