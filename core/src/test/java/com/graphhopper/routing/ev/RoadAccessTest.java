package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static com.graphhopper.Junit4To5Assertions.assertEquals;
import static com.graphhopper.routing.ev.RoadAccess.NO;
import static com.graphhopper.routing.ev.RoadAccess.YES;

public class RoadAccessTest {
    @Test
    public void testBasics() {
        assertEquals(YES, RoadAccess.find("unknown"));
        assertEquals(NO, RoadAccess.find("no"));
    }

}