package com.graphhopper.routing.ev;

import org.junit.Test;

import static com.graphhopper.routing.ev.CarAccess.NO;
import static com.graphhopper.routing.ev.CarAccess.YES;
import static org.junit.Assert.assertEquals;

public class CarAccessTest {
    @Test
    public void testBasics() {
        assertEquals(YES, CarAccess.find("unknown"));
        assertEquals(NO, CarAccess.find("no"));
    }

}