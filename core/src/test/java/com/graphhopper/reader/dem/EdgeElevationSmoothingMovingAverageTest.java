package com.graphhopper.reader.dem;

import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Christoph Lingg
 */
public class EdgeElevationSmoothingMovingAverageTest {

    @Test
    public void testTwoPoints() {
        // consists of only two tower nodes and no pillar nodes
        // elevation must stay unchanged
        PointList pl = new PointList(2, true);
        pl.add(0, 0, -1);
        pl.add(1, 1, 100);
        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);
        assertEquals(2, pl.size());
        assertEquals(-1.0, pl.getEle(0), 0.000001);
        assertEquals(100.0, pl.getEle(1), 0.000001);
    }

    @Test
    public void testAllFlat() {
        PointList pl = new PointList(3, true);
        pl.add(0, 0, 1);
        pl.add(1, 1, 1);
        pl.add(2, 2, 1);
        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);
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

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

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

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

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

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

        assertEquals(5, pl.size());
        assertEquals(30, pl.getEle(0), 0.000001);
        assertEquals(((27.5 + 20.0) / 2.0 * 75.0 + 10 * 50.24 + 50 * 24.7) / 150.0, pl.getEle(1), 0.1);
        assertEquals(((22.5 + 20.0) / 2.0 * 25.0 + 10 * 50.24 + 50 * 100 + 25 * 212.5) / 150.0, pl.getEle(2), 0.1);
        assertEquals((5 * 25 + 50 * 100 + 75 * 237.5) / 150.0, pl.getEle(3), 1);
        assertEquals(300, pl.getEle(4), 0.000001);
    }

    @Test
    public void testDuplicates() {
        PointList pl = new PointList(5, true);
        pl.add(0.0, 0.0, 1.0);
        pl.add(1.0, 1.0, 2.0);
        pl.add(1.0, 1.0, 2.0);
        pl.add(1.0, 1.0, 3.0);
        pl.add(2.0, 2.0, 4.0);

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

        assertEquals(5, pl.size());
        assertEquals(1.0, pl.getEle(0), 0.000001);
        assertFalse(Double.isNaN(pl.getEle(1)));
        assertFalse(Double.isNaN(pl.getEle(2)));
        assertFalse(Double.isNaN(pl.getEle(3)));
        assertEquals(4.0, pl.getEle(4), 0.000001);
    }

    @Test
    public void testDuplicatesTower() {
        PointList pl = new PointList(5, true);
        pl.add(0.0, 0.0, 1.0);
        pl.add(0.0, 0.0, 1.0);
        pl.add(0.0, 0.0, 2.0);
        pl.add(1.0, 1.0, 3.0);
        pl.add(2.0, 2.0, 4.0);

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

        assertEquals(5, pl.size());
        assertEquals(1.0, pl.getEle(0), 0.000001);
        assertFalse(Double.isNaN(pl.getEle(1)));
        assertFalse(Double.isNaN(pl.getEle(2)));
        assertFalse(Double.isNaN(pl.getEle(3)));
        assertEquals(4.0, pl.getEle(4), 0.000001);
    }

    @Test
    public void testManyPoints() {
        PointList pl = new PointList(27, true);
        pl.add(10.153564, 47.324976, 1209.5);
        pl.add(10.15365, 47.32499, 1209.3);
        pl.add(10.153465, 47.325058, 1213.6);
        pl.add(10.153382, 47.325062, 1213.5);
        pl.add(10.153283, 47.325048, 1213.5);
        pl.add(10.153049, 47.324956, 1213.5);
        pl.add(10.152899, 47.324949, 1213.6);
        pl.add(10.152795, 47.324968, 1213.7);
        pl.add(10.152706, 47.325044, 1213.7);
        pl.add(10.152466, 47.325041, 1215.2);
        pl.add(10.152283, 47.32508, 1215.4);
        pl.add(10.152216, 47.325074, 1215.5);
        pl.add(10.151649, 47.324849, 1216);
        pl.add(10.151502, 47.324824, 1216.9);
        pl.add(10.151212, 47.324708, 1218.1);
        pl.add(10.150862, 47.324493, 1219.7);
        pl.add(10.150729, 47.324491, 1220.4);
        pl.add(10.150714, 47.324514, 1220.6);
        pl.add(10.150767, 47.324605, 1226.5);
        pl.add(10.150989, 47.324943, 1236.4);
        pl.add(10.150996, 47.32502, 1236.3);
        pl.add(10.150964, 47.325038, 1236.3);
        pl.add(10.150528, 47.324928, 1237.2);
        pl.add(10.149945, 47.324733, 1239.3);
        pl.add(10.14989, 47.324736, 1249.9);
        pl.add(10.149504, 47.324455, 1248.6);
        pl.add(10.149392, 47.324333, 1248);

        EdgeElevationSmoothing.smoothMovingAverage(pl, 150.0);

        double[] expectedElevations = {
                1209.5, 1209.8259124400417, 1212.16778770315, 1212.4695940128302,
                1212.7073845131501, 1213.3531253136111, 1213.933051594191,
                1214.1484378838704, 1214.3274054744827, 1214.72713517562,
                1215.0590153809194, 1215.1879106460751, 1216.6557611915186,
                1217.0679077126047, 1218.343674121969, 1223.000531752824,
                1224.8734639760428, 1225.2508699507118, 1226.687883355977,
                1232.0752337564345, 1233.0834528364871, 1233.565160290987,
                1237.6276352913503, 1243.4122145535805, 1243.92486025017,
                1248.5623859735897, 1248.0
        };

        for (int i = 0; i < pl.size(); i++) {
            assertEquals(expectedElevations[i], pl.getEle(i), 0.0000001);
        }
    }
}
