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

/**
 * A simple bounding box defined as follows: minLon, maxLon followed by minLat which is south(!) and
 * maxLat. Equally to EX_GeographicBoundingBox in the ISO 19115 standard see
 * http://osgeo-org.1560.n6.nabble.com/Boundingbox-issue-for-discussion-td3875533.html
 *
 * Nice German overview:
 * http://www.geoinf.uni-jena.de/fileadmin/Geoinformatik/Lehre/Diplomarbeiten/DA_Andres.pdf
 *
 * @author Peter Karich
 */
public class BBox implements Shape {

    // longitude (theta) = x, latitude (phi) = y
    public final double minLon;
    public final double maxLon;
    public final double minLat;
    public final double maxLat;

    public BBox(double minLon, double maxLon, double minLat, double maxLat) {
        assert minLon < maxLon : "second longitude should be bigger than the first";
        assert minLat < maxLat : "second latitude should be smaller than the first";
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.minLat = minLat;
        this.maxLon = maxLon;
    }

    public static BBox createEarthMax() {
        return new BBox(-180.0, 180.0, -90.0, 90.0);
    }

    @Override
    public boolean intersect(Shape s) {
        if (s instanceof BBox)
            return intersect((BBox) s);
        else if (s instanceof Circle)
            return ((Circle) s).intersect(this);

        throw new UnsupportedOperationException("unsupported shape");
    }

    @Override
    public boolean contains(Shape s) {
        if (s instanceof BBox)
            return contains((BBox) s);
        else if (s instanceof Circle)
            return contains((Circle) s);

        throw new UnsupportedOperationException("unsupported shape");
    }

    public boolean intersect(Circle s) {
        return ((Circle) s).intersect(this);
    }

    public boolean intersect(BBox o) {
        // return (o.minLon < minLon && o.maxLon > minLon || o.minLon < maxLon && o.minLon >= minLon)
        //  && (o.maxLat < maxLat && o.maxLat >= minLat || o.maxLat >= maxLat && o.minLat < maxLat);
        return minLon < o.maxLon && minLat < o.maxLat && o.minLon < maxLon && o.minLat < maxLat;
    }

    @Override
    public boolean contains(double lat, double lon) {
        return lat < maxLat && lat >= minLat && lon < maxLon && lon >= minLon;
    }

    public boolean contains(BBox b) {
        return maxLat >= b.maxLat && minLat <= b.minLat && maxLon >= b.maxLon && minLon <= b.minLon;
    }

    public boolean contains(Circle c) {
        return contains(c.getBBox());
    }

    @Override
    public String toString() {
        return minLon + "," + maxLon + "," + minLat + "," + maxLat;
    }

    public String toLessPrecisionString() {
        return (float) minLon + "," + (float) maxLon + "," + (float) minLat + "," + (float) maxLat;
    }

    @Override
    public BBox getBBox() {
        return this;
    }
}
