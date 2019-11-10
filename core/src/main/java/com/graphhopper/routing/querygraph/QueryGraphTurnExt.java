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

package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostExtension;

/**
 * special {@link TurnCostExtension} that handles virtual nodes and edges
 */
class QueryGraphTurnExt extends TurnCostExtension {
    private final TurnCostExtension mainTurnExtension;
    private final int firstVirtualNodeId;
    private final int firstVirtualEdgeId;
    private final IntArrayList closestEdges;

    QueryGraphTurnExt(Graph mainGraph, IntArrayList closestEdges) {
        super(mainGraph.getTurnCostExtension());
        this.mainTurnExtension = mainGraph.getTurnCostExtension();
        this.firstVirtualNodeId = mainGraph.getNodes();
        this.firstVirtualEdgeId = mainGraph.getEdges();
        this.closestEdges = closestEdges;
    }

    @Override
    public long getTurnCostFlags(int edgeFrom, int nodeVia, int edgeTo) {
        if (isVirtualNode(nodeVia)) {
            return 0;
        } else if (isVirtualEdge(edgeFrom) || isVirtualEdge(edgeTo)) {
            if (isVirtualEdge(edgeFrom)) {
                edgeFrom = getOriginalEdge(edgeFrom);
            }
            if (isVirtualEdge(edgeTo)) {
                edgeTo = getOriginalEdge(edgeTo);
            }
            return mainTurnExtension.getTurnCostFlags(edgeFrom, nodeVia, edgeTo);
        } else {
            return mainTurnExtension.getTurnCostFlags(edgeFrom, nodeVia, edgeTo);
        }
    }

    @Override
    public boolean isUTurn(int edgeFrom, int edgeTo) {
        // detecting a u-turn from a virtual to a non-virtual edge requires looking at the original edge of the
        // virtual edge. however when we are turning between virtual edges we need to compare the virtual edge ids
        // see #1593
        if (isVirtualEdge(edgeFrom) && !isVirtualEdge(edgeTo)) {
            edgeFrom = getOriginalEdge(edgeFrom);
        } else if (!isVirtualEdge(edgeFrom) && isVirtualEdge(edgeTo)) {
            edgeTo = getOriginalEdge(edgeTo);
        }
        return mainTurnExtension.isUTurn(edgeFrom, edgeTo);
    }

    @Override
    public boolean isUTurnAllowed(int node) {
        // do not allow u-turns at virtual nodes, otherwise the route depends on whether or not there are virtual
        // via nodes, see #1672
        return !isVirtualNode(node);
    }

    private int getOriginalEdge(int edge) {
        return closestEdges.get((edge - firstVirtualEdgeId) / 4);
    }

    private boolean isVirtualNode(int nodeId) {
        return nodeId >= firstVirtualNodeId;
    }

    private boolean isVirtualEdge(int edgeId) {
        return edgeId >= firstVirtualEdgeId;
    }
}
