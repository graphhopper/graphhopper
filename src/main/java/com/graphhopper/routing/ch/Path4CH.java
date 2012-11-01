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

import com.graphhopper.routing.Path4Shortcuts;
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
public class Path4CH extends Path4Shortcuts {

    public Path4CH(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    static boolean isValidEdge(int edgeId) {
        return edgeId > 0;
    }

    @Override
    public void calcWeight(EdgeIterator mainIter) {
        weight += weightCalculation.getWeight(mainIter);
        distance += weightCalculation.revert(mainIter.distance(), mainIter.flags());
        handleSkippedEdge((EdgeSkipIterator) mainIter);
    }

    @Override
    protected void handleSkippedEdge(EdgeSkipIterator mainIter) {
        expand(mainIter.fromNode(), mainIter.node(), mainIter.skippedEdge());
    }

    void expand(int from, int to, int skippedEdge) {
        if (!isValidEdge(skippedEdge))
            return;

        // 2 things:
        // - one edge needs to be determined explicitely -> because we store only one
        // - edgeProps can return an empty edge if the shortcuts is available for both directions
        if (reverse) {
            EdgeSkipIterator iter = (EdgeSkipIterator) g.getEdgeProps(skippedEdge, from);
            if (iter.isEmpty()) {
                iter = (EdgeSkipIterator) g.getEdgeProps(skippedEdge, to);
                int skippedNode = iter.fromNode();
                expand(skippedNode, to, iter.skippedEdge());
                add(skippedNode);
                findSkippedNode(from, skippedNode);
            } else {
                int skippedNode = iter.fromNode();
                findSkippedNode(skippedNode, to);
                add(skippedNode);
                expand(from, skippedNode, iter.skippedEdge());
            }
        } else {
            EdgeSkipIterator iter = (EdgeSkipIterator) g.getEdgeProps(skippedEdge, to);
            if (iter.isEmpty()) {
                iter = (EdgeSkipIterator) g.getEdgeProps(skippedEdge, from);
                int skippedNode = iter.fromNode();
                expand(from, skippedNode, iter.skippedEdge());
                add(skippedNode);
                findSkippedNode(skippedNode, to);
            } else {
                int skippedNode = iter.fromNode();
                findSkippedNode(from, skippedNode);
                add(skippedNode);
                expand(skippedNode, to, iter.skippedEdge());
            }
        }
    }

    private void findSkippedNode(int from, int to) {
        EdgeSkipIterator iter = (EdgeSkipIterator) g.getOutgoing(from);
        double lowest = Double.MAX_VALUE;
        int skip = -1;
        while (iter.next()) {
            if (iter.node() == to && iter.distance() < lowest) {
                lowest = iter.distance();
                skip = iter.skippedEdge();
            }
        }
        expand(from, to, skip);
    }
}
