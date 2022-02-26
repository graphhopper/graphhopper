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

/**
 * This is a shallow copy of a PointList. This class can be used to create a memory efficient copy of a PointList.
 * You have to be aware, that if the wrapped PointList changes, the shallow copy will change as well. This can lead to
 * unexpected results. We recommend making the wrapped PointList immutable {@link PointList#makeImmutable()}.
 *
 * @author Robin Boldt
 */
public final class ShallowImmutablePointList extends PointList {

    private final static String IMMUTABLE_ERR = "This class is immutable, you are not allowed to change it";

    final int fromOffset;
    final int toOffset;
    final PointList wrappedPointList;

    public ShallowImmutablePointList(int fromOffset, int toOffset, PointList wrappedPointList) {
        if (fromOffset > toOffset)
            throw new IllegalArgumentException("from must be smaller or equal to end");
        if (fromOffset < 0 || toOffset > wrappedPointList.size())
            throw new IllegalArgumentException("Illegal interval: " + fromOffset + ", " + toOffset);
        this.fromOffset = fromOffset;
        this.toOffset = toOffset;
        this.wrappedPointList = wrappedPointList;
    }

    @Override
    public int size() {
        return toOffset - fromOffset;
    }

    public int getSize() {
        return size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    public String getIntervalString() {
        return "[" + fromOffset + ", " + toOffset + "[";
    }

    @Override
    public double getLat(int index) {
        if (index > size())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size());
        return wrappedPointList.getLat(fromOffset + index);
    }

    @Override
    public double getLon(int index) {
        if (index > size())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size());
        return wrappedPointList.getLon(fromOffset + index);
    }

    @Override
    public double getEle(int index) {
        if (index > size())
            throw new ArrayIndexOutOfBoundsException(ERR_MSG + " index:" + index + ", size:" + size());
        return wrappedPointList.getEle(fromOffset + index);
    }

    @Override
    public void setElevation(int index, double ele) {
        wrappedPointList.setElevation(fromOffset + index, ele);
    }

    public PointList makeImmutable() {
        this.wrappedPointList.makeImmutable();
        return this;
    }

    @Override
    public boolean isImmutable() {
        return this.wrappedPointList.isImmutable();
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
    public void removeLastPoint() {
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

}
