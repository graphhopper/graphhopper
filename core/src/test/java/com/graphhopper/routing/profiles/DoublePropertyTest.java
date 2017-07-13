package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DoublePropertyTest {

    @Test
    public void testInit() {
        DoubleProperty prop = new DoubleProperty("test", 10, 50, 2);
        prop.init(new Property.InitializerConfig());
        assertEquals(10d, prop.fromStorageFormatToDouble(prop.toStorageFormatFromDouble(0, 10d)), 0.1);
    }

    @Test
    public void testParseDefaultValue() {
        DoubleProperty prop = new DoubleProperty("test", 10, 50, 2);
        ReaderWay way = new ReaderWay(2L);
        way.setTag("highway", "xy");
        assertEquals(50d, prop.parse(way));
    }
}