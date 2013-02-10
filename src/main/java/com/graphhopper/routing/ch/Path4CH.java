/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
        expandEdge((EdgeSkipIterator) mainIter, false);
    }

    @Override
    public void calcWeight(EdgeIterator mainIter) {
        double dist = mainIter.distance();
        int flags = mainIter.flags();
        weight += weightCalculation.getWeight(dist, flags);
        distance += weightCalculation.revertWeight(dist, flags);
        time += weightCalculation.getTime(dist, flags);
    }

    private void expandEdge(EdgeSkipIterator mainIter, boolean revert) {
        if (!mainIter.isShortcut()) {
            calcWeight(mainIter);
            addEdge(mainIter.edge());
            return;
        }

        int skippedEdge1 = mainIter.skippedEdge1();
        int skippedEdge2 = mainIter.skippedEdge2();
        int from = mainIter.baseNode(), to = mainIter.node();
        if (revert) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // - getEdgeProps could possibly return an empty edge if the shortcut is available for both directions
        if (reverseOrder) {
            EdgeSkipIterator iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge1, to);
            boolean empty = iter.isEmpty();
            if (empty)
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge2, to);
            expandEdge(iter, false);

            if (empty)
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge1, from);
            else
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge2, from);
            expandEdge(iter, true);
        } else {
            EdgeSkipIterator iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge1, from);
            boolean empty = iter.isEmpty();
            if (empty)
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge2, from);
            expandEdge(iter, true);

            if (empty)
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge1, to);
            else
                iter = (EdgeSkipIterator) graph.getEdgeProps(skippedEdge2, to);
            expandEdge(iter, false);
        }
    }

    // No need for this method as long as we store both edges instead of just one
    //
    // The edge 5-9 contains the right edge as skipped edge => find the left one!
    // @params are from=5 and to=7, keep in mind that we can get edges only upwards (level-wise)
    //     _ 9
    // 5 -/  /
    //  \   /
    //   \ /
    //    7
    private void findSkippedEdge(int from, int to) {
        EdgeSkipIterator iter = (EdgeSkipIterator) graph.getIncoming(to);
        double lowest = Double.MAX_VALUE;
        int edge = EdgeIterator.NO_EDGE;
        while (iter.next()) {
            if (iter.node() == from && iter.distance() < lowest) {
                lowest = iter.distance();
                edge = iter.edge();
            }
        }
        if (EdgeIterator.Edge.isValid(edge))
            expandEdge((EdgeSkipIterator) graph.getEdgeProps(edge, from), false);
    }
}
