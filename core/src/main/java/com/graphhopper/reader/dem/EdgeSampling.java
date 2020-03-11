package com.graphhopper.reader.dem;

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures that elevation is sampled along a point list with no more than maxDistance between samples. Works by adding
 * points along long edges and fetching elevation at each inserted point.
 *
 * This uses a simple linear approximation to interpolate between points which should be fine for short distances, but
 * for longer distances this should use a more robust algorithm like rhumb or great circle interpolation.
 */
public class EdgeSampling {
    private static final Logger logger = LoggerFactory.getLogger(EdgeSampling.class);
    private static final double WARN_ON_SEGMENT_LENGTH = DistanceCalcEarth.METERS_PER_DEGREE / 4;

    private EdgeSampling() {}

    public static PointList sample(PointList input, double maxDistance, DistanceCalc distCalc, ElevationProvider elevation) {
        PointList output = new PointList(input.getSize() * 2, input.is3D());
        if (input.isEmpty()) return output;
        int nodes = input.getSize();
        double lastLat = input.getLat(0), lastLon = input.getLon(0), thisLat, thisLon, thisEle;
        for (int i = 0; i < nodes; i++) {
            thisLat = input.getLat(i);
            thisLon = input.getLon(i);
            thisEle = input.getEle(i);
            if (i > 0 && !distCalc.isCrossBoundary(lastLon, thisLon)) {
                double segmentLength = distCalc.calcDist(lastLat, lastLon, thisLat, thisLon);
                if (segmentLength > WARN_ON_SEGMENT_LENGTH) {
                    logger.warn("Edge from " + lastLat + "," + lastLon + " to " + thisLat + "," + thisLon + " is " +
                            (int)segmentLength + "m long, might cause issues with edge sampling.");
                }
                int segments = (int) Math.round(segmentLength / maxDistance);
                for (int segment = 1; segment < segments; segment++) {
                    double ratio = (double) segment / segments;
                    double lat = lastLat + (thisLat - lastLat) * ratio;
                    double lon = lastLon + (thisLon - lastLon) * ratio;
                    double ele = elevation.getEle(lat, lon);
                    if (!Double.isNaN(ele)) {
                        output.add(lat, lon, ele);
                    }
                }
            }
            output.add(thisLat, thisLon, thisEle);
            lastLat = thisLat;
            lastLon = thisLon;
        }
        return output;
    }
}