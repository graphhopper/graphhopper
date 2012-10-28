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
package com.graphhopper.routing;

import com.graphhopper.routing.util.PrepareContractionHierarchies;
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

    @Override
    protected void handleSkippedNode(EdgeSkipIterator mainIter) {
        expand(mainIter.fromNode(), mainIter.node(), mainIter.skippedNode());
    }

    @Override
    public void calcWeight(EdgeIterator mainIter) {
        weight += weightCalculation.getWeight(mainIter);
        distance += weightCalculation.revert(mainIter.distance(), mainIter.flags());
        EdgeSkipIterator iter = (EdgeSkipIterator) mainIter;
        if (iter.skippedNode() >= 0)
            handleSkippedNode(iter);
    }

    private void expand(int from, int to, int skippedNode) {
        // TODO smaller or equal to 0
        if (skippedNode < 0)
            return;

        if (reverse) {
            findSkippedNode(skippedNode, to);
            add(skippedNode);
            findSkippedNode(from, skippedNode);
        } else {
            findSkippedNode(from, skippedNode);
            add(skippedNode);
            findSkippedNode(skippedNode, to);
        }
    }

    private void findSkippedNode(int from, int to) {
        EdgeSkipIterator iter = (EdgeSkipIterator) g.getOutgoing(from);
        double lowest = Double.MAX_VALUE;
        int skip = -1;
        while (iter.next()) {
            if (iter.node() == to && iter.distance() < lowest) {
                lowest = iter.distance();
                skip = iter.skippedNode();
            }
        }
        expand(from, to, skip);
    }
}
