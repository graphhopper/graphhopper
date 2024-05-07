package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;

public class SlopeCalculator implements TagParser {
    private final DecimalEncodedValue maxSlopeEnc;
    private final DecimalEncodedValue averageSlopeEnc;
    // the elevation data fluctuates a lot and so the slope is not that precise for short edges.
    private static final double MIN_LENGTH = 8;

    public SlopeCalculator(DecimalEncodedValue max, DecimalEncodedValue averageEnc) {
        this.maxSlopeEnc = max;
        this.averageSlopeEnc = averageEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        if (pointList != null) {
            if (pointList.isEmpty() || !pointList.is3D()) {
                if (maxSlopeEnc != null)
                    maxSlopeEnc.setDecimal(false, edgeId, edgeIntAccess, 0);
                if (averageSlopeEnc != null)
                    averageSlopeEnc.setDecimal(false, edgeId, edgeIntAccess, 0);
                return;
            }
            // Calculate 2d distance, although pointList might be 3D.
            // This calculation is a bit expensive and edge_distance is available already, but this would be in 3D
            double distance2D = DistanceCalcEarth.calcDistance(pointList, false);
            if (distance2D < MIN_LENGTH) {
                if (averageSlopeEnc != null)
                    // default is minimum of average_slope is negative so we have to explicitly set it to 0
                    averageSlopeEnc.setDecimal(false, edgeId, edgeIntAccess, 0);
                return;
            }

            double towerNodeSlope = calcSlope(pointList.getEle(pointList.size() - 1) - pointList.getEle(0), distance2D);
            if (Double.isNaN(towerNodeSlope))
                throw new IllegalArgumentException("average_slope was NaN for OSM way ID " + way.getId());

            if (averageSlopeEnc != null) {
                if (towerNodeSlope >= 0)
                    averageSlopeEnc.setDecimal(false, edgeId, edgeIntAccess, Math.min(towerNodeSlope, averageSlopeEnc.getMaxStorableDecimal()));
                else
                    averageSlopeEnc.setDecimal(true, edgeId, edgeIntAccess, Math.min(Math.abs(towerNodeSlope), averageSlopeEnc.getMaxStorableDecimal()));
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

                // For tunnels and bridges we cannot trust the pillar node elevation and ignore all changes.
                // Probably we should somehow recalculate even the average_slope after elevation interpolation? See EdgeElevationInterpolator
                if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
                    maxSlope = towerNodeSlope;
                else
                    maxSlope = Math.abs(towerNodeSlope) > Math.abs(maxSlope) ? towerNodeSlope : maxSlope;

                if (Double.isNaN(maxSlope))
                    throw new IllegalArgumentException("max_slope was NaN for OSM way ID " + way.getId());

                double val = Math.max(maxSlope, maxSlopeEnc.getMinStorableDecimal());
                maxSlopeEnc.setDecimal(false, edgeId, edgeIntAccess, Math.min(maxSlopeEnc.getMaxStorableDecimal(), val));
            }
        }
    }

    static double calcSlope(double eleDelta, double distance2D) {
        return eleDelta * 100 / distance2D;
    }
}
