package com.graphhopper.reader.gtfs;

import org.junit.Assert;
import org.junit.Test;

import java.util.stream.Stream;

public class LegsTest {

    @Test
    public void splittingBefore() {
        Assert.assertEquals("[[1, 2, 3], [4, 5, 6, 7], [4, 8, 9, 10]]",
                Stream.of(1, 2, 3, 4, 5, 6, 7, 4, 8, 9, 10)
                        .collect(GraphHopperGtfs.splittingBefore(i -> i == 4))
                        .toString());
    }

}
