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
    private final BaseGraph baseGraph;
    private final boolean elevation;

    public GHNodeAccess(BaseGraph baseGraph, boolean withElevation) {
        this.baseGraph = baseGraph;
        this.elevation = withElevation;
    }

    @Override
    public void ensureNode(int nodeId) {
        baseGraph.ensureNodeIndex(nodeId);
    }

    @Override
    public final void setNode(int nodeId, double lat, double lon, double ele) {
        baseGraph.ensureNodeIndex(nodeId);
        long tmp = baseGraph.toNodePointer(nodeId);
        baseGraph.nodes.setInt(tmp + baseGraph.N_LAT, Helper.degreeToInt(lat));
        baseGraph.nodes.setInt(tmp + baseGraph.N_LON, Helper.degreeToInt(lon));

        if (is3D()) {
            // meter precision is sufficient for now
            baseGraph.nodes.setInt(tmp + baseGraph.N_ELE, Helper.eleToInt(ele));
            baseGraph.bounds.update(lat, lon, ele);

        } else {
            baseGraph.bounds.update(lat, lon);
        }
    }

    @Override
    public final double getLat(int nodeId) {
        return Helper.intToDegree(baseGraph.nodes.getInt(baseGraph.toNodePointer(nodeId) + baseGraph.N_LAT));
    }

    @Override
    public final double getLon(int nodeId) {
        return Helper.intToDegree(baseGraph.nodes.getInt(baseGraph.toNodePointer(nodeId) + baseGraph.N_LON));
    }

    @Override
    public final double getEle(int nodeId) {
        if (!elevation)
            throw new IllegalStateException("Cannot access elevation - 3D is not enabled");

        return Helper.intToEle(baseGraph.nodes.getInt(baseGraph.toNodePointer(nodeId) + baseGraph.N_ELE));
    }

    public final void setTurnCostIndex(int index, int turnCostIndex) {
        if (baseGraph.supportsTurnCosts()) {
            baseGraph.ensureNodeIndex(index);
            long tmp = baseGraph.toNodePointer(index);
            baseGraph.nodes.setInt(tmp + baseGraph.N_TC, turnCostIndex);
        } else {
            throw new AssertionError("This graph does not support turn costs");
        }
    }

    @Override
    public final int getTurnCostIndex(int index) {
        if (baseGraph.supportsTurnCosts())
            return baseGraph.nodes.getInt(baseGraph.toNodePointer(index) + baseGraph.N_TC);
        else
            throw new AssertionError("This graph does not support turn costs");
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
