package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.AverageSlope;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.MaxSlope;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlopeCalculatorTest {

    @Test
    void simpleElevation() {
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        SlopeCalculator creator = new SlopeCalculator(maxEnc, averageEnc);
        IntsRef edgeRef = new IntsRef(1);
        ReaderWay way = new ReaderWay(1L);
        PointList pointList = new PointList(5, true);
        pointList.add(51.0, 12.001, 0);
        pointList.add(51.0, 12.002, 3.5); // ~70m
        pointList.add(51.0, 12.003, 4); // ~140m
        pointList.add(51.0, 12.004, 2); // ~210m
        way.setTag("point_list", pointList);
        creator.handleWayTags(edgeRef, way, IntsRef.EMPTY);

        assertEquals(Math.round(2.0 / 210 * 100), averageEnc.getDecimal(false, edgeRef), 1e-3);
        assertEquals(-Math.round(2.0 / 210 * 100), averageEnc.getDecimal(true, edgeRef), 1e-3);

        assertEquals(Math.round(1.75 / 105 * 100), maxEnc.getDecimal(false, edgeRef), 1e-3);
        assertEquals(Math.round(1.75 / 105 * 100), maxEnc.getDecimal(true, edgeRef), 1e-3);
    }

    @Test
    public void testAveragingOfMaxSlope() {
        // point=49.977518%2C11.564285&point=49.979878%2C11.563663&profile=bike
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        SlopeCalculator creator = new SlopeCalculator(maxEnc, averageEnc);
        IntsRef edgeRef = new IntsRef(1);
        ReaderWay way = new ReaderWay(1L);
        PointList pointList = new PointList(5, true);
        pointList.add(51.0, 12.0010, 10);
        pointList.add(51.0, 12.0014, 8); // 28m
        pointList.add(51.0, 12.0034, 8); // 140m
        pointList.add(51.0, 12.0054, 0); // 140m
        pointList.add(51.0, 12.0070, 7); // 112m
        way.setTag("point_list", pointList);
        creator.handleWayTags(edgeRef, way, IntsRef.EMPTY);

        assertEquals(Math.round(8.0 / 210 * 100), maxEnc.getDecimal(false, edgeRef), 1e-3);
        assertEquals(Math.round(8.0 / 210 * 100), maxEnc.getDecimal(true, edgeRef), 1e-3);
    }

    @Test
    public void test2() {
        PointList pointList = new PointList(5, true);
        pointList.add(47.7281561, 11.9993135, 1163.0);
        pointList.add(47.7282782, 11.9991944, 1163.0);
        pointList.add(47.7283135, 11.9991135, 1178.0);
        ReaderWay way = new ReaderWay(1);
        way.setTag("point_list", pointList);
        IntsRef edgeRef = new IntsRef(1);
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();
        SlopeCalculator creator = new SlopeCalculator(maxEnc, averageEnc);
        creator.handleWayTags(edgeRef, way, IntsRef.EMPTY);
        assertEquals(31, maxEnc.getDecimal(false, edgeRef), 1e-3);
        assertEquals(31, averageEnc.getDecimal(false, edgeRef), 1e-3);
    }

    @Test
    public void test2D() {
        IntsRef edgeRef = new IntsRef(1);
        PointList pointList = new PointList(5, false);
        pointList.add(47.7283135, 11.9991135);
        ReaderWay way = new ReaderWay(1);
        way.setTag("point_list", pointList);
        DecimalEncodedValue averageEnc = AverageSlope.create();
        DecimalEncodedValue maxEnc = MaxSlope.create();
        new EncodingManager.Builder().add(averageEnc).add(maxEnc).build();

        SlopeCalculator creator = new SlopeCalculator(maxEnc, averageEnc);
        creator.handleWayTags(edgeRef, way, IntsRef.EMPTY);
        assertEquals(0, maxEnc.getDecimal(false, edgeRef), 1e-3);
        assertEquals(0, averageEnc.getDecimal(false, edgeRef), 1e-3);
    }
}
