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

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Extract path from a time-dependent Dijkstra or A*
 * <p>
 *
 * @author Andrzej Oles
 */
public class TDPathExtractor extends PathExtractor {
    private boolean reverse;

    public static Path extractPath(Graph graph, Weighting weighting, SPTEntry sptEntry, boolean reverseDirection) {
        return new TDPathExtractor(graph, weighting, reverseDirection).extract(sptEntry, false);
    }

    protected TDPathExtractor(Graph graph, Weighting weighting, boolean reverseDirection) {
        super(graph, weighting);
        reverse = reverseDirection;
    }

    @Override
    protected void extractPath(SPTEntry sptEntry, boolean reverseDirection) {
        SPTEntry currEntry = sptEntry;
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            processEdge(currEntry);
            currEntry = currEntry.parent;
        }
        if (!reverse) {
            ArrayUtil.reverse(path.getEdges());
            ArrayUtil.reverse(path.getTimes());
        }
        setFromToNode(currEntry.adjNode, sptEntry.adjNode);
    }

    private void setFromToNode(int source, int target) {
        path.setFromNode(reverse ? target : source);
        path.setEndNode(reverse ? source : target);
    }

    private void processEdge(SPTEntry currEdge) {
        int edgeId = currEdge.edge;
        EdgeIteratorState iter = graph.getEdgeIteratorState(edgeId, currEdge.adjNode);
        path.addDistance(iter.getDistance());
        path.addTime((reverse ? -1 : 1) * (currEdge.time - currEdge.parent.time));
        path.addEdge(edgeId);
    }
}
