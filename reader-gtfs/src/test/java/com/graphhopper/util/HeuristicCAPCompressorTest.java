package com.graphhopper.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HeuristicCAPCompressorTest {

    @Test
    public void testExampleFromPaper() {
        List<Integer> s = Arrays.asList(3, 5, 7, 10, 15, 17, 19, 20, 23, 24, 30, 31, 40, 50, 60);
        List<HeuristicCAPCompressor.ArithmeticProgression> arithmeticProgressions = HeuristicCAPCompressor.compress(s);
        Assert.assertArrayEquals(s.toArray(new Integer[]{}), HeuristicCAPCompressor.decompress(arithmeticProgressions).toArray());
    }

}
