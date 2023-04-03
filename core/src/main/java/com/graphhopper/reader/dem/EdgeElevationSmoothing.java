package com.graphhopper.reader.dem;

import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.cursors.IntDoubleCursor;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.PointList;

import java.util.function.Consumer;

/**
 * Elevation data is read from DEM tiles that have data points for rectangular tiles usually having an
 * edge length of 30 or 90 meter. Elevation in between the middle points of those tiles will be
 * interpolated and weighted by the distance from a node to adjacent tile centers.
 * <p>
 * Ways that go along cliffs or ridges are particularly affected by ups and downs that do not reflect
 * the actual elevation but may be artifacts originated from very accurately mapping when elevation has
 * a lower resolution.
 *
 * @author Peter Karich
 * @author Christoph Lingg
 */
public class EdgeElevationSmoothing {
    public static void smoothMovingAverage(PointList geometry, double maxWindowSize) {
        if (geometry.size() <= 2) {
            // geometry consists only of tower nodes, there are no pillar nodes to be smoothed in between
            return;
        }

        // calculate the distance between all points once here to avoid repeated calculation.
        // for n nodes there are always n-1 edges
        double[] distances = new double[geometry.size() - 1];
        for (int i = 0; i <= geometry.size() - 2; i++) {
            distances[i] = DistancePlaneProjection.DIST_PLANE.calcDist(
                    geometry.getLat(i), geometry.getLon(i),
                    geometry.getLat(i + 1), geometry.getLon(i + 1)
            );
        }


        // map that will collect all smoothed elevation values, size is less by 2
        // because elevation of start and end point (tower nodes) won't be touched
        IntDoubleHashMap averagedElevations = new IntDoubleHashMap((geometry.size() - 1) * 4 / 3);

        // iterate over every pillar node to smooth its elevation
        // first and last points are left out as they are tower nodes
        for (int i = 1; i <= geometry.size() - 2; i++) {
            // first, determine the average window which could be smaller when close to pillar nodes
            double searchDistance = maxWindowSize / 2.0;

            double searchDistanceBack = 0.0;
            for (int j = i - 1; j >= 0; j--) {
                searchDistanceBack += distances[j];
                if (searchDistanceBack > searchDistance) {
                    break;
                }
            }

            // update search distance if pillar node is close to START tower node
            searchDistance = Math.min(searchDistance, searchDistanceBack);

            double searchDistanceForward = 0.0;
            for (int j = i; j < geometry.size() - 1; j++) {
                searchDistanceForward += distances[j];
                if (searchDistanceForward > searchDistance) {
                    break;
                }
            }

            // update search distance if pillar node is close to END tower node
            searchDistance = Math.min(searchDistance, searchDistanceForward);

            if (searchDistance <= 0.0) {
                // there is nothing to smooth. this is an edge case where pillar nodes share exactly the same location
                // as a tower node.
                // by doing so we avoid (at least theoretically) a division by zero later in the function call
                continue;
            }

            // area under elevation curve
            double elevationArea = 0.0;

            // first going again backwards
            double distanceBack = 0.0;
            for (int j = i - 1; j >= 0; j--) {
                double dist = distances[j];
                double searchDistLeft = searchDistance - distanceBack;
                distanceBack += dist;
                if (searchDistLeft < dist) {
                    // node lies outside averaging window
                    double elevationDelta = geometry.getEle(j) - geometry.getEle(j + 1);
                    double elevationAtSearchDistance = geometry.getEle(j + 1) + searchDistLeft / dist * elevationDelta;
                    elevationArea += searchDistLeft * (geometry.getEle(j + 1) + elevationAtSearchDistance) / 2.0;
                    break;
                } else {
                    elevationArea += dist * (geometry.getEle(j + 1) + geometry.getEle(j)) / 2.0;
                }
            }

            // now going forward
            double distanceForward = 0.0;
            for (int j = i; j < geometry.size() - 1; j++) {
                double dist = distances[j];
                double searchDistLeft = searchDistance - distanceForward;
                distanceForward += dist;
                if (searchDistLeft < dist) {
                    double elevationDelta = geometry.getEle(j + 1) - geometry.getEle(j);
                    double elevationAtSearchDistance = geometry.getEle(j) + searchDistLeft / dist * elevationDelta;
                    elevationArea += searchDistLeft * (geometry.getEle(j) + elevationAtSearchDistance) / 2.0;
                    break;
                } else {
                    elevationArea += dist * (geometry.getEle(j + 1) + geometry.getEle(j)) / 2.0;
                }
            }

            double elevationAverage = elevationArea / (searchDistance * 2);
            averagedElevations.put(i, elevationAverage);
        }

        // after all pillar nodes got an averaged elevation, elevations are overwritten
        averagedElevations.forEach((Consumer<IntDoubleCursor>) c -> geometry.setElevation(c.key, c.value));
    }

