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
package com.graphhopper.reader.dem

import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.PointList

/**
 * Interpolates elevations of pillar nodes based on elevations of tower nodes.
 *
 * @author Alexey Valikov
 */
class NodeElevationInterpolator(private val graph: BaseGraph) {

    private val elevationInterpolator = ElevationInterpolator()

    fun interpolateElevationsOfInnerNodes(outerNodeIds: IntArray, innerNodeIds: IntArray) {
        val numberOfOuterNodes = outerNodeIds.size
        if (numberOfOuterNodes == 0) {
            // do nothing
        } else if (numberOfOuterNodes == 1) {
            interpolateElevationsOfInnerNodesForOneOuterNode(outerNodeIds[0], innerNodeIds)
        } else if (numberOfOuterNodes == 2) {
            interpolateElevationsOfInnerNodesForTwoOuterNodes(outerNodeIds[0], outerNodeIds[1], innerNodeIds)
        } else if (numberOfOuterNodes == 3) {
            interpolateElevationsOfInnerNodesForThreeOuterNodes(outerNodeIds[0], outerNodeIds[1], outerNodeIds[2], innerNodeIds)
        } else if (numberOfOuterNodes > 3) {
            interpolateElevationsOfInnerNodesForNOuterNodes(outerNodeIds, innerNodeIds)
        }
    }

    private fun interpolateElevationsOfInnerNodesForOneOuterNode(outerNodeId: Int, innerNodeIds: IntArray) {
        val nodeAccess = graph.nodeAccess
        val ele = nodeAccess.getEle(outerNodeId)
        for (innerNodeId in innerNodeIds) {
            val lat = nodeAccess.getLat(innerNodeId)
            val lon = nodeAccess.getLon(innerNodeId)
            nodeAccess.setNode(innerNodeId, lat, lon, ele)
        }
    }

    private fun interpolateElevationsOfInnerNodesForTwoOuterNodes(firstOuterNodeId: Int,
                                                                  secondOuterNodeId: Int, innerNodeIds: IntArray) {
        val nodeAccess = graph.nodeAccess
        val lat0 = nodeAccess.getLat(firstOuterNodeId)
        val lon0 = nodeAccess.getLon(firstOuterNodeId)
        val ele0 = nodeAccess.getEle(firstOuterNodeId)

        val lat1 = nodeAccess.getLat(secondOuterNodeId)
        val lon1 = nodeAccess.getLon(secondOuterNodeId)
        val ele1 = nodeAccess.getEle(secondOuterNodeId)

        for (innerNodeId in innerNodeIds) {
            val lat = nodeAccess.getLat(innerNodeId)
            val lon = nodeAccess.getLon(innerNodeId)
            val ele = elevationInterpolator.calculateElevationBasedOnTwoPoints(lat, lon, lat0, lon0, ele0,
                    lat1, lon1, ele1)
            nodeAccess.setNode(innerNodeId, lat, lon, ele)
        }
    }

    private fun interpolateElevationsOfInnerNodesForThreeOuterNodes(firstOuterNodeId: Int, secondOuterNodeId: Int,
                                                                    thirdOuterNodeId: Int, innerNodeIds: IntArray) {
        val nodeAccess = graph.nodeAccess
        val lat0 = nodeAccess.getLat(firstOuterNodeId)
        val lon0 = nodeAccess.getLon(firstOuterNodeId)
        val ele0 = nodeAccess.getEle(firstOuterNodeId)

        val lat1 = nodeAccess.getLat(secondOuterNodeId)
        val lon1 = nodeAccess.getLon(secondOuterNodeId)
        val ele1 = nodeAccess.getEle(secondOuterNodeId)

        val lat2 = nodeAccess.getLat(thirdOuterNodeId)
        val lon2 = nodeAccess.getLon(thirdOuterNodeId)
        val ele2 = nodeAccess.getEle(thirdOuterNodeId)

        for (innerNodeId in innerNodeIds) {
            val lat = nodeAccess.getLat(innerNodeId)
            val lon = nodeAccess.getLon(innerNodeId)
            val ele = elevationInterpolator.calculateElevationBasedOnThreePoints(lat, lon, lat0,
                    lon0, ele0, lat1, lon1, ele1, lat2, lon2, ele2)
            nodeAccess.setNode(innerNodeId, lat, lon, ele)
        }
    }

    private fun interpolateElevationsOfInnerNodesForNOuterNodes(outerNodeIds: IntArray,
                                                                innerNodeIds: IntArray) {
        val nodeAccess = graph.nodeAccess
        val pointList = PointList(outerNodeIds.size, true)
        for (outerNodeId in outerNodeIds) {
            pointList.add(nodeAccess.getLat(outerNodeId), nodeAccess.getLon(outerNodeId),
                    nodeAccess.getEle(outerNodeId))
        }
        for (innerNodeId in innerNodeIds) {
            val lat = nodeAccess.getLat(innerNodeId)
            val lon = nodeAccess.getLon(innerNodeId)
            val ele = elevationInterpolator.calculateElevationBasedOnPointList(lat, lon, pointList)
            nodeAccess.setNode(innerNodeId, lat, lon, ele)
        }
    }
}
