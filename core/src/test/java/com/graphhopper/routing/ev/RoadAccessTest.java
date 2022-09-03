package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.ev.RoadAccess.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoadAccessTest {
    @Test
    public void testBasics() {
        assertEquals(YES, RoadAccess.find("unknown"));
        assertEquals(YES, RoadAccess.find("permissive"));
        assertEquals(AGRICULTURAL, RoadAccess.find("agricultural;forestry"));
        assertEquals(FORESTRY, RoadAccess.find("forestry;agricultural"));
        assertEquals(NO, RoadAccess.find("no"));
    }

}