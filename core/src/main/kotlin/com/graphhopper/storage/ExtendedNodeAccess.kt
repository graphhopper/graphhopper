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

import com.graphhopper.util.PointList

/**
 * [NodeAccess] that allows adding additional points
 *
 * @param nodeAccess            the node access this class delegates to
 * @param additionalNodes       the additional points that should be used
 * @param firstAdditionalNodeId the node id that is used for the first additional point (all other nodes will
 *                              use consecutive ids)
 */
class ExtendedNodeAccess(
    private val nodeAccess: NodeAccess,
    private val additionalNodes: PointList,
    private val firstAdditionalNodeId: Int
) : NodeAccess {

    override fun ensureNode(nodeId: Int) {
        nodeAccess.ensureNode(nodeId)
    }

    override fun is3D(): Boolean = nodeAccess.is3D()

    override fun getDimension(): Int = nodeAccess.getDimension()

    override fun getLat(nodeId: Int): Double {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getLat(nodeId - firstAdditionalNodeId)
        return nodeAccess.getLat(nodeId)
    }

    override fun getLon(nodeId: Int): Double {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getLon(nodeId - firstAdditionalNodeId)
        return nodeAccess.getLon(nodeId)
    }

    override fun getEle(nodeId: Int): Double {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getEle(nodeId - firstAdditionalNodeId)
        return nodeAccess.getEle(nodeId)
    }

    override fun getTurnCostIndex(nodeId: Int): Int {
        if (isAdditionalNode(nodeId))
            return 0
        return nodeAccess.getTurnCostIndex(nodeId)
    }

    override fun setNode(nodeId: Int, lat: Double, lon: Double, ele: Double) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun setTurnCostIndex(nodeId: Int, additionalValue: Int) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    private fun isAdditionalNode(nodeId: Int): Boolean = nodeId >= firstAdditionalNodeId
}
