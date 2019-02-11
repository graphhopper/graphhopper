package com.graphhopper.isochrone.algorithm;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

/**
 *
 * @author Peter Karich
 */
public class DelaunayTriangulationIsolineBuilderTest {

    @Test
    public void testCalc() {
        DelaunayTriangulationIsolineBuilder instance = new DelaunayTriangulationIsolineBuilder();
        List<List<Coordinate>> listOfList = new ArrayList<>();
        List<Coordinate> list = new ArrayList<>();
        listOfList.add(list);
        // lon,lat!
        list.add(new Coordinate(0.000, 0.000));
        list.add(new Coordinate(0.001, 0.000));
        list.add(new Coordinate(0.001, 0.001));
        list.add(new Coordinate(0.001, 0.002));
        list.add(new Coordinate(0.000, 0.002));

        List<Coordinate[]> res = instance.calcList(listOfList, listOfList.size());
        Coordinate[] geometry = res.get(0);
        Assert.assertEquals(9, geometry.length);
    }
}
