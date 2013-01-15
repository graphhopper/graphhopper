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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;

/**
 * Recursivly unpack shortcuts.
 *
 * @see PrepareContractionHierarchies
 * @author Peter Karich,
 */
public class Path4CH extends PathBidirRef {

    public Path4CH(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    @Override
    protected void processWeight(int tmpEdge, int endNode) {
        EdgeIterator mainIter = graph.getEdgeProps(tmpEdge, endNode);

        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        EdgeSkipIterator iter = (EdgeSkipIterator) mainIter;
        if (EdgeIterator.Edge.isValid(iter.skippedEdge())) {
            expandEdge(iter, false);
        } else {
            // only add if it is not a shortcut
            calcWeight(mainIter);
            addEdge(mainIter.edge());
        }
    }

    @Override
    public void calcWeight(EdgeIterator mainIter) {
        double dist = mainIter.distance();
        int flags = mainIter.flags();
        weight += weightCalculation.getWeight(dist, flags);
        distance += weightCalculation.revert(dist, flags);
        time += weightCalculation.getTime(dist, flags);
    }

    private void expandEdge(EdgeSkipIterator mainIter, boolean revert) {
        int skippedEdge = mainIter.skippedEdge();
        if (!EdgeIterator.Edge.isValid(skippedEdge)) {
            calcWeight(mainIter);
            addEdge(mainIter.edge());
            return;
        }
        int from = mainIter.baseNode(), to = mainIter.node();
        if (revert) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // 2 things:
        // - one edge needs to be determined explicitely because we store only one -> one futher branch necessary :/
        // - getEdgeProps can return an empty edge if the shortcuts is available for both directions
        if (reverse) {
            EdgeSkipIterator iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge, from);
            if (iter.isEmpty()) {
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge, to);
                int skippedNode = iter.baseNode();
                expandEdge(iter, false);
                // addEdge(iter.edge());
                findSkippedEdge(from, skippedNode);
            } else {
                int skippedNode = iter.baseNode();
                findSkippedEdge(skippedNode, to);
                // addEdge(iter.edge());
                expandEdge(iter, true);
            }
        } else {
            EdgeSkipIterator iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge, to);
            if (iter.isEmpty()) {
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge, from);
                int skippedNode = iter.baseNode();
                expandEdge(iter, true);
                // addEdge(iter.edge());
                findSkippedEdge(skippedNode, to);
            } else {
                int skippedNode = iter.baseNode();
                findSkippedEdge(from, skippedNode);
                // addEdge(iter.edge());
                expandEdge(iter, false);
            }
        }
    }

    private void findSkippedEdge(int from, int to) {
        EdgeSkipIterator iter = (EdgeSkipIterator) graph.getOutgoing(from);
        double lowest = Double.MAX_VALUE;
        int edge = EdgeIterator.NO_EDGE;
        while (iter.next()) {
            if (iter.node() == to && iter.distance() < lowest) {
                lowest = iter.distance();
                edge = iter.edge();
            }
        }
        if (EdgeIterator.Edge.isValid(edge))
            expandEdge((EdgeSkipIterator) graph.getEdgeProps(edge, to), false);
    }
}
