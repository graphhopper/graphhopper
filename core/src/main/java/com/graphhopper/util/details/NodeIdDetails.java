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
package com.graphhopper.util.details;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.Parameters.DETAILS.NODE_ID;

/**
 * Calculate the Node Id segments of a Path
 *
 * @author Maximilian Sturm
 */
public class NodeIdDetails extends AbstractPathDetailsBuilder {

    private int nodeId = -1;
    private int edgeId = -1;

    public NodeIdDetails() {
        super(NODE_ID);
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int thisNodeId = nodeId(edge);
        int thisEdgeId = edgeId(edge);
        if (thisEdgeId != edgeId) {
            nodeId = thisNodeId;
            edgeId = thisEdgeId;
            return true;
        }
        return false;
    }

    private int nodeId(EdgeIteratorState edge) {
        if (nodeId == edge.getBaseNode())
            return edge.getAdjNode();
        return edge.getBaseNode();
    }

    private int edgeId(EdgeIteratorState edge) {
        if (edge instanceof VirtualEdgeIteratorState) {
            return GHUtility.getEdgeFromEdgeKey(((VirtualEdgeIteratorState) edge).getOriginalTraversalKey());
        } else {
            return edge.getEdge();
        }
    }

    @Override
    public Object getCurrentValue() {
        return nodeId;
    }
}
