package com.graphhopper.routing.ev;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        DecimalEncodedValue ev = new DecimalEncodedValueImpl("test1", 8, 0.5, false);
        EncodingManager em = EncodingManager.start().add(ev).build();
        IntsRef flags = em.createEdgeFlags();
        ev.setDecimal(false, flags, 100d);
        assertEquals(100, ev.getDecimal(false, flags), 1e-1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValueImpl("test", 10, 5, false);
        prop.init(new EncodedValue.InitializerConfig());
        assertThrows(Exception.class, () -> prop.setDecimal(false, new IntsRef(1), -1));
    }
}