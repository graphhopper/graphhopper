/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.reader.CalcDistance;

/**
 * A simple bounding box - Use top-left and bottom right corner, although we use bottom-to-top
 * direction for latitude! See tests - e.g. 10, 20, 5, 25
 *
 * @author Peter Karich
 */
public class BBox {

    // latitude (phi),  longitude (theta)
    public double lat1;
    public double lon1;
    public double lat2;
    public double lon2;

    public BBox(double lat1, double lon1, double lat2, double lon2) {
        assert lat2 < lat1 : "second latitude should be smaller than the first";
        assert lon1 < lon2 : "second longitude should be bigger than the first";
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.lat2 = lat2;
        this.lon2 = lon2;
    }

    public double lat1() {
        return (double) lat1;
    }

    public double lat2() {
        return (double) lat2;
    }

    public double lon1() {
        return (double) lon1;
    }

    public double lon2() {
        return (double) lon2;
    }

//    public static BBox create(int lat1, int lon1, int lat2, int lon2) {
//        return new BBox(lat1, lon1, lat2, lon2);
//    }

    public static BBox create(double lat, double lon, double radiusInKm, CalcDistance calc) {
        if (radiusInKm <= 0)
            throw new IllegalArgumentException("Distance cannot be 0 or negative! " + radiusInKm + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        double dLon = (360 / (calc.calcCircumference(lat) / radiusInKm));

        // length of a circle is independent of the longitude
        double dLat = (360 / (CalcDistance.C / radiusInKm));

        // Now return bounding box in coordinates
        return new BBox(lat + dLat, lon - dLon, lat - dLat, lon + dLon);
    }

    public static BBox createEarthMax() {
        return new BBox(90.0, -180.0, -90.0, 180.0);
    }

    public boolean intersectIfBottomLeftAndTopRight(BBox o) {
        return (o.lon1 < lon1 && o.lon2 > lon1 || o.lon1 < lon2 && o.lon1 >= lon1)
                && (o.lat1 < lat1 && o.lat2 >= lat1 || o.lat1 < lat2 && o.lat1 >= lat1);
    }

    public boolean intersect(BBox o) {
        return (o.lon1 < lon1 && o.lon2 > lon1 || o.lon1 < lon2 && o.lon1 >= lon1)
                && (o.lat1 < lat1 && o.lat1 >= lat2 || o.lat1 >= lat1 && o.lat2 < lat1);
    }

    @Override
    public String toString() {
        return lat1 + "," + lon1 + " | " + lat2 + "," + lon2;
    }
}
