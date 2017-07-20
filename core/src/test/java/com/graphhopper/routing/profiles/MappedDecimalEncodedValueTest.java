package com.graphhopper.routing.profiles;

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
        List<Double> list = Arrays.asList(1d, 2d, 4.5, 6d);
        maxweight = new MappedDecimalEncodedValue("maxweight", list, 0.1, 6d, false);
        maxweight.init(new EncodedValue.InitializerConfig());
    }

    @Test
    public void testMapping() {
        assertEquals(4.5, maxweight.fromStorageFormatToDouble(false, maxweight.toStorageFormatFromDouble(false, 0, 4.5)), 0.01);
    }

    @Test
    public void testMappingError() {
        try {
            maxweight.toStorageFormatFromDouble(false, 0, 4);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }
}