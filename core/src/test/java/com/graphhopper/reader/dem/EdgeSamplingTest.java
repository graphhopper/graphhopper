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
                1,
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
        in.add(0.8, 0, 0);

        PointList out = EdgeSampling.sample(
                1,
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 2,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.4,0.0,10.0), (0.8,0.0,0.0)", out.toString());
    }

    @Test
    public void addsExtraPointBelowSecondThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(0.8, 0, 0);

        PointList out = EdgeSampling.sample(
                1,
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 3,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.4,0.0,10.0), (0.8,0.0,0.0)", out.toString());
    }

    @Test
    public void addsTwoPointsAboveThreshold() {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(0.75, 0, 0);

        PointList out = EdgeSampling.sample(
                1,
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 4,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.25,0.0,10.0), (0.5,0.0,10.0), (0.75,0.0,0.0)", out.toString());
    }

    @Test
    public void doesntAddPointsCrossingInternationalDateLine() {
        PointList in = new PointList(2, true);
        in.add(0, -179, 0);
        in.add(0.0, 179, 0);

        PointList out = EdgeSampling.sample(
                1,
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,-179.0,0.0), (0.0,179.0,0.0)", out.toString());
    }

}