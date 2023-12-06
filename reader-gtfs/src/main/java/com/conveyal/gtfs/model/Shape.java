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
import com.graphhopper.Trip;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.mapdb.Fun;

import java.util.*;
import java.util.stream.Collectors;

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
    // use dist traveled in stop order
    public static List<Shape> fromDist(GTFSFeed feed, String shapeId, List<Fun.Tuple2<Trip.Stop, StopTime>> stops) {
        Map<Fun.Tuple2<String, Integer>, ShapePoint> points = feed.shape_points.subMap(
                new Fun.Tuple2(shapeId, null), new Fun.Tuple2(shapeId, Fun.HI)
        );
        List<ShapePoint> shapePoints = points.values().stream().toList();
        List<List<ShapePoint>> shps = new ArrayList<>();
        double lowerBoundary = -1;
        for(int i = 1; i < stops.size(); i++){
            Fun.Tuple2<Trip.Stop, StopTime> start = stops.get(i - 1);
            Fun.Tuple2<Trip.Stop, StopTime> stop = stops.get(i);
            lowerBoundary = i == 1? start.b.shape_dist_traveled: lowerBoundary;
            final double upperBoundary = stop.b.shape_dist_traveled;
            final double finalLowerBoundary = lowerBoundary;
            shps.add(shapePoints.stream()
                    .filter(p -> finalLowerBoundary <= p.shape_dist_traveled &&
                            p.shape_dist_traveled < upperBoundary)
                    .toList()
            );
            lowerBoundary = upperBoundary;
        }

        return shps.stream().map(Shape::new).toList();
    }
    // goes through the path in stop order
    public static List<Shape> fromStops(GTFSFeed feed, String shapeId, List<Trip.Stop> stops){
        Map<Fun.Tuple2<String, Integer>, ShapePoint> points = feed.shape_points.subMap(
                new Fun.Tuple2(shapeId, null), new Fun.Tuple2(shapeId, Fun.HI)
        );
        int cursor = 1;
        final Trip.Stop start = stops.get(0);
        final double startLat = start.geometry.getY();
        final double startLon = start.geometry.getX();
        List<List<ShapePoint>> shps = new ArrayList<>();
        List<ShapePoint> shp = new ArrayList<>();
        List<ShapePoint> shapePoints = points.values()
                .stream()
                .sorted(Comparator.comparing(x -> x.shape_pt_sequence))
                .toList(); // ensure correct order
        Iterator<Fun.Tuple2<Double, ShapePoint>> closestToStart = points.values()
                .stream()
                .map(s -> {
                    final double distance = distanceLatLon(startLat, startLon,
                            s.shape_pt_lat, s.shape_pt_lon);
                    return new Fun.Tuple2<>(distance, s);
                })
                .sorted(Comparator.comparing(x -> x.a))
                .iterator();

        final double stopLat = stops.get(cursor).geometry.getY();
        final double stopLon = stops.get(cursor).geometry.getX();
        double previousTotalDistance = -1;
        int previousStopIndex = -1;
        int closestPoints = 0;
        while (closestToStart.hasNext()) {
            Fun.Tuple2<Double, ShapePoint> close = closestToStart.next();
            if (closestPoints > 2) // 2 closest point is enough
                break;

            int i = shapePoints.indexOf(close.b);
            double prevCloseDistance = -1;
            int prevCloseDistanceIndex = -1;
            for(int j = Math.max(i, 0); j < shapePoints.size(); j++){
                ShapePoint stopPoint1 = shapePoints.get(j);
                double distance;
                if (j == i)
                    distance = distanceLatLon(stopLat, stopLon,
                            stopPoint1.shape_pt_lat, stopPoint1.shape_pt_lon);
                else {
                    ShapePoint stopPoint2 = shapePoints.get(j - 1);
                    distance = closestPointToLine(stopLat, stopLon,
                            stopPoint1.shape_pt_lat, stopPoint1.shape_pt_lon,
                            stopPoint2.shape_pt_lat, stopPoint2.shape_pt_lon);
                }
                if (prevCloseDistance == -1 || prevCloseDistance > distance){
                    prevCloseDistance = distance;
                    prevCloseDistanceIndex = Math.min(j + 1, shapePoints.size() - 1);
                }
            }
            if (prevCloseDistanceIndex == -1) // Start point at End point?
                continue;

            closestPoints++;
            double totalDistance = prevCloseDistance + close.a;
            if (previousTotalDistance == -1 || (previousTotalDistance > totalDistance)){
                previousTotalDistance = totalDistance;
                shp = new ArrayList<>(shapePoints.subList(i, prevCloseDistanceIndex));
                previousStopIndex = prevCloseDistanceIndex;
            }
        }
        cursor++;
        shps.add(shp);
        for(; cursor < stops.size(); cursor++){
            final double tripStopLatitude = stops.get(cursor).geometry.getY();
            final double tripStopLongitude = stops.get(cursor).geometry.getX();
            List<ShapePoint> currentPoints = shapePoints.subList(previousStopIndex,
                    shapePoints.size());
            Fun.Tuple2<Double, ShapePoint> bestPointDistance = null;
            for(int i = 1; i < currentPoints.size(); i++){
                final ShapePoint startPoint = currentPoints.get(i - 1);
                final ShapePoint stopPoint = currentPoints.get(i);
                double pointDistance = closestPointToLine(
                        tripStopLatitude, tripStopLongitude,
                        startPoint.shape_pt_lat, startPoint.shape_pt_lon,
                        stopPoint.shape_pt_lat, stopPoint.shape_pt_lon
                );
                if (bestPointDistance == null || (bestPointDistance.a > pointDistance))
                    bestPointDistance = new Fun.Tuple2<>(pointDistance, startPoint);
            }
            if (currentPoints.size() == 0){
                // it can be empty due to previous stop unable to be derived from.
                break;
            }
            ShapePoint bestPoint = bestPointDistance == null?
                    currentPoints.get(0): bestPointDistance.b;
            int bestStopPointIndex = shapePoints.indexOf(bestPoint) + 1;
            shps.add(new ArrayList<>(shapePoints.subList(previousStopIndex - 1, bestStopPointIndex)));
            previousStopIndex = bestStopPointIndex;
        }
        return shps.stream().map(Shape::new).toList();
    }
    public Shape(List<ShapePoint> shp){
        Coordinate[] coords = shp.stream()
                .map(point -> new Coordinate(point.shape_pt_lon, point.shape_pt_lat))
                .toArray(Coordinate[]::new);
        if (coords.length > 1)
            geometry = geometryFactory.createLineString(coords);
        else
            geometry = geometryFactory.createLineString();
        shape_dist_traveled = shp.stream()
                .mapToDouble(point -> point.shape_dist_traveled)
                .toArray();
    }
    // do pointA -> pointB combination, closest point, without shape dist traveled & shape_id
    public Shape(GTFSFeed feed, Point start, Point stop){
        Set<String> uniqueIds = feed.shape_points.keySet()
                .stream()
                .map(pair -> pair.a)
                .collect(Collectors.toSet());

        List<ShapePoint> finalShape = new ArrayList<>();
        final double startLat = start.getY();
        final double startLon = start.getX();
        final double stopLat = stop.getY();
        final double stopLon = stop.getX();
        double finalCloseDistance = -1;
        for (String shapeId: uniqueIds) {
            Map<Fun.Tuple2<String, Integer>, ShapePoint> points = feed.shape_points.subMap(
                    new Fun.Tuple2(shapeId, null), new Fun.Tuple2(shapeId, Fun.HI)
            );
            List<ShapePoint> shp = new ArrayList<>();
            List<ShapePoint> shapePoints = points.values()
                    .stream()
                    .sorted(Comparator.comparing(x -> x.shape_pt_sequence))
                    .toList();
            Iterator<Fun.Tuple2<Double, ShapePoint>> closestToStart = points.values()
                    .stream()
                    .map(s -> {
                        final double distance = distanceLatLon(startLat, startLon,
                                s.shape_pt_lat, s.shape_pt_lon);
                        return new Fun.Tuple2<>(distance, s);
                    })
                    .sorted(Comparator.comparing(x -> x.a))
                    .iterator();

            double previousTotalDistance = -1;
            int closestPoints = 0;
            while (closestToStart.hasNext()) {
                Fun.Tuple2<Double, ShapePoint> close = closestToStart.next();
                if (closestPoints > 2) // 2 closest point is enough
                    break;

                int i = shapePoints.indexOf(close.b);
                double prevCloseDistance = -1;
                int prevCloseDistanceIndex = -1;
                for(int j = Math.max(i, 0); j < shapePoints.size(); j++){
                    ShapePoint stopPoint1 = shapePoints.get(j);
                    double distance;
                    if (j == i)
                        distance = distanceLatLon(stopLat, stopLon,
                                stopPoint1.shape_pt_lat, stopPoint1.shape_pt_lon);
                    else {
                        ShapePoint stopPoint2 = shapePoints.get(j - 1);
                        distance = closestPointToLine(stopLat, stopLon,
                                stopPoint1.shape_pt_lat, stopPoint1.shape_pt_lon,
                                stopPoint2.shape_pt_lat, stopPoint2.shape_pt_lon);
                    }
                    if (prevCloseDistance == -1 || prevCloseDistance > distance){
                        prevCloseDistance = distance;
                        prevCloseDistanceIndex = Math.min(j + 1, shapePoints.size() - 1);
                    }
                }
                if (prevCloseDistanceIndex == -1) // Start point at End point?
                    continue;

                closestPoints++;
                double totalDistance = prevCloseDistance + close.a;
                if (previousTotalDistance == -1 || (previousTotalDistance > totalDistance)){
                    previousTotalDistance = totalDistance;
                    shp = new ArrayList<>(shapePoints.subList(i, prevCloseDistanceIndex));
                }
            }

            if (finalCloseDistance == -1 || (finalCloseDistance > previousTotalDistance)){
                finalCloseDistance = previousTotalDistance;
                finalShape = shp;
            }
        }
       Coordinate[] coords = finalShape.stream()
                .map(point -> new Coordinate(point.shape_pt_lon, point.shape_pt_lat))
                .toArray(Coordinate[]::new);
        if (coords.length > 1)
            geometry = geometryFactory.createLineString(coords);
        else
            geometry = geometryFactory.createLineString();
        shape_dist_traveled = finalShape.stream()
                .mapToDouble(point -> point.shape_dist_traveled)
                .toArray();
    }
    public static double closestPointToLine(double lat0, double lon0, // target point
                                             double lat1, double lon1, double lat2, double lon2){
        double distance;
        // this should only be use on close distances
        // due to Perpendicular Distance is for cartesian.
        // lat = y, lng = x
        if ((Math.min(lat1, lat2) < lat0 && lat0 < Math.max(lat1, lat2)) ||
                (Math.min(lon1, lon2) < lon0 && lon0 < Math.max(lon1, lon2))
        ){
            // Perpendicular Distance Formula
            double rawDist = Math.abs((lon1 - lon0) * (lat2 - lat1) - (lon2 - lon1) * (lat1 - lat0)) /
                    Math.sqrt(Math.pow((lon2 - lon1), 2) + Math.pow((lat2 - lat1), 2));
            distance = rawDist * 111000;
        }else{
            // use haversine formula if closest is not 90deg
            final double firstPoint = distanceLatLon(lat0, lon0, lat1, lon1);
            final double secondPoint = distanceLatLon(lat0, lon0, lat2, lon2);
            distance = Math.min(firstPoint, secondPoint);
        }
        return distance;
    }
    public static double distanceLatLon(double pt_lat1, double pt_lon1, double pt_lat2, double pt_lon2) {
        // haversine formula
        final double deltaLat = numberToRadius(pt_lat2 - pt_lat1);
        final double deltaLon = numberToRadius(pt_lon2 - pt_lon1);
        final double fhi1 = numberToRadius(pt_lat1);
        final double fhi2 = numberToRadius(pt_lat2);
        final double a = Math.pow(Math.sin(deltaLat / 2), 2) +
                Math.cos(fhi1) * Math.cos(fhi2) * Math.pow(Math.sin(deltaLon / 2), 2);

        final double R = 6371e3;  // returns meters
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    private static double numberToRadius(double number){
        return number * Math.PI / 180;
    }
}
