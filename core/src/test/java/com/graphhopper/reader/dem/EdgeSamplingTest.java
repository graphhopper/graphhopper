package com.graphhopper.reader.dem;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class EdgeSamplingTest {
    private final ElevationProvider elevation = new AbstractElevationProvider("") {
        @Override
        public double getEle(double lat, double lon) {
            return 10;
        }

        @Override
        public void release() {}
        @Override
        String getFileName(double lat, double lon) { return ""; }
        @Override
        String getDownloadURL(double lat, double lon) { return ""; }
    };

    @Test
    public void doesNotAddExtraPointBelowThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(1.4, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (1.4,0.0,0.0)", out.toString());
    }

    @Test
    public void addsExtraPointAboveThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(1.6, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.8,0.0,10.0), (1.6,0.0,0.0)", out.toString());
    }

    @Test
    public void addsExtraPointBelowSecondThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(2.4, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (1.2,0.0,10.0), (2.4,0.0,0.0)", out.toString());
    }

    @Test
    public void addsTwoPointsAboveThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(3.0, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (1.0,0.0,10.0), (2.0,0.0,10.0), (3.0,0.0,0.0)", out.toString());
    }

    @Test
    public void doesntAddPointsCrossingInternationalDateLine() {
        PointList in = new PointList(2, true);
        in.add(0, -179, 0);
        in.add(0.0, 179, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,-179.0,0.0), (0.0,179.0,0.0)", out.toString());
    }

}