package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

public class SlopeCalculator {
    private final DecimalEncodedValue maxSlopeEnc;
    private final DecimalEncodedValue averageSlopeEnc;
    // the elevation data fluctuates a lot and so the slope is not that precise for short edges.
    private static final double MIN_LENGTH = 8;

    public SlopeCalculator(DecimalEncodedValue max, DecimalEncodedValue averageEnc) {
        this.maxSlopeEnc = max;
        this.averageSlopeEnc = averageEnc;
    }

    public void execute(Graph graph) {
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            PointList pointList = iter.fetchWayGeometry(FetchMode.ALL);
            if (!pointList.is3D())
                throw new IllegalArgumentException("Cannot calculate slope for 2D PointList " + pointList);
            if (pointList.isEmpty()) {
                if (maxSlopeEnc != null)
                    iter.set(maxSlopeEnc, 0);
                if (averageSlopeEnc != null)
                    iter.set(averageSlopeEnc, 0);
                continue;
            }
            // Calculate 2d distance, although pointList might be 3D.
            double distance2D = DistanceCalcEarth.calcDistance(pointList, false);
            if (distance2D < MIN_LENGTH) {
                if (averageSlopeEnc != null)
                    // default minimum of average_slope is negative so we have to explicitly set it to 0
                    iter.set(averageSlopeEnc, 0);
                continue;
            }

            double towerNodeSlope = calcSlope(pointList.getEle(pointList.size() - 1) - pointList.getEle(0), distance2D);
            if (Double.isNaN(towerNodeSlope))
                throw new IllegalArgumentException("average_slope was NaN for edge " + iter.getEdge() + " " + pointList);

            if (averageSlopeEnc != null) {
                if (towerNodeSlope >= 0)
                    iter.set(averageSlopeEnc, Math.min(towerNodeSlope, averageSlopeEnc.getMaxStorableDecimal()));
                else
                    iter.setReverse(averageSlopeEnc, Math.min(Math.abs(towerNodeSlope), averageSlopeEnc.getMaxStorableDecimal()));
            }

            if (maxSlopeEnc != null) {
                // max_slope is more error-prone as the shorter distances increase the fluctuation
                // so apply some more filtering (here we use the average elevation delta of the previous two points)
                double maxSlope = 0, prevDist = 0, prevLat = pointList.getLat(0), prevLon = pointList.getLon(0);
                for (int i = 1; i < pointList.size(); i++) {
                    double pillarDistance2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i));
                    if (i > 1 && prevDist > MIN_LENGTH) {
                        double averagedPrevEle = (pointList.getEle(i - 1) + pointList.getEle(i - 2)) / 2;
                        double tmpSlope = calcSlope(pointList.getEle(i) - averagedPrevEle, pillarDistance2D + prevDist / 2);
                        maxSlope = Math.abs(tmpSlope) > Math.abs(maxSlope) ? tmpSlope : maxSlope;
                    }
                    prevDist = pillarDistance2D;
                    prevLat = pointList.getLat(i);
                    prevLon = pointList.getLon(i);
                }

                maxSlope = Math.abs(towerNodeSlope) > Math.abs(maxSlope) ? towerNodeSlope : maxSlope;

                if (Double.isNaN(maxSlope))
                    throw new IllegalArgumentException("max_slope was NaN for edge " + iter.getEdge() + " " + pointList);

                double val = Math.max(maxSlope, maxSlopeEnc.getMinStorableDecimal());
                iter.set(maxSlopeEnc, Math.min(maxSlopeEnc.getMaxStorableDecimal(), val));
            }
        }
    }

    private static double calcSlope(double eleDelta, double distance2D) {
        return eleDelta * 100 / distance2D;
    }
}