    /**
     * This method removes elevation fluctuations up to maxElevationDelta. Compared to the smoothMovingAverage function
     * this method has the advantage that the maximum slope of a PointList never increases (max(abs(slope_i))).
     * The disadvantage is that the appearance might be still more spiky (at tower nodes) as a result when a bigger
     * positive slope changes to a bigger negative slope.
     * <p>
     * The underlying algorithm is an adapted Ramer-Douglas-Peucker algorithm (see #2634) with a maximum elevation change and:
     * 1. only elevation changes are considered and any lat,lon difference is ignored
     * 2. instead of removing the point the elevation will be calculated from the average slope of the first and last
     * point of the specified pointList
     */
    public static void smoothRamer(PointList pointList, double maxElevationDelta) {
        internSmoothRamer(pointList, 0, pointList.size() - 1, maxElevationDelta);
    }

    static void internSmoothRamer(PointList pointList, int fromIndex, int lastIndex, double maxElevationDelta) {
        if (lastIndex - fromIndex < 2)
            return;

        double prevLat = pointList.getLat(fromIndex);
        double prevLon = pointList.getLon(fromIndex);
        double dist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, pointList.getLat(lastIndex), pointList.getLon(lastIndex));

        // in rare cases the first point can be identical to the last for e.g. areas (or for things like man_made=pier which are not explicitly excluded from adding edges)
        double averageSlope = dist2D == 0 ? 0 : (pointList.getEle(lastIndex) - pointList.getEle(fromIndex)) / dist2D;
        double prevAverageSlopeEle = pointList.getEle(fromIndex);
        double maxEleDelta = -1;
        int indexWithMaxDelta = -1;
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            double lat = pointList.getLat(i);
            double lon = pointList.getLon(i);
            double ele = pointList.getEle(i);
            double tmpDist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, lat, lon);
            double eleFromAverageSlope = averageSlope * tmpDist2D + prevAverageSlopeEle;
            double tmpEleDelta = Math.abs(ele - eleFromAverageSlope);
            if (maxEleDelta < tmpEleDelta) {
                indexWithMaxDelta = i;
                maxEleDelta = tmpEleDelta;
            }
            prevAverageSlopeEle = eleFromAverageSlope;
            prevLat = lat;
            prevLon = lon;
        }

        // the maximum elevation change limit filters away especially the smaller high frequent elevation changes,
        // which is likely the "noise" that we want to remove.
        if (indexWithMaxDelta < 0 || maxElevationDelta > maxEleDelta) {
            prevLat = pointList.getLat(fromIndex);
            prevLon = pointList.getLon(fromIndex);
            prevAverageSlopeEle = pointList.getEle(fromIndex);
            for (int i = fromIndex + 1; i < lastIndex; i++) {
                double lat = pointList.getLat(i);
                double lon = pointList.getLon(i);
                double tmpDist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, lat, lon);
                double eleFromAverageSlope = averageSlope * tmpDist2D + prevAverageSlopeEle;
                pointList.setElevation(i, eleFromAverageSlope);
                prevAverageSlopeEle = eleFromAverageSlope;
                prevLat = lat;
                prevLon = lon;
            }
        } else {
            internSmoothRamer(pointList, fromIndex, indexWithMaxDelta, maxElevationDelta);
            internSmoothRamer(pointList, indexWithMaxDelta, lastIndex, maxElevationDelta);
        }
    }
}
