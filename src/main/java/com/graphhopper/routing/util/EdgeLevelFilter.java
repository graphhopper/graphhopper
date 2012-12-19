/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;

/**
 * Only certain nodes are accepted and therefor the others are filtered out.
 *
 * @author Peter Karich
 */
public class EdgeLevelFilter implements EdgeIterator {

    private EdgeIterator edgeIter;
    protected LevelGraph graph;

    public EdgeLevelFilter(LevelGraph g) {
        graph = g;
    }

    public EdgeIterator doFilter(EdgeIterator iter) {
        this.edgeIter = iter;
        return this;
    }

    @Override public int baseNode() {
        return edgeIter.baseNode();
    }

    @Override public final int node() {
        return edgeIter.node();
    }

    @Override public final double distance() {
        return edgeIter.distance();
    }

    @Override public final int flags() {
        return edgeIter.flags();
    }

    @Override public final boolean next() {
        while (edgeIter.next()) {
            if (!accept())
                continue;
            return true;
        }
        return false;
    }

    public boolean accept() {
        return graph.getLevel(edgeIter.baseNode()) <= graph.getLevel(edgeIter.node());
    }

    @Override public int edge() {
        return edgeIter.edge();
    }

    @Override public boolean isEmpty() {
        return false;
    }
}
