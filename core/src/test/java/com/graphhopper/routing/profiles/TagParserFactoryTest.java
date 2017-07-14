package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TagParserFactoryTest {

    @Test
    public void testParseDefaultValue() {
        ReaderWay way = new ReaderWay(2L);
        way.setTag("highway", "xy");
        assertEquals(null, TagParserFactory.Car.createMaxSpeed().parse(way));
    }
}