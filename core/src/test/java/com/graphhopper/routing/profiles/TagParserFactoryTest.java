package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class TagParserFactoryTest {

    @Test
    public void testParseDefaultValue() {
        ReaderWay way = new ReaderWay(2L);
        way.setTag("highway", "xy");
        DecimalEncodedValue ev = new DecimalEncodedValue("maxspeed", 5, 0, 1, false);
        final AtomicInteger parsed = new AtomicInteger(-1);
        EdgeSetter setter = new EdgeSetter() {
            @Override
            public void set(EdgeIteratorState edgeState, EncodedValue value, Object object) {
                parsed.set(((Number) object).intValue());
            }
        };
        TagParserFactory.Car.createMaxSpeed(ev).parse(setter, way, null);
        assertEquals(-1, parsed.get());

        way.setTag("maxspeed", "30");
        TagParserFactory.Car.createMaxSpeed(ev).parse(setter, way, null);
        assertEquals(30, parsed.get());
    }
}