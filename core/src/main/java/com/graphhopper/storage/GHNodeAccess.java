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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;

/**
 * A helper class for GraphHopperStorage for its node access.
 * <p>
 *
 * @author Peter Karich
 */
class GHNodeAccess implements NodeAccess {
    private final BaseGraph that;
    private final boolean elevation;

    public GHNodeAccess(BaseGraph that, boolean withElevation) {
        this.that = that;
        this.elevation = withElevation;
    }

    @Override
    public void ensureNode(int nodeId) {
        that.ensureNodeIndex(nodeId);
    }

    @Override
    public final void setNode(int nodeId, double lat, double lon) {
        setNode(nodeId, lat, lon, Double.NaN);
    }

    @Override
    public final void setNode(int nodeId, double lat, double lon, double ele) {
        that.ensureNodeIndex(nodeId);
        long tmp = (long) nodeId * that.nodeEntryBytes;
        that.nodes.setInt(tmp + that.N_LAT, Helper.degreeToInt(lat));
        that.nodes.setInt(tmp + that.N_LON, Helper.degreeToInt(lon));

        if (is3D()) {
            // meter precision is sufficient for now
            that.nodes.setInt(tmp + that.N_ELE, Helper.eleToInt(ele));
            that.bounds.update(lat, lon, ele);

        } else {
            that.bounds.update(lat, lon);
        }

        // set the default value for the additional field of this node
        if (that.extStorage.isRequireNodeField())
            that.nodes.setInt(tmp + that.N_ADDITIONAL, that.extStorage.getDefaultNodeFieldValue());
    }

    @Override
    public final double getLatitude(int nodeId) {
        return Helper.intToDegree(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_LAT));
    }

    @Override
    public final double getLongitude(int nodeId) {
        return Helper.intToDegree(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_LON));
    }

    @Override
    public final double getElevation(int nodeId) {
        if (!elevation)
            throw new IllegalStateException("Cannot access elevation - 3D is not enabled");

        return Helper.intToEle(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_ELE));
    }

    @Override
    public final double getEle(int nodeId) {
        return getElevation(nodeId);
    }

    @Override
    public final double getLat(int nodeId) {
        return getLatitude(nodeId);
    }

    @Override
    public final double getLon(int nodeId) {
        return getLongitude(nodeId);
    }

    @Override
    public final void setAdditionalNodeField(int index, int additionalValue) {
        if (that.extStorage.isRequireNodeField() && that.N_ADDITIONAL >= 0) {
            that.ensureNodeIndex(index);
            long tmp = (long) index * that.nodeEntryBytes;
            that.nodes.setInt(tmp + that.N_ADDITIONAL, additionalValue);
        } else {
            throw new AssertionError("This graph does not provide an additional node field");
        }
    }

    @Override
    public final int getAdditionalNodeField(int index) {
        if (that.extStorage.isRequireNodeField() && that.N_ADDITIONAL >= 0)
            return that.nodes.getInt((long) index * that.nodeEntryBytes + that.N_ADDITIONAL);
        else
            throw new AssertionError("This graph does not provide an additional node field");
    }

    @Override
    public final boolean is3D() {
        return elevation;
    }

    @Override
    public int getDimension() {
        if (elevation)
            return 3;
        return 2;
    }
}
