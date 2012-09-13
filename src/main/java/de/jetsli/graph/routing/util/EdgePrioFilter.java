/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.routing.util;

import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeIterator;

/**
 * @author Peter Karich
 */
public class EdgePrioFilter implements EdgeIterator {

    protected EdgeIterator edgeIter;
    private PriorityGraph graph;

    public EdgePrioFilter(PriorityGraph g) {
        graph = g;
    }

    public EdgeIterator doFilter(EdgeIterator iter) {
        this.edgeIter = iter;
        return this;
    }

    @Override
    public final int node() {
        return edgeIter.node();
    }

    @Override
    public final double distance() {
        return edgeIter.distance();
    }

    @Override
    public final int flags() {
        return edgeIter.flags();
    }

    @Override
    public final boolean next() {
        while (edgeIter.next()) {
            if (!accept())
                continue;
            return true;
        }
        return false;
    }

    public boolean accept() {
        return graph.getPriority(edgeIter.fromNode()) <= graph.getPriority(edgeIter.node());
    }

    @Override
    public int fromNode() {
        return edgeIter.fromNode();
    }
}
