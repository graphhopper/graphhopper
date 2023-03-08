/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.mapdb.Fun;

import java.util.Map;

/**
 * Represents a collection of GTFS shape points. Never saved in MapDB but constructed on the fly.
 */
public class Shape {
    public static GeometryFactory geometryFactory = new GeometryFactory();
    /** The shape itself */
    public LineString geometry;

    /** shape_dist_traveled for each point in the geometry. TODO how to handle shape dist traveled not specified, or not specified on all stops? */
    public double[] shape_dist_traveled;

    public Shape (GTFSFeed feed, String shape_id) {
        Map<Fun.Tuple2<String, Integer>, ShapePoint> points =
                feed.shape_points.subMap(new Fun.Tuple2(shape_id, null), new Fun.Tuple2(shape_id, Fun.HI));

        Coordinate[] coords = points.values().stream()
                .map(point -> new Coordinate(point.shape_pt_lon, point.shape_pt_lat))
                .toArray(i -> new Coordinate[i]);
        geometry = geometryFactory.createLineString(coords);
        shape_dist_traveled = points.values().stream().mapToDouble(point -> point.shape_dist_traveled).toArray();
    }

    public LineString getGeometryStartToEnd(double distTravelledStart, double distTravelledEnd, Coordinate departureStopCoordinates, Coordinate arrivalStopCoordinates) {
        CoordinateList coordinates = new CoordinateList();
        Coordinate[] shapePoints = geometry.getCoordinates();
        coordinates.add(departureStopCoordinates);
        boolean startProcessed = false;
        for (int i = 0; i < shape_dist_traveled.length; i++) {
            if (shape_dist_traveled[i] >= distTravelledStart) { // Point i is on the route
                if (!startProcessed) { // Start point has not been processed yet
                    if (i > 0) { // Start point is on an edge between 2 shape coordinates
                        coordinates.add(getPartialDistanceCoordinates(distTravelledStart, shape_dist_traveled[i - 1], shape_dist_traveled[i], shapePoints[i - 1], shapePoints[i]));
                    }
                    startProcessed = true;
                }
                if (shape_dist_traveled[i] >= distTravelledEnd) { // Point i is after the end of the route
                    coordinates.add(getPartialDistanceCoordinates(distTravelledEnd, shape_dist_traveled[i - 1], shape_dist_traveled[i], shapePoints[i - 1], shapePoints[i]));
                    break;
                } else {
                    coordinates.add(shapePoints[i]);
                }
            }
        }
        coordinates.add(arrivalStopCoordinates);
        return geometryFactory.createLineString(coordinates.toCoordinateArray());
    }

    private Coordinate getPartialDistanceCoordinates(double distancePoint, double distanceA, double distanceB, Coordinate pointA, Coordinate pointB) {
        double partRatio = (distancePoint - distanceA) / (distanceB - distanceA);
        return new Coordinate(pointA.x + (pointB.x - pointA.x) * partRatio, pointA.y + (pointB.y - pointA.y) * partRatio);
    }
}
