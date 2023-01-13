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
package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.graphhopper.util.Helper.round2;
import static com.graphhopper.util.Helper.round6;

/**
 * Slim list to store several points (without the need for a point object). Be aware that the PointList is closely
 * coupled with the {@link ShallowImmutablePointList} both are not designed for inheritance (but final is not possible if we keep it simple).
 *
 * @author Peter Karich
 */
public class PointList implements Iterable<GHPoint3D>, PointAccess {
    // should be thread-safe according to https://github.com/locationtech/jts/issues/512
    private static final GeometryFactory factory = new GeometryFactory();
    public static final PointList EMPTY = new PointList(0, true) {
        @Override
        public void set(int index, double lat, double lon, double ele) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void add(double lat, double lon, double ele) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void removeLastPoint() {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void clear() {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void setElevation(int index, double ele) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void trimToSize(int newSize) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public void parse2DJSON(String str) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override
        public PointList copy(int from, int end) {
            throw new RuntimeException("cannot copy EMPTY PointList");
        }

        @Override
        public PointList clone(boolean reverse) {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }

        @Override
        public double getLat(int index) {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }

        @Override
        public double getLon(int index) {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }

        @Override
        public double getEle(int index) {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }

        @Override
        public void reverse() {
            throw new UnsupportedOperationException("cannot change EMPTY PointList");
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public GHPoint3D get(int index) {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }

        @Override
        public boolean is3D() {
            throw new UnsupportedOperationException("cannot access EMPTY PointList");
        }
    };

    final static String ERR_MSG = "Tried to access PointList with too big index!";
    protected int size = 0;
    protected boolean is3D;
    private double[] latitudes;
    private double[] longitudes;
    private double[] elevations;
    private boolean isImmutable = false;
    private LineString cachedLineString;

    public PointList() {
        this(10, false);
    }

    public PointList(int cap, boolean is3D) {
        latitudes = new double[cap];
        longitudes = new double[cap];
        this.is3D = is3D;
        if (is3D)
            elevations = new double[cap];
    }

    @Override
    public boolean is3D() {
        return is3D;
    }

    @Override
    public int getDimension() {
        if (is3D)
            return 3;
        return 2;
    }

    @Override
    public void ensureNode(int nodeId) {
        incCap(nodeId + 1);
    }

    @Override
    public void setNode(int nodeId, double lat, double lon, double ele) {
        set(nodeId, lat, lon, ele);
    }

    public void set(int index, double lat, double lon, double ele) {
        ensureMutability();
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException("index has to be smaller than size " + size);

        latitudes[index] = lat;
        longitudes[index] = lon;
        if (is3D)
            elevations[index] = ele;
        else if (!Double.isNaN(ele))
            throw new IllegalStateException("This is a 2D list we cannot store elevation: " + ele);
    }

    private void incCap(int newSize) {
        if (newSize <= latitudes.length)
            return;

        int cap = newSize * 2;
        if (cap < 15)
            cap = 15;
        latitudes = Arrays.copyOf(latitudes, cap);
        longitudes = Arrays.copyOf(longitudes, cap);
        if (is3D)
            elevations = Arrays.copyOf(elevations, cap);
    }

    public void add(double lat, double lon) {
        if (is3D)
            throw new IllegalStateException("Cannot add point without elevation data in 3D mode");
        add(lat, lon, Double.NaN);
    }

    public void add(double lat, double lon, double ele) {
        ensureMutability();
        int newSize = size + 1;
        incCap(newSize);
        latitudes[size] = lat;
        longitudes[size] = lon;
        if (is3D)
            elevations[size] = ele;
        else if (!Double.isNaN(ele))
            throw new IllegalStateException("This is a 2D list we cannot store elevation: " + ele);
        size = newSize;
    }

    public void add(PointAccess nodeAccess, int index) {
        if (is3D)
            add(nodeAccess.getLat(index), nodeAccess.getLon(index), nodeAccess.getEle(index));
        else
            add(nodeAccess.getLat(index), nodeAccess.getLon(index));
    }

    public void add(GHPoint point) {
        if (is3D)
            add(point.lat, point.lon, ((GHPoint3D) point).ele);
        else
            add(point.lat, point.lon);
    }

    public void add(PointList points) {
        ensureMutability();
        int newSize = size + points.size();
        incCap(newSize);
        for (int i = 0; i < points.size(); i++) {
            int tmp = size + i;
            latitudes[tmp] = points.getLat(i);
            longitudes[tmp] = points.getLon(i);
            if (is3D)
                elevations[tmp] = points.getEle(i);
        }
        size = newSize;
    }

    public void removeLastPoint() {
        if (size == 0)
            throw new IllegalStateException("Cannot remove last point from empty PointList");
        size--;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public double getLat(int index) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size);

        return latitudes[index];
    }

    @Override
    public double getLon(int index) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size);

        return longitudes[index];
    }

