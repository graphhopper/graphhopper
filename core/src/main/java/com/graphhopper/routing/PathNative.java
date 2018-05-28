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
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;

/**
 * This class creates a Path from a DijkstraOneToMany algorithm
 *
 * @author Peter Karich
 */
public class PathNative extends Path {
    private final int[] parentNodes;
    private final int[] parentEdges;

    public PathNative(Graph g, Weighting weighting, int[] parentNodes, int[] parentEdges) {
        super(g, weighting);
        this.parentNodes = parentNodes;
        this.parentEdges = parentEdges;
    }

    @Override
    public Path extract() {
        if (endNode < 0)
            return this;

        int prevEdge = EdgeIterator.NO_EDGE;
        while (true) {
            int edgeId = parentEdges[endNode];
            if (!EdgeIterator.Edge.isValid(edgeId))
                break;

            processEdge(edgeId, endNode, prevEdge);
            prevEdge = edgeId;
            endNode = parentNodes[endNode];
        }
        reverseOrder();
        return setFound(true);
    }
}
