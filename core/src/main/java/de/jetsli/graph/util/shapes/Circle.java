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
package de.jetsli.graph.util.shapes;

import de.jetsli.graph.reader.CalcDistance;

/**
 * @author Peter Karich
 */
public class Circle implements Shape {

    // need an instance per circle??
    private static CalcDistance calc = new CalcDistance();
    private final double radiusInKm;
    private final double lat;
    private final double lon;
    private final double normedDist;
    private final BBox bbox;

    public Circle(double lat, double lon, double radiusInKm) {
        this.lat = lat;
        this.lon = lon;
        this.radiusInKm = radiusInKm;
        this.normedDist = calc.normalizeDist(radiusInKm);
        bbox = calc.createBBox(lat, lon, radiusInKm);
    }

    @Override
    public boolean contains(double lat1, double lon1) {
        return normDist(lat1, lon1) <= normedDist;
    }

    @Override
    public BBox getBBox() {
        return bbox;
    }

    private double normDist(double lat1, double lon1) {
        return calc.calcNormalizedDist(lat, lon, lat1, lon1);
    }

    @Override
    public boolean intersect(Shape o) {
        if (o instanceof Circle) {
            return intersect((Circle) o);
        } else if (o instanceof BBox)
            return intersect((BBox) o);

        return o.intersect(this);
    }

    public boolean intersect(BBox b) {
        // test top intersect
        if (lat > b.lat1) {
            if (lon < b.lon1)
                return normDist(b.lat1, b.lon1) <= normedDist;
            if (lon > b.lon2)
                return normDist(b.lat1, b.lon2) <= normedDist;
            return b.lat1 - bbox.lat2 > 0;
        }

        // test bottom intersect
        if (lat < b.lat2) {
            if (lon < b.lon1)
                return normDist(b.lat2, b.lon1) <= normedDist;
            if (lon > b.lon2)
                return normDist(b.lat2, b.lon2) <= normedDist;
            return bbox.lat1 - b.lat2 > 0;
        }

        // test middle intersect
        if (lon < b.lon1)
            return bbox.lon2 - b.lon1 > 0;
        if (lon > b.lon2)
            return b.lon2 - bbox.lon1 > 0;
        return true;
    }

    public boolean intersect(Circle c) {
        // necessary to improve speed?
        if (!getBBox().intersect(c.getBBox()))
            return false;

        return normDist(c.lat, c.lon) <= calc.normalizeDist(radiusInKm + c.radiusInKm);
    }

    @Override
    public String toString() {
        return lat + "," + lon + ", radius:" + radiusInKm;
    }
}
