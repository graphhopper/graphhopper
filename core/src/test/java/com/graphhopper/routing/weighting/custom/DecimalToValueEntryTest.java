package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.UnsignedDecimalEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.DecimalToValueEntry.parseRange;
import static org.junit.jupiter.api.Assertions.*;

class DecimalToValueEntryTest {

    @Test
    public void testMapParsing() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        DecimalEncodedValue decimalEncodedValue = new UnsignedDecimalEncodedValue("my_speed", 7, 0.5, false);
        decimalEncodedValue.init(config);

        double defaultValue = 33;
        Map<String, Object> map = new HashMap<>();
        map.put("8,10", 24);
        map.put("2,6", 12);
        double[][] arrays = DecimalToValueEntry.createArrays(decimalEncodedValue, "priority.my_speed", defaultValue, 1, 100, map);
        assertEquals("[2.0, 6.0, 8.0, 10.0]", Arrays.toString(arrays[0]));
        assertEquals("[33.0, 12.0, 33.0, 24.0]", Arrays.toString(arrays[1]));

        EdgeToValueEntry entry = DecimalToValueEntry.create(decimalEncodedValue, "priority.my_speed", defaultValue, 1, 100, map);
        IntsRef flags = new IntsRef(1);

        assertValue(defaultValue, 0, flags, decimalEncodedValue, entry);
        assertValue(defaultValue, 7, flags, decimalEncodedValue, entry);
        assertValue(defaultValue, 7.5, flags, decimalEncodedValue, entry);
        assertValue(defaultValue, 10, flags, decimalEncodedValue, entry);
        assertValue(12, 5, flags, decimalEncodedValue, entry);
        assertValue(24, 8, flags, decimalEncodedValue, entry);
    }

    static void assertValue(double expectedReturnValue, double ev, IntsRef flags, DecimalEncodedValue decimalEncodedValue, EdgeToValueEntry entry) {
        decimalEncodedValue.setDecimal(false, flags, ev);
        assertEquals(expectedReturnValue, entry.getValue(GHUtility.createMockedEdgeIteratorState(100, flags), false));
    }

//    @Test
//    public void testRangeParsing() {
//        DecimalToValueEntry.Range range = parseRange("priority", "[1,2.5]", 1);
//        assertEquals(1, range.min);
//        assertEquals(2.5, range.max);
//
//        range = parseRange("priority", "1,2.5", 1);
//        assertEquals(1, range.min);
//        assertEquals(2.5, range.max);
//
//        try {
//            parseRange("priority", "1,2]", 1);
//            fail();
//        } catch (Exception ex) {
//            assertTrue(ex.getMessage().startsWith("Range must begin with "));
//        }
//    }

    @Test
    public void testRangeParsing() {
        DecimalToValueEntry.Range range = parseRange("priority", "1,2.5", 1);
        assertEquals(1, range.min);
        assertEquals(2.5, range.max);

        try {
            parseRange("priority", "1,2,3", 1);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Range is invalid."));
        }
    }
}