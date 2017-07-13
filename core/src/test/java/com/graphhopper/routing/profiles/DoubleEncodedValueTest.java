package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DoubleEncodedValueTest {

    @Test
    public void testInit() {
        DoubleEncodedValue prop = new DoubleEncodedValue("test", 10, 50, 2);
        prop.init(new EncodedValue.InitializerConfig());
        assertEquals(10d, prop.fromStorageFormatToDouble(prop.toStorageFormatFromDouble(0, 10d)), 0.1);
    }

    @Test
    public void testParseDefaultValue() {
        DoubleEncodedValue prop = new DoubleEncodedValue("test", 10, 50, 2);
        ReaderWay way = new ReaderWay(2L);
        way.setTag("highway", "xy");
        assertEquals(50d, prop.parse(way));
    }
}