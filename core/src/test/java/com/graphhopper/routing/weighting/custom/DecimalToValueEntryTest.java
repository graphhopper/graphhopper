package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.UnsignedDecimalEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecimalToValueEntryTest {

    @Test
    public void testMapParsing() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        DecimalEncodedValue decimalEncodedValue = new UnsignedDecimalEncodedValue("my_speed", 7, 0.5, false);
        decimalEncodedValue.init(config);

        double defaultValue = 33;
        Map<String, Object> map = new HashMap<>();
        map.put(">8", 24);
        EdgeToValueEntry entry = DecimalToValueEntry.create("priority.my_speed", decimalEncodedValue, map, defaultValue, 1, 100);
        IntsRef flags = new IntsRef(1);

        assertValue(defaultValue, 0, flags, decimalEncodedValue, entry);
        assertValue(defaultValue, 8.0, flags, decimalEncodedValue, entry);
        assertValue(24.0, 10, flags, decimalEncodedValue, entry);
    }

    static void assertValue(double expectedReturnValue, double returnedFromEdge, IntsRef flags, DecimalEncodedValue decimalEncodedValue, EdgeToValueEntry entry) {
        decimalEncodedValue.setDecimal(false, flags, returnedFromEdge);
        assertEquals(expectedReturnValue, entry.getValue(GHUtility.createMockedEdgeIteratorState(100, flags), false));
    }
}