    @Override
    public double getEle(int index) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size);
        if (!is3D)
            return Double.NaN;

        return elevations[index];
    }

    public void setElevation(int index, double ele) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size);
        if (!is3D)
            throw new IllegalStateException("This is a 2D PointList, you cannot set it's elevation");
        this.elevations[index] = ele;
    }

    public void reverse() {
        ensureMutability();
        // in-place reverse
        int max = size / 2;
        for (int i = 0; i < max; i++) {
            int swapIndex = size - i - 1;

            double tmp = latitudes[i];
            latitudes[i] = latitudes[swapIndex];
            latitudes[swapIndex] = tmp;

            tmp = longitudes[i];
            longitudes[i] = longitudes[swapIndex];
            longitudes[swapIndex] = tmp;

            if (is3D) {
                tmp = elevations[i];
                elevations[i] = elevations[swapIndex];
                elevations[swapIndex] = tmp;
            }
        }
    }

    public void clear() {
        ensureMutability();
        size = 0;
    }

    public void trimToSize(int newSize) {
        ensureMutability();
        if (newSize > size)
            throw new IllegalArgumentException("new size needs be smaller than old size");

        size = newSize;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            if (i > 0)
                sb.append(", ");

            sb.append('(');
            sb.append(this.getLat(i));
            sb.append(',');
            sb.append(this.getLon(i));
            if (this.is3D()) {
                sb.append(',');
                sb.append(this.getEle(i));
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public static PointList fromLineString(LineString lineString) {
        final PointList pointList = new PointList();
        for (Coordinate coordinate : lineString.getCoordinates()) {
            pointList.add(new GHPoint(coordinate.y, coordinate.x));
        }
        return pointList;
    }

    public LineString toLineString(boolean includeElevation) {
        Coordinate[] coordinates = new Coordinate[size() == 1 ? 2 : size()];
        for (int i = 0; i < size(); i++) {
            coordinates[i] = includeElevation ?
                    new Coordinate(
                            round6(this.getLon(i)),
                            round6(this.getLat(i)),
                            round2(this.getEle(i))) :
                    new Coordinate(
                            round6(this.getLon(i)),
                            round6(this.getLat(i)));
        }

        // special case as just 1 point is not supported in the specification #1412
        if (size() == 1)
            coordinates[1] = coordinates[0];
        return factory.createLineString(new PackedCoordinateSequence.Double(coordinates, includeElevation ? 3 : 2));
    }

    public LineString getCachedLineString(boolean includeElevation) {
        if (cachedLineString != null)
            return cachedLineString;
        if (!isImmutable)
            throw new IllegalArgumentException("Make PointList immutable before calling getCachedLineString");
        return cachedLineString = toLineString(includeElevation);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        PointList other = (PointList) obj;
        if (this.isEmpty() && other.isEmpty())
            return true;

        if (size() != other.size() || this.is3D() != other.is3D())
            return false;

        for (int i = 0; i < size(); i++) {
            if (!equalsEps(this.getLat(i), other.getLat(i)))
                return false;

            if (!equalsEps(this.getLon(i), other.getLon(i)))
                return false;

            if (this.is3D() && !equalsEps(this.getEle(i), other.getEle(i)))
                return false;
        }
        return true;
    }

    private final static double DEFAULT_PRECISION = 1e-6;

    public static boolean equalsEps(double d1, double d2) {
        return equalsEps(d1, d2, DEFAULT_PRECISION);
    }

    public static boolean equalsEps(double d1, double d2, double epsilon) {
        return Math.abs(d1 - d2) < epsilon;
    }

    /**
     * Clones this PointList. If this PointList was immutable, the cloned will be mutable. If this PointList was a
     * {@link ShallowImmutablePointList}, the cloned PointList will be a regular PointList.
     */
    public PointList clone(boolean reverse) {
        PointList clonePL = new PointList(size(), is3D());
        if (is3D())
            for (int i = 0; i < size(); i++) {
                clonePL.add(this.getLat(i), this.getLon(i), this.getEle(i));
            }
        else
            for (int i = 0; i < size(); i++) {
                clonePL.add(this.getLat(i), this.getLon(i));
            }
        if (reverse)
            clonePL.reverse();
        return clonePL;
    }

    /**
     * This method does a deep copy of this object for the specified range.
     *
     * @param from the copying of the old PointList starts at this index
     * @param end  the copying of the old PointList ends at the index before (i.e. end is exclusive)
     */
    public PointList copy(int from, int end) {
        if (from > end)
            throw new IllegalArgumentException("from must be smaller or equal to end");
        if (from < 0 || end > size())
            throw new IllegalArgumentException("Illegal interval: " + from + ", " + end + ", size:" + size());


        PointList thisPL = this;
        if (this instanceof ShallowImmutablePointList) {
            ShallowImmutablePointList spl = (ShallowImmutablePointList) this;
            thisPL = spl.wrappedPointList;
            from = spl.fromOffset + from;
            end = spl.fromOffset + end;
        }

        int len = end - from;
        PointList copyPL = new PointList(len, is3D());
        copyPL.size = len;
        copyPL.isImmutable = isImmutable();
        System.arraycopy(thisPL.latitudes, from, copyPL.latitudes, 0, len);
        System.arraycopy(thisPL.longitudes, from, copyPL.longitudes, 0, len);
        if (is3D())
            System.arraycopy(thisPL.elevations, from, copyPL.elevations, 0, len);
        return copyPL;
    }

    /**
     * Create a shallow copy of this Pointlist from from to end, excluding end.
     *
     * @param makeImmutable makes this PointList immutable. If you don't ensure the consistency it might happen that due to changes of this
     *                      object, the shallow copy might contain incorrect or corrupt data.
     */
    public PointList shallowCopy(final int from, final int end, boolean makeImmutable) {
        if (makeImmutable)
            this.makeImmutable();
        return new ShallowImmutablePointList(from, end, this);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        for (int i = 0; i < size(); i++) {
            hash = 73 * hash + (int) Math.round(this.getLat(i) * 1000000);
            hash = 73 * hash + (int) Math.round(this.getLon(i) * 1000000);
        }
        hash = 73 * hash + size();
        return hash;
    }

    /**
     * Takes the string from a json array ala [lon1,lat1], [lon2,lat2], ... and fills the list from
     * it.
     */
    public void parse2DJSON(String str) {
        for (String latlon : str.split("\\[")) {
            if (latlon.trim().length() == 0)
                continue;

            String[] ll = latlon.split(",");
            String lat = ll[1].replace("]", "").trim();
            add(Double.parseDouble(lat), Double.parseDouble(ll[0].trim()), Double.NaN);
        }
    }

    public GHPoint3D get(int index) {
        return new GHPoint3D(this.getLat(index), this.getLon(index), this.getEle(index));
    }

    public int getCapacity() {
        return latitudes.length;
    }

    @Override
    public Iterator<GHPoint3D> iterator() {
        return new Iterator<GHPoint3D>() {
            int counter = 0;

            @Override
            public boolean hasNext() {
                return counter < size();
            }

            @Override
            public GHPoint3D next() {
                if (counter >= size())
                    throw new NoSuchElementException();

                GHPoint3D point = PointList.this.get(counter);
                counter++;
                return point;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Not supported.");
            }
        };
    }

    public static PointList from(LineString lineString) {
        final PointList pointList = new PointList();
        for (Coordinate coordinate : lineString.getCoordinates()) {
            pointList.add(new GHPoint(coordinate.y, coordinate.x));
        }
        return pointList;
    }

    public boolean isImmutable() {
        return this.isImmutable;
    }

    /**
     * Once immutable, there is no way to make this object mutable again. This is done to ensure the consistency of
     * shallow copies. If you need to modify this object again, you have to create a deep copy of it.
     */
    public PointList makeImmutable() {
        this.isImmutable = true;
        return this;
    }

    private void ensureMutability() {
        if (this.isImmutable()) {
            throw new IllegalStateException("You cannot change an immutable PointList");
        }
    }
}
