package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.tagparsers.CarMaxSpeedParser;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TagParserFactoryTest {

    @Test
    public void testParseDefaultValue() {
        ReaderWay way = new ReaderWay(2L);
        way.setTag("highway", "xy");
        DecimalEncodedValue ev = new DecimalEncodedValue("maxspeed", 5, 1, 1, false);
        ev.init(new EncodedValue.InitializerConfig());
        ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return true;
            }
        };

        IntsRef ints = new IntsRef(1);
        new CarMaxSpeedParser(ev).setReadWayFilter(filter).parse(ints, way);
        assertEquals(1, ev.getDecimal(false, ints), .1);

        way.setTag("maxspeed", "30");
        new CarMaxSpeedParser(ev).setReadWayFilter(filter).parse(ints, way);
        assertEquals(30, ev.getDecimal(false, ints), .1);
    }
}