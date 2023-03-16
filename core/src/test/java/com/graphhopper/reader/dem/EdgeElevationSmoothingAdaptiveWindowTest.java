package com.graphhopper.reader.dem;

import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christoph Lingg
 */
public class EdgeElevationSmoothingAdaptiveWindowTest {

    @Test
    public void testTwoPoints() {
        // consists of only two tower nodes and no pillar nodes
        // elevation must stay unchanged
        PointList pl = new PointList(2, true);
        pl.add(0, 0, -1);
        pl.add(1, 1, 100);
        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);
        assertEquals(2, pl.size());
        assertEquals(-1.0, pl.getEle(0), 0.000001);
        assertEquals(100.0, pl.getEle(1), 0.000001);
    }

//    @Test
//    public void testDuplicates() {
//        // maybe that can happen
//        PointList pl = new PointList(3, true);
//        pl.add(0, 0, -1);
//        pl.add(1, 1, 100);
//        pl.add(1, 1, 200);
//        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);
//
//        assertEquals(3, pl.size());
//        assertEquals(150, pl.getEle(1), 0.000001);
//    }

    @Test
    public void testAllFlat() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 1);
        pl.add(1, 1, 1);
        pl.add(2, 2, 1);
        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);
        assertEquals(3, pl.size());
        assertEquals(1.0, pl.getEle(0), 0.000001);
        assertEquals(1.0, pl.getEle(1), 0.000001);
        assertEquals(1.0, pl.getEle(2), 0.000001);
    }

    @Test
    public void testSparsePoints() {
        PointList pl = new PointList(3, true);
        pl.add(47.329730504970684, 10.156667197157475, 0);
        pl.add(47.3298073615309, 10.15798541322701, 100);
        pl.add(47.3316055451794, 10.158042110691866, 200);

        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);

        assertEquals(3, pl.size());
        assertEquals(0, pl.getEle(0), 0.000001);
        assertEquals((62.5 * 75 + 118.75 * 75) / 150.0, pl.getEle(1), 0.5);
        assertEquals(200, pl.getEle(2), 0.000001);
    }

    @Test
    public void testShortWay() {
        PointList pl = new PointList(3, true);
        pl.add(47.330741060295594, 10.1571805769575, -100);
        pl.add(47.33088752836167, 10.157333651129761, -50);
        pl.add(47.33091499107897, 10.157482223121235, -200);

        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);

        assertEquals(3, pl.size());
        assertEquals(-100, pl.getEle(0), 0.000001);
        assertEquals((-75 * 20 + -125 * 12) / 32.0, pl.getEle(1), 2);
        assertEquals(-200, pl.getEle(2), 0.000001);
    }

    @Test
    public void testDenseWay() {
        // distance: 100m, 50m, 50m, 100m
        PointList pl = new PointList(5, true);
        pl.add(47.32763157186426, 10.158549243021412, 30);
        pl.add(47.32846770417248, 10.159039980808643, 20);
        pl.add(47.32891933217678, 10.159062491716355, 0);
        pl.add(47.32935875031157, 10.159197557162912, 200);
        pl.add(47.330136877623886, 10.159850373485142, 300);

        EdgeElevationSmoothing.smoothMovingAverageAdaptiveWindow(pl);

        assertEquals(5, pl.size());
        assertEquals(30, pl.getEle(0), 0.000001);
        assertEquals(((27.5 + 20.0) / 2.0 * 75.0 + 10 * 50.24 + 50 * 24.7) / 150.0, pl.getEle(1), 0.1);
        assertEquals(((22.5 + 20.0) / 2.0 * 25.0 + 10 * 50.24 + 50 * 100 + 25 * 212.5) / 150.0, pl.getEle(2), 0.1);
        assertEquals((5 * 25 + 50 * 100 + 75 * 237.5) / 150.0, pl.getEle(3), 1);
        assertEquals(300, pl.getEle(4), 0.000001);
    }
}
