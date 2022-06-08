package com.graphhopper.routing.ev;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.util.CarTagParser;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoders;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DecimalEncodedValueTest {

    @Test
    public void testInit() {
        DecimalEncodedValue prop = new DecimalEncodedValueImpl("test", 10, 2, false);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(1);
        prop.setDecimal(false, ref, 10d);
        assertEquals(10d, prop.getDecimal(false, ref), 0.1);
    }

    @Test
    public void testMaxValue() {
        FlagEncoder carEncoder = FlagEncoders.createCar(new PMap("speed_bits=10|speed_factor=0.5"));
        EncodingManager em = EncodingManager.create(carEncoder);
        DecimalEncodedValue carAverageSpeedEnc = em.getDecimalEncodedValue(EncodingManager.getKey(carEncoder, "average_speed"));
        CarTagParser carTagParser = new CarTagParser(em, new PMap());
        carTagParser.init(new DateRangeParser());

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        IntsRef flags = carTagParser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(101.5, carAverageSpeedEnc.getDecimal(true, flags), 1e-1);

        DecimalEncodedValue instance1 = new DecimalEncodedValueImpl("test1", 8, 0.5, false);
        instance1.init(new EncodedValue.InitializerConfig());
        flags = em.createEdgeFlags();
        instance1.setDecimal(false, flags, 100d);
        assertEquals(100, instance1.getDecimal(false, flags), 1e-1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValueImpl("test", 10, 5, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setDecimal(false, new IntsRef(1), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}