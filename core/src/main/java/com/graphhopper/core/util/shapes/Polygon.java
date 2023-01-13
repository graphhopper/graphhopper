/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.core.util.shapes;

import com.graphhopper.util.PointList;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedPolygon;

import java.util.Arrays;

/**
 * This class represents a polygon that is defined by a set of points.
 * Every point i is connected to point i-1 and i+1.
 *
 * @author Robin Boldt
 */
public class Polygon implements Shape {

    private static final GeometryFactory factory = new GeometryFactory();
    public final PreparedGeometry prepPolygon;
    public final boolean rectangle;
    public final Envelope envelope;

    public Polygon(PreparedPolygon prepPolygon) {
        this.prepPolygon = prepPolygon;
        this.rectangle = prepPolygon.getGeometry().isRectangle();
        this.envelope = prepPolygon.getGeometry().getEnvelopeInternal();
    }

    public Polygon(double[] lats, double[] lons) {
        if (lats.length != lons.length)
            throw new IllegalArgumentException("Points must be of equal length but was " + lats.length + " vs. " + lons.length);

        if (lats.length == 0)
            throw new IllegalArgumentException("Points must not be empty");

        Coordinate[] coordinates = new Coordinate[lats.length + 1];
        for (int i = 0; i < lats.length; i++) {
            coordinates[i] = new Coordinate(lons[i], lats[i]);
        }
        coordinates[lats.length] = coordinates[0];
        this.prepPolygon = new PreparedPolygon(factory.createPolygon(new PackedCoordinateSequence.Double(coordinates, 2)));
        this.rectangle = prepPolygon.getGeometry().isRectangle();
        this.envelope = prepPolygon.getGeometry().getEnvelopeInternal();
    }

    public static Polygon create(org.locationtech.jts.geom.Polygon polygon) {
        return new Polygon(new PreparedPolygon(polygon));
    }

    public boolean intersects(PointList pointList) {
        return prepPolygon.intersects(pointList.getCachedLineString(false));
    }

    /**
     * Does the point in polygon check.
     *
     * @param lat Latitude of the point to be checked
     * @param lon Longitude of the point to be checked
     * @return true if point is inside polygon
     */
    public boolean contains(double lat, double lon) {
        return prepPolygon.contains(factory.createPoint(new Coordinate(lon, lat)));
    }

    @Override
    public BBox getBounds() {
        return new BBox(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY());
    }

    public double getMinLat() {
        return envelope.getMinY();
    }

    public double getMinLon() {
        return envelope.getMinX();
    }

    public double getMaxLat() {
        return envelope.getMaxY();
    }

    public double getMaxLon() {
        return envelope.getMaxX();
    }

    public boolean isRectangle() {
        return rectangle;
    }

    @Override
    public String toString() {
        return "polygon (" + prepPolygon.getGeometry().getNumPoints() + " points," + prepPolygon.getGeometry().getNumGeometries() + " geometries)";
    }

    public static Polygon parsePoints(String pointsStr) {
        String[] arr = pointsStr.split(",");
        if (arr.length % 2 == 1)
            throw new IllegalArgumentException("incorrect polygon specified: " + Arrays.asList(arr));

        Coordinate[] coordinates = new Coordinate[arr.length / 2 + 1];
        for (int i = 0; i < coordinates.length - 1; i++) {
            int arrIndex = i * 2;
            coordinates[i] = new Coordinate(Double.parseDouble(arr[arrIndex + 1]), Double.parseDouble(arr[arrIndex]));
        }
        coordinates[coordinates.length - 1] = coordinates[0];

        // store more efficient
        return new Polygon(new PreparedPolygon(new GeometryFactory().createPolygon(new PackedCoordinateSequence.Double(coordinates, 2))));
    }
}
