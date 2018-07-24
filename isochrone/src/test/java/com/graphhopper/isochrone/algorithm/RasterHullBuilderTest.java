package com.graphhopper.isochrone.algorithm;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class RasterHullBuilderTest {

    @Test
    public void testCalc() {
        RasterHullBuilder instance = new RasterHullBuilder();
        List<List<Double[]>> listOfList = new ArrayList<List<Double[]>>();
        List<Double[]> list = new ArrayList<Double[]>();
        listOfList.add(list);
        // lon,lat!
        list.add(new Double[]{0.000, 0.000});
        list.add(new Double[]{0.001, 0.000});
        list.add(new Double[]{0.001, 0.001});
        list.add(new Double[]{0.001, 0.002});
        list.add(new Double[]{0.000, 0.002});

        List<List<Double[]>> res = instance.calcList(listOfList, listOfList.size());
        List<Double[]> geometry = res.get(0);
        Assert.assertEquals(9, geometry.size());
    }
}
