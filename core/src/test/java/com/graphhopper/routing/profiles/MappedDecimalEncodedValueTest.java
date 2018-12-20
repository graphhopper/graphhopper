package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MappedDecimalEncodedValueTest {

    private MappedDecimalEncodedValue maxweight;

    @Before
    public void setup() {
        List<Double> list = Arrays.asList(6d, 1d, 2d, 4.5);
        maxweight = new MappedDecimalEncodedValue("maxweight", list, 0.1, false);
        maxweight.init(new EncodedValue.InitializerConfig());
    }

    @Test
    public void testMapping() {
        IntsRef ref = new IntsRef(1);
        maxweight.setDecimal(false, ref, 4.5);
        assertEquals(4.5, maxweight.getDecimal(false, ref), 0.01);
    }

    @Test
    public void testDefault() {
        IntsRef ref = new IntsRef(1);
        assertEquals(6.0, maxweight.getDecimal(false, ref), 0.01);
    }

    @Test
    public void testMappingError() {
        try {
            maxweight.setDecimal(false, new IntsRef(0), 4);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}