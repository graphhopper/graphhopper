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
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.Parameters.Details.EDGE_KEY;

public class EdgeKeyDetails extends AbstractPathDetailsBuilder {

    private int edgeKey;

    public EdgeKeyDetails() {
        super(EDGE_KEY);
        edgeKey = -1;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int newEdgeKey = getEdgeKey(edge);
        if (newEdgeKey != edgeKey) {
            edgeKey = newEdgeKey;
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.edgeKey;
    }

    static int getEdgeKey(EdgeIteratorState edge) {
        if (edge instanceof VirtualEdgeIteratorState) {
            return ((VirtualEdgeIteratorState) edge).getOriginalEdgeKey();
        } else {
            return edge.getEdge() * 2 + (edge.get(EdgeIteratorState.REVERSE_STATE) ? 1 : 0);
        }
    }

}
