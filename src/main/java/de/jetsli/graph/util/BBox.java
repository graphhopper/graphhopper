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

import de.jetsli.graph.reader.CalcDistance;

/**
 * A simple bounding box
 *
 * @author Peter Karich
 */
public class BBox {

    // latitude (phi),  longitude (theta)
    public float lat1;
    public float lon1;
    public float lat2;
    public float lon2;

    public BBox(float lat1, float lon1, float lat2, float lon2) {
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.lat2 = lat2;
        this.lon2 = lon2;
    }

    public static BBox create(float lat, float lon, float radiusInKm, CalcDistance calc) {
        if (radiusInKm <= 0)
            throw new IllegalArgumentException("Distance cannot be 0 or negative! " + radiusInKm + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        float dLon = (float) (360 / (calc.calcCircumference(lat) / radiusInKm));

        // length of a circle is independent of the longitude
        float dLat = (float) (360 / (CalcDistance.C / radiusInKm));

        // Now return bounding box in coordinates. As we use the unmodified bottom to top 
        // coordinate system we need to use bottom-left and top-right corner to specify bounding box 
        // for our specific intersect method (designed for the top-to-bottom case). See tests.
        return new BBox(lat - dLat, lon - dLon, lat + dLat, lon + dLon);
    }

    public static BBox createEarthMax() {
        return new BBox(-90, -180, 90, 180);
    }

    public boolean intersect(BBox o) {
        return (o.lon1 < lon1 && o.lon2 > lon1 || o.lon1 < lon2 && o.lon1 >= lon1)
                && (o.lat1 < lat1 && o.lat2 >= lat1 || o.lat1 < lat2 && o.lat1 >= lat1);
    }

    @Override
    public String toString() {
        return lat1 + "," + lon1 + " | " + lat2 + "," + lon2;
    }
}
