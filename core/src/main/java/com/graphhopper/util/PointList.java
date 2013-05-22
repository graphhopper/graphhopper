/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.graphhopper.util.shapes.GHPlace;

/**
 * Slim list to store several points (without the need for a point object).
 *
 * @author Peter Karich
 */
public class PointList {

    private double[] latitudes;
    private double[] longitudes;
    private int size = 0;

    public PointList() {
        this(10);
    }

    public PointList(int cap) {
        if (cap < 5)
            cap = 5;
        latitudes = new double[cap];
        longitudes = new double[cap];
    }

    public void set(int index, double lat, double lon) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException("index has to be smaller than size " + size);

        latitudes[index] = lat;
        longitudes[index] = lon;
    }

    public void add(double lat, double lon) {
        int newSize = size + 1;
        if (newSize >= latitudes.length) {
            int cap = (int) (newSize * 1.7);
            latitudes = Arrays.copyOf(latitudes, cap);
            longitudes = Arrays.copyOf(longitudes, cap);
        }

        latitudes[size] = lat;
        longitudes[size] = lon;
        size = newSize;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Get the latitude of the point at the passed index in the list
     * @param index
     * @return
     * @throws ArrayIndexOutOfBoundsException if index is out of bounds
     */
    public double latitude(int index) {
        if (index >= size)
            throw new ArrayIndexOutOfBoundsException("Tried to access PointList with too big index! "
                    + "index:" + index + ", size:" + size);
        return latitudes[index];
    }

	/**
	 * Get the longitude of the point at the passed index in the list
	 * 
	 * @param index
	 * @return
	 * @throws ArrayIndexOutOfBoundsException
	 *             if index is out of bounds
	 */
	public double longitude(int index) {
		if (index >= size)
			throw new ArrayIndexOutOfBoundsException(
					"Tried to access PointList with too big index! " + "index:"
							+ index + ", size:" + size);
		return longitudes[index];
	}

	/**
	 * Get the lat/lon of the point at the passed index in the list
	 * 
	 * @param index
	 * @return
	 * @throws ArrayIndexOutOfBoundsException
	 *             if index is out of bounds
	 */
	public GHPlace point(int index) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException(
					"Tried to access PointList with too big index! " + "index:"
							+ index + ", size:" + size);
		}
		return new GHPlace(latitudes[index], longitudes[index]);
	}

    public void reverse() {
        int max = size / 2;
        for (int i = 0; i < max; i++) {
            int swapIndex = size - i - 1;

            double tmp = latitudes[i];
            latitudes[i] = latitudes[swapIndex];
            latitudes[swapIndex] = tmp;

            tmp = longitudes[i];
            longitudes[i] = longitudes[swapIndex];
            longitudes[swapIndex] = tmp;
        }
    }

    public void clear() {
        size = 0;
    }

    public void trimToSize(int newSize) {
        if (newSize > size)
            throw new IllegalArgumentException("new size needs be smaller than old size");
        size = newSize;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0)
                sb.append(", ");

            sb.append('(');
            sb.append(latitudes[i]);
            sb.append(',');
            sb.append(longitudes[i]);
            sb.append(')');
        }
        return sb.toString();
    }
    
    public String toWkt() {
    	StringBuilder sb = new StringBuilder("LINESTRING(");
    	for (int i = 0; i < size; i++) {
            if (i > 0)
                sb.append(", ");

            sb.append(longitudes[i])
              .append(' ')
              .append(latitudes[i]);
        }
    	sb.append(')');
    	return sb.toString();
    }

    /**
     * Attention: geoJson is LON,LAT
     */
    public List<Double[]> toGeoJson() {
        ArrayList<Double[]> points = new ArrayList<Double[]>(size);
        for (int i = 0; i < size; i++) {
            points.add(new Double[]{longitude(i), latitude(i)});
        }
        return points;
    }
    private final static int PRECISION = 100000;

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;
        final PointList other = (PointList) obj;
        if (this.size != other.size)
            return false;
        for (int i = 0; i < size; i++) {
            if (Math.round(latitudes[i] * PRECISION) != Math.round(other.latitudes[i] * PRECISION))
                return false;
            if (Math.round(longitudes[i] * PRECISION) != Math.round(other.longitudes[i] * PRECISION))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        for (int i = 0; i < latitudes.length; i++) {
            hash = 73 * hash + (int) Math.round(latitudes[i] * PRECISION);
        }
        hash = 73 * hash + this.size;
        return hash;
    }

    public double calculateDistance(DistanceCalc calc) {
        double lat = -1;
        double lon = -1;
        double dist = 0;
        for (int i = 0; i < size; i++) {
            if (i > 0)
                dist += calc.calcDist(lat, lon, latitudes[i], longitudes[i]);
            lat = latitudes[i];
            lon = longitudes[i];
        }
        return dist;
    }

    public PointList trimToSize() {
        // 1 free point is ok
        if (latitudes.length <= size + 1)
            return this;

        latitudes = Arrays.copyOf(latitudes, size);
        longitudes = Arrays.copyOf(longitudes, size);
        return this;
    }
    public static final PointList EMPTY = new PointList(0) {
        @Override public void set(int index, double lat, double lon) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override public void add(double lat, double lon) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override public double latitude(int index) {
            throw new RuntimeException("cannot access EMPTY PointList");
        }

        @Override public double longitude(int index) {
            throw new RuntimeException("cannot access EMPTY PointList");
        }

        @Override public void clear() {
            throw new RuntimeException("cannot change EMPTY PointList");
        }

        @Override public void trimToSize(int newSize) {
            throw new RuntimeException("cannot change EMPTY PointList");
        }
    };
}
