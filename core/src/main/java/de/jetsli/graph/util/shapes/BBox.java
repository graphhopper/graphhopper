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
 * A simple bounding box - Use top-left and bottom right corner, although we use bottom-to-top
 * direction for latitude! See tests - e.g. 10, 20, 5, 25 and identical to GeoNetworks.
 *
 * See http://osgeo-org.1560.n6.nabble.com/Boundingbox-issue-for-discussion-td3875533.html
 *
 * So overall GeoNetwork seems to define the bounding box differently to ISO 19115 and GML Standard
 * (OGC 07-036 -> WGS84BoundingBox).
 *
 * Gute Übersicht:
 * http://www.geoinf.uni-jena.de/fileadmin/Geoinformatik/Lehre/Diplomarbeiten/DA_Andres.pdf
 *
 * @author Peter Karich
 */
public class BBox implements Shape {

    // latitude (phi),  longitude (theta)
    public final double lat1;
    public final double lon1;
    public final double lat2;
    public final double lon2;

    public BBox(double lat1, double lon1, double lat2, double lon2) {
        assert lat2 < lat1 : "second latitude should be smaller than the first";
        assert lon1 < lon2 : "second longitude should be bigger than the first";
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.lat2 = lat2;
        this.lon2 = lon2;
    }

    public static BBox createEarthMax() {
        return new BBox(90.0, -180.0, -90.0, 180.0);
    }

    public boolean intersectIfBottomLeftAndTopRight(BBox o) {
        return (o.lon1 < lon1 && o.lon2 > lon1 || o.lon1 < lon2 && o.lon1 >= lon1)
                && (o.lat1 < lat1 && o.lat2 >= lat1 || o.lat1 < lat2 && o.lat1 >= lat1);
    }

    @Override
    public boolean intersect(Shape s) {
        if (s instanceof BBox) {
            return intersect((BBox) s);
        } else if (s instanceof Circle) {
            return ((Circle) s).intersect(this);
        }
        throw new UnsupportedOperationException("unsupported shape");
    }

    public boolean intersect(Circle s) {
        return ((Circle) s).intersect(this);
    }

    public boolean intersect(BBox o) {
        return (o.lon1 < lon1 && o.lon2 > lon1 || o.lon1 < lon2 && o.lon1 >= lon1)
                && (o.lat1 < lat1 && o.lat1 >= lat2 || o.lat1 >= lat1 && o.lat2 < lat1);
    }

    @Override
    public boolean contains(double lat, double lon) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String toString() {
        return lat1 + "," + lon1 + " | " + lat2 + "," + lon2;
    }

    @Override
    public BBox getBBox() {
        return this;
    }
}
