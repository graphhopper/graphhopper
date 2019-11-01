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

import com.graphhopper.util.PointList;

/**
 * {@link NodeAccess} that allows adding additional points
 */
public class ExtendedNodeAccess implements NodeAccess {
    private final NodeAccess nodeAccess;
    private final PointList additionalNodes;
    private final int firstAdditionalNodeId;

    /**
     * @param nodeAccess            the node access this class delegates to
     * @param additionalNodes       the additional points that should be used
     * @param firstAdditionalNodeId the node id that is used for the first additional point (all other nodes will
     *                              use consecutive ids)
     */
    public ExtendedNodeAccess(NodeAccess nodeAccess, PointList additionalNodes, int firstAdditionalNodeId) {
        this.nodeAccess = nodeAccess;
        this.firstAdditionalNodeId = firstAdditionalNodeId;
        this.additionalNodes = additionalNodes;
    }

    @Override
    public void ensureNode(int nodeId) {
        nodeAccess.ensureNode(nodeId);
    }

    @Override
    public boolean is3D() {
        return nodeAccess.is3D();
    }

    @Override
    public int getDimension() {
        return nodeAccess.getDimension();
    }

    @Override
    public double getLatitude(int nodeId) {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getLatitude(nodeId - firstAdditionalNodeId);
        return nodeAccess.getLatitude(nodeId);
    }

    @Override
    public double getLongitude(int nodeId) {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getLongitude(nodeId - firstAdditionalNodeId);
        return nodeAccess.getLongitude(nodeId);
    }

    @Override
    public double getElevation(int nodeId) {
        if (isAdditionalNode(nodeId))
            return additionalNodes.getElevation(nodeId - firstAdditionalNodeId);
        return nodeAccess.getElevation(nodeId);
    }

    @Override
    public int getAdditionalNodeField(int nodeId) {
        if (isAdditionalNode(nodeId))
            return 0;
        return nodeAccess.getAdditionalNodeField(nodeId);
    }

    @Override
    public void setNode(int nodeId, double lat, double lon) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setNode(int nodeId, double lat, double lon, double ele) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setAdditionalNodeField(int nodeId, int additionalValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getLat(int nodeId) {
        return getLatitude(nodeId);
    }

    @Override
    public double getLon(int nodeId) {
        return getLongitude(nodeId);
    }

    @Override
    public double getEle(int nodeId) {
        return getElevation(nodeId);
    }

    private boolean isAdditionalNode(int nodeId) {
        return nodeId >= firstAdditionalNodeId;
    }
}
