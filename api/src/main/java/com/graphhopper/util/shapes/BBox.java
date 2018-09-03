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
package com.graphhopper.util.shapes;

import com.graphhopper.util.Helper;
import com.graphhopper.util.NumHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple bounding box defined as follows: minLon, maxLon followed by minLat which is south(!) and
 * maxLat. Equally to EX_GeographicBoundingBox in the ISO 19115 standard see
 * http://osgeo-org.1560.n6.nabble.com/Boundingbox-issue-for-discussion-td3875533.html
 * <p>
 * Nice German overview:
 * http://www.geoinf.uni-jena.de/fileadmin/Geoinformatik/Lehre/Diplomarbeiten/DA_Andres.pdf
 * <p>
 *
 * @author Peter Karich
 */
public class BBox implements Shape, Cloneable {

    private final boolean elevation;
    // longitude (theta) = x, latitude (phi) = y, elevation = z
    public double minLon;
    public double maxLon;
    public double minLat;
    public double maxLat;
    public double minEle;
    public double maxEle;

    public BBox(double[] coords) {
        this(coords[0],coords[2],coords[1],coords[3]);
    }

    public BBox(double minLon, double maxLon, double minLat, double maxLat) {
        this(minLon, maxLon, minLat, maxLat, Double.NaN, Double.NaN, false);
    }

    public BBox(double minLon, double maxLon, double minLat, double maxLat, double minEle, double maxEle) {
        this(minLon, maxLon, minLat, maxLat, minEle, maxEle, true);
    }

    public BBox(double minLon, double maxLon, double minLat, double maxLat, double minEle, double maxEle, boolean elevation) {
        this.elevation = elevation;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.minLat = minLat;
        this.maxLon = maxLon;
        this.minEle = minEle;
        this.maxEle = maxEle;
    }

    /**
     * Prefills BBox with minimum values so that it can increase.
     */
    public static BBox createInverse(boolean elevation) {
        if (elevation) {
            return new BBox(Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.MAX_VALUE, -Double.MAX_VALUE, true);
        } else {
            return new BBox(Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.NaN, Double.NaN, false);
        }
    }

    public boolean hasElevation() {
        return elevation;
    }

    public void update(double lat, double lon) {
        if (lat > maxLat) {
            maxLat = lat;
        }

        if (lat < minLat) {
            minLat = lat;
        }

        if (lon > maxLon) {
            maxLon = lon;
        }
        if (lon < minLon) {
            minLon = lon;
        }
    }

    public void update(double lat, double lon, double elev) {
        if (elevation) {
            if (elev > maxEle) {
                maxEle = elev;
            }
            if (elev < minEle) {
                minEle = elev;
            }
        } else {
            throw new IllegalStateException("No BBox with elevation to update");
        }
        update(lat, lon);

    }

    /**
     * Calculates the intersecting BBox between this and the specified BBox
     *
     * @return the intersecting BBox or null if not intersecting
     */
    public BBox calculateIntersection(BBox bBox) {
        if (!this.intersect(bBox))
            return null;

        double minLon = Math.max(this.minLon, bBox.minLon);
        double maxLon = Math.min(this.maxLon, bBox.maxLon);
        double minLat = Math.max(this.minLat, bBox.minLat);
        double maxLat = Math.min(this.maxLat, bBox.maxLat);

        return new BBox(minLon, maxLon, minLat, maxLat);
    }

    @Override
    public BBox clone() {
        return new BBox(minLon, maxLon, minLat, maxLat, minEle, maxEle, elevation);
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

    @Override
    public boolean contains(Shape s) {
        if (s instanceof BBox) {
            return contains((BBox) s);
        } else if (s instanceof Circle) {
            return contains((Circle) s);
        }

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
        return lat <= maxLat && lat >= minLat && lon <= maxLon && lon >= minLon;
    }

    public boolean contains(BBox b) {
        return maxLat >= b.maxLat && minLat <= b.minLat && maxLon >= b.maxLon && minLon <= b.minLon;
    }

    public boolean contains(Circle c) {
        return contains(c.getBounds());
    }

    @Override
    public String toString() {
        String str = minLon + "," + maxLon + "," + minLat + "," + maxLat;
        if (elevation)
            str += "," + minEle + "," + maxEle;

        return str;
    }

    public String toLessPrecisionString() {
        return (float) minLon + "," + (float) maxLon + "," + (float) minLat + "," + (float) maxLat;
    }

    @Override
    public BBox getBounds() {
        return this;
    }

    @Override
    public GHPoint getCenter() {
        return new GHPoint((maxLat + minLat) / 2, (maxLon + minLon) / 2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        BBox b = (BBox) obj;
        // equals within a very small range
        return NumHelper.equalsEps(minLat, b.minLat) && NumHelper.equalsEps(maxLat, b.maxLat)
                && NumHelper.equalsEps(minLon, b.minLon) && NumHelper.equalsEps(maxLon, b.maxLon);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.minLon) ^ (Double.doubleToLongBits(this.minLon) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.maxLon) ^ (Double.doubleToLongBits(this.maxLon) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.minLat) ^ (Double.doubleToLongBits(this.minLat) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.maxLat) ^ (Double.doubleToLongBits(this.maxLat) >>> 32));
        return hash;
    }

