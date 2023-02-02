package com.graphhopper.routing.ev;

import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecimalEncodedValueTest {

    @Test
    public void testInit() {
        DecimalEncodedValue prop = new DecimalEncodedValueImpl("test", 10, 2, false);
        prop.init(new EncodedValue.InitializerConfig());
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        prop.setDecimal(false, edgeId, intAccess, 10d);
        assertEquals(10d, prop.getDecimal(false, edgeId, intAccess), 0.1);
    }

    @Test
    public void testMaxValue() {
        DecimalEncodedValue ev = new DecimalEncodedValueImpl("test1", 8, 0.5, false);
        EncodingManager.start().add(ev).build();
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        ev.setDecimal(false, edgeId, intAccess, 100d);
        assertEquals(100, ev.getDecimal(false, edgeId, intAccess), 1e-1);
    }

    @Test
    public void testNegativeBounds() {
        DecimalEncodedValue prop = new DecimalEncodedValueImpl("test", 10, 5, false);
        prop.init(new EncodedValue.InitializerConfig());
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        assertThrows(Exception.class, () -> prop.setDecimal(false, edgeId, intAccess, -1));
    }
}