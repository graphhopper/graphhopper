package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VehicleConditionsTest {
    private final VehicleConditions truck = new VehicleConditions(List.of("hgv", "motor_vehicle", "vehicle", "access"),
            Set.of("yes", "destination")).weight(26).height(3.7).width(2.6, 0.34).length(12).axes(3).hgv(true);

    @Test
    public void conditional() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("access:conditional", "no @ (weight > 7.5)");
        assertEquals("no", truck.conditional(way, "access"));
        assertNull(truck.conditional(way, "hgv"));
        way.setTag("access:conditional", "no @ (weight < 7.5)");
        assertNull(truck.conditional(way, "access"));
        // the equals sign is ignored
        way.setTag("access:conditional", "no @ weight>=25");
        assertEquals("no", truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ (height>3.5t)");
        assertEquals("no", truck.conditional(way, "access"));
        // mirrors do not count
        way.setTag("access:conditional", "no @ (width > 2.7)");
        assertNull(truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ hgv");
        assertEquals("no", truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ psv");
        assertNull(truck.conditional(way, "access"));
        way.setTag("access:conditional", "none @ (maxweight)");
        assertEquals("none", truck.conditional(way, "access"));
        way.setTag("access:conditional", "destination @ (weight > 3.5)");
        assertEquals("destination", truck.conditional(way, "access"));
        way.setTag("access:conditional", "none @ destination");
        assertEquals("none", truck.conditional(way, "access"));
        way.setTag("access:conditional", "none @ private");
        assertNull(truck.conditional(way, "access"));
        // temporal, multiple and broken conditions are not evaluated
        way.setTag("access:conditional", "no @ (Mo-Fr 06:00-10:00)");
        assertNull(truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ (weight > 7.5); yes @ (Mo-Fr)");
        assertNull(truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ (weight > 7,5)");
        assertNull(truck.conditional(way, "access"));
        way.setTag("access:conditional", "no @ (weight >)");
        assertNull(truck.conditional(way, "access"));
    }

    @Test
    public void limits() {
        ReaderWay way = new ReaderWay(1);
        assertFalse(truck.exceedsLimits(way));
        way.setTag("maxweight", "26");
        assertFalse(truck.exceedsLimits(way));
        way.setTag("maxweight", "25.9t");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("access:conditional", "yes @ (weight > 7.5)");
        assertFalse(truck.exceedsLimits(way));
        way.setTag("access:conditional", "no @ (weight > 7.5)");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("maxweight:conditional", "none @ destination");
        assertFalse(truck.exceedsLimits(way));
        way.clearTags();

        way.setTag("maxgcweight", "20");
        assertTrue(truck.exceedsLimits(way));
        way.clearTags();
        way.setTag("maxaxleload", "8.6");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("maxaxleload", "8.6 t");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("maxaxleload", "9");
        assertFalse(truck.exceedsLimits(way));
        way.clearTags();

        way.setTag("height", "3.9");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("height", "4");
        assertFalse(truck.exceedsLimits(way));
        way.setTag("maxheight:physical", "3 m");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("bridge", "no");
        assertFalse(truck.exceedsLimits(way));
        way.clearTags();

        way.setTag("maxwidth:physical", "2.9");
        assertTrue(truck.exceedsLimits(way));
        way.setTag("maxwidth:physical", "3");
        assertFalse(truck.exceedsLimits(way));
        way.clearTags();

        way.setTag("maxlength:hgv", "11");
        assertTrue(truck.exceedsLimits(way));
        assertFalse(truck.hgv(false).exceedsLimits(way));
    }
}
