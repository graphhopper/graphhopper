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

import com.graphhopper.util.shapes.GHPoint3D;
import com.vividsolutions.jts.geom.LineString;

import java.util.Iterator;
import java.util.List;

/**
 * Slim list to store several points (without the need for a point object).
 * <p>
 *
 * @author Peter Karich
 */
public class ShallowImmutablePointList extends PointList {

    private final static String IMMUTABLE_ERR = "This class is immutable, you are not allowed to change it";

    private final int fromOffset;
    private final int toOffset;
    private final PointList wrappedPointList;

    public ShallowImmutablePointList(int fromOffset, int toOffset, PointList wrappedPointList) {
        if (fromOffset > toOffset)
            throw new IllegalArgumentException("from must be smaller or equal to end");
        if (fromOffset < 0 || toOffset > wrappedPointList.getSize())
            throw new IllegalArgumentException("Illegal interval: " + fromOffset + ", " + toOffset);
        this.fromOffset = fromOffset;
        this.toOffset = toOffset;
        this.wrappedPointList = wrappedPointList;
    }

    @Override
    public int size() {
        return toOffset - fromOffset;
    }

    @Override
    public int getSize() {
        return size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public double getLatitude(int index) {
        if (index > getSize())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + getSize());
        return wrappedPointList.getLatitude(fromOffset + index);
    }

    @Override
    public double getLongitude(int index) {
        if (index > getSize())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + getSize());
        return wrappedPointList.getLongitude(fromOffset + index);
    }

    @Override
    public double getElevation(int index) {
        if (index > getSize())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + getSize());
        return wrappedPointList.getElevation(fromOffset + index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < getSize(); i++) {
            if (i > 0)
                sb.append(", ");

            sb.append('(');
            sb.append(getLatitude(i));
            sb.append(',');
            sb.append(getLongitude(i));
            if (is3D) {
                sb.append(',');
                sb.append(getElevation(i));
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /*
    Wrapping Part
     */

    @Override
    public boolean is3D() {
        return wrappedPointList.is3D();
    }

    @Override
    public int getDimension() {
        return wrappedPointList.getDimension();
    }


    /*
    Immutable forbidden part
     */

    @Override
    public void ensureNode(int nodeId) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void setNode(int nodeId, double lat, double lon) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void setNode(int nodeId, double lat, double lon, double ele) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void set(int index, double lat, double lon, double ele) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void add(double lat, double lon, double ele) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void add(PointList points) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void reverse() {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void trimToSize(int newSize) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public void parse2DJSON(String str) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    /*
    Not exactly a mutation, but shouldn't be needed
     */

    @Override
    public List<Double[]> toGeoJson(boolean includeElevation) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public PointList clone(boolean reverse) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public PointList copy(final int from, final int end) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public PointList shallowCopy(final int from, final int end) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

    @Override
    public double calcDistance(DistanceCalc calc) {
        throw new UnsupportedOperationException(IMMUTABLE_ERR);
    }

}
