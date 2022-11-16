package com.onthegomap.planetiler.geo;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.geom.util.GeometryTransformer;

/**
 * A collection of utilities for working with JTS data structures and geographic data.
 * <p>
 * "world" coordinates in this class refer to web mercator coordinates where the top-left/northwest corner of the map is
 * (0,0) and bottom-right/southeast corner is (1,1).
 */
public class GeoUtils {

    public static final GeometryFactory JTS_FACTORY = new GeometryFactory(PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    private static final double RADIANS_PER_DEGREE = Math.PI / 180;
    private static final double DEGREES_PER_RADIAN = 180 / Math.PI;
    /**
     * Transform latitude/longitude coordinates to web mercator where top-left corner of the planet is (0,0) and
     * bottom-right is (1,1).
     */
    private static final GeometryTransformer PROJECT_WORLD_COORDS = new GeometryTransformer() {
        @Override
        protected CoordinateSequence transformCoordinates(CoordinateSequence coords, Geometry parent) {
            CoordinateSequence copy = new PackedCoordinateSequence.Double(coords.size(), 2);
            for (int i = 0; i < coords.size(); i++) {
                copy.setOrdinate(i, 0, getWorldX(coords.getX(i)));
                copy.setOrdinate(i, 1, getWorldY(coords.getY(i)));
            }
            return copy;
        }
    };
    private static final double MAX_LAT = getWorldLat(-0.1);
    private static final double MIN_LAT = getWorldLat(1.1);

    // should not instantiate
    private GeoUtils() {
    }

    /**
     * Returns a copy of {@code geom} transformed from latitude/longitude coordinates to web mercator where top-left
     * corner of the planet is (0,0) and bottom-right is (1,1).
     */
    public static Geometry latLonToWorldCoords(Geometry geom) {
        return PROJECT_WORLD_COORDS.transform(geom);
    }

    /**
     * Returns the latitude for a web mercator {@code y} coordinate where 0 is the north edge of the map, 0.5 is the
     * equator, and 1 is the south edge of the map.
     */
    public static double getWorldLat(double y) {
        double n = Math.PI - 2 * Math.PI * y;
        return DEGREES_PER_RADIAN * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
    }

    /**
     * Returns the web mercator X coordinate for {@code longitude} where 0 is the international date line on the west
     * side, 1 is the international date line on the east side, and 0.5 is the prime meridian.
     */
    public static double getWorldX(double longitude) {
        return (longitude + 180) / 360 ;
    }

    /**
     * Returns the web mercator Y coordinate for {@code latitude} where 0 is the north edge of the map, 0.5 is the
     * equator, and 1 is the south edge of the map.
     */
    public static double getWorldY(double latitude) {
        if (latitude <= MIN_LAT) {
            return 1.1;
        }
        if (latitude >= MAX_LAT) {
            return -0.1;
        }
        double sin = Math.sin(latitude * RADIANS_PER_DEGREE);
        return 0.5 - 0.25 * Math.log((1 + sin) / (1 - sin)) / Math.PI;
    }

    public static Point point(Coordinate coord) {
        return JTS_FACTORY.createPoint(coord);
    }

}
