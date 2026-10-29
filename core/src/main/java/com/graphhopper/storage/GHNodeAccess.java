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

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

/**
 * @author Peter Karich
 */
class GHNodeAccess implements NodeAccess {
    private final BaseGraphNodesAndEdges store;

    public GHNodeAccess(BaseGraphNodesAndEdges store) {
        this.store = store;
    }

    @Override
    public void ensureNode(int nodeId) {
        store.ensureNodeCapacity(nodeId);
    }

    @Override
    public final void setNode(int nodeId, double lat, double lon, double ele) {
        store.ensureNodeCapacity(nodeId);
        long nodePointer = store.toNodePointer(nodeId);
        if (store.getLatInt(nodePointer) != Helper.degreeToInt(lat) || store.getLonInt(nodePointer) != Helper.degreeToInt(lon))
            checkNoGeometryRelativeTo(nodeId);
        store.setLat(nodePointer, lat);
        store.setLon(nodePointer, lon);

        if (store.withElevation()) {
            // meter precision is sufficient for now
            store.setEle(store.toNodePointer(nodeId), ele);
            // Helper.ELE_UNKNOWN is a marker value for deferred elevation, don't let it poison bounds
            if (ele != Helper.ELE_UNKNOWN)
                store.bounds.update(lat, lon, ele);
        } else {
            store.bounds.update(lat, lon);
        }
    }

    // the first pillar is stored relative to nodeA, so moving nodeA would silently shift the pillars
    private void checkNoGeometryRelativeTo(int nodeId) {
        int edge = store.getEdgeRef(store.toNodePointer(nodeId));
        while (edge != EdgeIterator.NO_EDGE) {
            long edgePointer = store.toEdgePointer(edge);
            boolean isNodeA = store.getNodeA(edgePointer) == nodeId;
            if (isNodeA && store.getGeoRef(edgePointer) > 0)
                throw new IllegalStateException("Cannot change coordinates of node " + nodeId + " because edge " + edge
                        + " stores its geometry relative to it. Set the node coordinates before calling setWayGeometry.");
            edge = isNodeA ? store.getLinkA(edgePointer) : store.getLinkB(edgePointer);
        }
    }

    @Override
    public final double getLat(int nodeId) {
        return store.getLat(store.toNodePointer(nodeId));
    }

    @Override
    public final double getLon(int nodeId) {
        return store.getLon(store.toNodePointer(nodeId));
    }

    @Override
    public final double getEle(int nodeId) {
        if (!store.withElevation())
            throw new IllegalStateException("elevation is disabled");
        return store.getEle(store.toNodePointer(nodeId));
    }

    @Override
    public final void setTurnCostIndex(int index, int turnCostIndex) {
        if (store.withTurnCosts()) {
            // todo: remove ensure?
            store.ensureNodeCapacity(index);
            store.setTurnCostRef(store.toNodePointer(index), turnCostIndex);
        } else {
            throw new AssertionError("This graph does not support turn costs");
        }
    }

    @Override
    public final int getTurnCostIndex(int index) {
        if (store.withTurnCosts())
            return store.getTurnCostRef(store.toNodePointer(index));
        else
            throw new AssertionError("This graph does not support turn costs");
    }

    @Override
    public final boolean is3D() {
        return store.withElevation();
    }

    @Override
    public int getDimension() {
        return store.withElevation() ? 3 : 2;
    }
}
