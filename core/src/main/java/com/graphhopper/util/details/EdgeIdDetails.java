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

import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.core.util.Parameters.Details.EDGE_ID;

/**
 * Calculate the Edge Id segments of a Path
 *
 * @author Robin Boldt
 */
public class EdgeIdDetails extends AbstractPathDetailsBuilder {

    private int edgeId = EdgeIterator.NO_EDGE;

    public EdgeIdDetails() {
        super(EDGE_ID);
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        edgeId = edgeId(edge);
        return true;
    }

    private int edgeId(EdgeIteratorState edge) {
        if (edge instanceof VirtualEdgeIteratorState) {
            return GHUtility.getEdgeFromEdgeKey(((VirtualEdgeIteratorState) edge).getOriginalEdgeKey());
        } else {
            return edge.getEdge();
        }
    }

    @Override
    public Object getCurrentValue() {
        return this.edgeId;
    }
}
