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
package com.graphhopper.routing.util;

import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Only accepts edges leading to equal and higher level nodes. Used in CH preparation.
 * <p>
 *
 * @author Peter Karich
 */
public class LevelEdgeFilter implements EdgeFilter {
    private final CHGraph graph;
    private final int maxNodes;

    public LevelEdgeFilter(CHGraph g) {
        graph = g;
        maxNodes = g.getNodes();
    }

    @Override
    public boolean accept(EdgeIteratorState edgeIterState) {
        int base = edgeIterState.getBaseNode();
        int adj = edgeIterState.getAdjNode();
        // always accept virtual edges, see #288
        if (base >= maxNodes || adj >= maxNodes)
            return true;

        // minor performance improvement: shortcuts in wrong direction are disconnected, so no need to exclude them
        if (((CHEdgeIteratorState) edgeIterState).isShortcut())
            return true;

        return graph.getLevel(base) <= graph.getLevel(adj);
    }
}
