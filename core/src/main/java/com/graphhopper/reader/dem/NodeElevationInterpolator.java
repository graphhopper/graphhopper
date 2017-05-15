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
package com.graphhopper.reader.dem;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.PointList;

/**
 * Interpolates elevations of inner nodes based on elevations of outer nodes.
 *
 * @author Alexey Valikov
 */
public class NodeElevationInterpolator {

    private final GraphHopperStorage storage;
    private final ElevationInterpolator elevationInterpolator = new ElevationInterpolator();

    public NodeElevationInterpolator(GraphHopperStorage storage) {
        this.storage = storage;
    }

    public void interpolateElevationsOfInnerNodes(int[] outerNodeIds, int[] innerNodeIds) {
        final int numberOfOuterNodes = outerNodeIds.length;
        if (numberOfOuterNodes == 0) {
            // do nothing
        } else if (numberOfOuterNodes == 1) {
            interpolateElevationsOfInnerNodesForOneOuterNode(outerNodeIds[0], innerNodeIds);
        } else if (numberOfOuterNodes == 2) {
            interpolateElevationsOfInnerNodesForTwoOuterNodes(outerNodeIds[0], outerNodeIds[1],
                    innerNodeIds);
        } else if (numberOfOuterNodes == 3) {
            interpolateElevationsOfInnerNodesForThreeOuterNodes(outerNodeIds[0], outerNodeIds[1],
                    outerNodeIds[2], innerNodeIds);
        } else if (numberOfOuterNodes > 3) {
            interpolateElevationsOfInnerNodesForNOuterNodes(outerNodeIds, innerNodeIds);
        }
    }

    private void interpolateElevationsOfInnerNodesForOneOuterNode(int outerNodeId,
                                                                  int[] innerNodeIds) {
        NodeAccess nodeAccess = storage.getNodeAccess();
        double ele = nodeAccess.getEle(outerNodeId);
        for (int innerNodeId : innerNodeIds) {
            double lat = nodeAccess.getLat(innerNodeId);
            double lon = nodeAccess.getLon(innerNodeId);
            nodeAccess.setNode(innerNodeId, lat, lon, ele);
        }
    }

    private void interpolateElevationsOfInnerNodesForTwoOuterNodes(int firstOuterNodeId,
                                                                   int secondOuterNodeId, int[] innerNodeIds) {
        final NodeAccess nodeAccess = storage.getNodeAccess();
        double lat0 = nodeAccess.getLat(firstOuterNodeId);
        double lon0 = nodeAccess.getLon(firstOuterNodeId);
        double ele0 = nodeAccess.getEle(firstOuterNodeId);

        double lat1 = nodeAccess.getLat(secondOuterNodeId);
        double lon1 = nodeAccess.getLon(secondOuterNodeId);
        double ele1 = nodeAccess.getEle(secondOuterNodeId);

        for (int innerNodeId : innerNodeIds) {
            double lat = nodeAccess.getLat(innerNodeId);
            double lon = nodeAccess.getLon(innerNodeId);
            double ele = elevationInterpolator.calculateElevationBasedOnTwoPoints(lat, lon, lat0,
                    lon0, ele0, lat1, lon1, ele1);
            nodeAccess.setNode(innerNodeId, lat, lon, ele);
        }
    }

    private void interpolateElevationsOfInnerNodesForThreeOuterNodes(int firstOuterNodeId,
                                                                     int secondOuterNodeId, int thirdOuterNodeId, int[] innerNodeIds) {
        NodeAccess nodeAccess = storage.getNodeAccess();
        double lat0 = nodeAccess.getLat(firstOuterNodeId);
        double lon0 = nodeAccess.getLon(firstOuterNodeId);
        double ele0 = nodeAccess.getEle(firstOuterNodeId);

        double lat1 = nodeAccess.getLat(secondOuterNodeId);
        double lon1 = nodeAccess.getLon(secondOuterNodeId);
        double ele1 = nodeAccess.getEle(secondOuterNodeId);

        double lat2 = nodeAccess.getLat(thirdOuterNodeId);
        double lon2 = nodeAccess.getLon(thirdOuterNodeId);
        double ele2 = nodeAccess.getEle(thirdOuterNodeId);

        for (int innerNodeId : innerNodeIds) {
            double lat = nodeAccess.getLat(innerNodeId);
            double lon = nodeAccess.getLon(innerNodeId);
            double ele = elevationInterpolator.calculateElevationBasedOnThreePoints(lat, lon, lat0,
                    lon0, ele0, lat1, lon1, ele1, lat2, lon2, ele2);
            nodeAccess.setNode(innerNodeId, lat, lon, ele);
        }
    }

    private void interpolateElevationsOfInnerNodesForNOuterNodes(int[] outerNodeIds,
                                                                 int[] innerNodeIds) {
        NodeAccess nodeAccess = storage.getNodeAccess();
        PointList pointList = new PointList(outerNodeIds.length, true);
        for (int outerNodeId : outerNodeIds) {
            pointList.add(nodeAccess.getLat(outerNodeId), nodeAccess.getLon(outerNodeId),
                    nodeAccess.getEle(outerNodeId));
        }
        for (int innerNodeId : innerNodeIds) {
            double lat = nodeAccess.getLat(innerNodeId);
            double lon = nodeAccess.getLon(innerNodeId);
            double ele = elevationInterpolator.calculateElevationBasedOnPointList(lat, lon,
                    pointList);
            nodeAccess.setNode(innerNodeId, lat, lon, ele);
        }
    }
}
