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
package com.graphhopper.storage

import com.graphhopper.util.Helper

/**
 * @author Peter Karich
 */
internal class GHNodeAccess(private val store: BaseGraphNodesAndEdges) : NodeAccess {

    override fun ensureNode(nodeId: Int) {
        store.ensureNodeCapacity(nodeId)
    }

    override fun setNode(nodeId: Int, lat: Double, lon: Double, ele: Double) {
        store.ensureNodeCapacity(nodeId)
        store.setLat(store.toNodePointer(nodeId), lat)
        store.setLon(store.toNodePointer(nodeId), lon)

        if (store.withElevation()) {
            // meter precision is sufficient for now
            store.setEle(store.toNodePointer(nodeId), ele)
            // Helper.ELE_UNKNOWN is a marker value for deferred elevation, don't let it poison bounds
            if (ele != Helper.ELE_UNKNOWN)
                store.bounds.update(lat, lon, ele)
        } else {
            store.bounds.update(lat, lon)
        }
    }

    override fun getLat(nodeId: Int): Double = store.getLat(store.toNodePointer(nodeId))

    override fun getLon(nodeId: Int): Double = store.getLon(store.toNodePointer(nodeId))

    override fun getEle(nodeId: Int): Double {
        if (!store.withElevation())
            throw IllegalStateException("elevation is disabled")
        return store.getEle(store.toNodePointer(nodeId))
    }

    override fun setTurnCostIndex(nodeId: Int, additionalValue: Int) {
        if (store.withTurnCosts()) {
            // todo: remove ensure?
            store.ensureNodeCapacity(nodeId)
            store.setTurnCostRef(store.toNodePointer(nodeId), additionalValue)
        } else {
            throw AssertionError("This graph does not support turn costs")
        }
    }

    override fun getTurnCostIndex(nodeId: Int): Int {
        if (store.withTurnCosts())
            return store.getTurnCostRef(store.toNodePointer(nodeId))
        else
            throw AssertionError("This graph does not support turn costs")
    }

    override fun is3D(): Boolean = store.withElevation()

    override fun getDimension(): Int = if (store.withElevation()) 3 else 2
}
