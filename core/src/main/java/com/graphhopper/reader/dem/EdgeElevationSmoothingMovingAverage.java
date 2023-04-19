package com.graphhopper.reader.dem;

import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.cursors.IntDoubleCursor;
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
 * @author Christoph Lingg
 */
public class EdgeElevationSmoothingMovingAverage {
    public static void smooth(PointList geometry, double maxWindowSize) {
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
}
