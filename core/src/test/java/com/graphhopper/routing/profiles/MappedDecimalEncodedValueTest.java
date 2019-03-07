package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
    public void testDefaultIsSmallest() {
        IntsRef ref = new IntsRef(1);
        assertEquals(1.0, maxweight.getDecimal(false, ref), 0.01);
    }

    @Test
    public void testRoundingRequired() {
        IntsRef intsRef = new IntsRef(1);
        maxweight.setDecimal(false, intsRef, 4);
        assertEquals(4.5, maxweight.getDecimal(false, intsRef), 0.1);

        maxweight.setDecimal(false, intsRef, 2.5);
        assertEquals(2.0, maxweight.getDecimal(false, intsRef), 0.1);

        maxweight.setDecimal(false, intsRef, 6.5);
        assertEquals(6, maxweight.getDecimal(false, intsRef), 0.1);

        maxweight.setDecimal(false, intsRef, 0.5);
        assertEquals(1, maxweight.getDecimal(false, intsRef), 0.1);
    }
}