    public boolean isValid() {
        // second longitude should be bigger than the first
        if (minLon >= maxLon)
            return false;

        // second latitude should be smaller than the first
        if (minLat >= maxLat)
            return false;

        if (elevation) {
            // equal elevation is okay
            if (minEle > maxEle)
                return false;

            if (Double.compare(maxEle, -Double.MAX_VALUE) == 0
                    || Double.compare(minEle, Double.MAX_VALUE) == 0)
                return false;
        }

        return Double.compare(maxLat, -Double.MAX_VALUE) != 0
                && Double.compare(minLat, Double.MAX_VALUE) != 0
                && Double.compare(maxLon, -Double.MAX_VALUE) != 0
                && Double.compare(minLon, Double.MAX_VALUE) != 0;
    }

    /**
     * @return array containing this bounding box. Attention: GeoJson is lon,lat! If 3D is gets even
     * worse: lon,lat,ele
     */
    public List<Double> toGeoJson() {
        List<Double> list = new ArrayList<>(4);
        list.add(Helper.round6(minLon));
        list.add(Helper.round6(minLat));
        // hmh
        if (elevation)
            list.add(Helper.round2(minEle));

        list.add(Helper.round6(maxLon));
        list.add(Helper.round6(maxLat));
        if (elevation)
            list.add(Helper.round2(maxEle));

        return list;
    }

    /**
     * @return an estimated area in m^2 using the mean value of latitudes for longitude distance
     */
    @Override
    public double calculateArea() {
        double meanLat = (maxLat + minLat) / 2;
        return Helper.DIST_PLANE.calcDist(meanLat, minLon, meanLat, maxLon)
                // left side should be equal to right side no mean value necessary
                * Helper.DIST_PLANE.calcDist(minLat, minLon, maxLat, minLon);
    }

    /**
     * This method creates a BBox out of a string in format lat1,lon1,lat2,lon2
     */
    public static BBox parseTwoPoints(String objectAsString) {
        String[] splittedObject = objectAsString.split(",");

        if (splittedObject.length != 4)
            throw new IllegalArgumentException("BBox should have 4 parts but was " + objectAsString);

        double minLat = Double.parseDouble(splittedObject[0]);
        double minLon = Double.parseDouble(splittedObject[1]);

        double maxLat = Double.parseDouble(splittedObject[2]);
        double maxLon = Double.parseDouble(splittedObject[3]);

        if (minLat > maxLat) {
            double tmp = minLat;
            minLat = maxLat;
            maxLat = tmp;
        }

        if (minLon > maxLon) {
            double tmp = minLon;
            minLon = maxLon;
            maxLon = tmp;
        }

        return new BBox(minLon, maxLon, minLat, maxLat);
    }

    /**
     * This method creates a BBox out of a string in format lon1,lon2,lat1,lat2
     */
    public static BBox parseBBoxString(String objectAsString) {
        String[] splittedObject = objectAsString.split(",");

        if (splittedObject.length != 4)
            throw new IllegalArgumentException("BBox should have 4 parts but was " + objectAsString);

        double minLon = Double.parseDouble(splittedObject[0]);
        double maxLon = Double.parseDouble(splittedObject[1]);

        double minLat = Double.parseDouble(splittedObject[2]);
        double maxLat = Double.parseDouble(splittedObject[3]);

        return new BBox(minLon, maxLon, minLat, maxLat);
    }

}
