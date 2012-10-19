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
import com.graphhopper.util.GraphUtility;

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
    protected void handleSkippedNode(int from, int to, int flags, int skippedNode) {
        EdgeIterator tmpIter = until(from, skippedNode, flags);
        if (tmpIter != EdgeIterator.EMPTY) {
            EdgeSkipIterator tmp2 = (EdgeSkipIterator) tmpIter;
            if (tmp2.skippedNode() >= 0)
                handleSkippedNode(from, skippedNode, flags, tmp2.skippedNode());
        }
        
        super.handleSkippedNode(from, to, flags, skippedNode);
        tmpIter = until(skippedNode, to, flags);
        if (tmpIter != EdgeIterator.EMPTY) {
            EdgeSkipIterator tmp2 = (EdgeSkipIterator) tmpIter;
            if (tmp2.skippedNode() >= 0)
                handleSkippedNode(skippedNode, to, flags, tmp2.skippedNode());
        }        
    }

    @Override
    protected EdgeIterator until(int from, int to, int flags) {
        // ignore flags as they should be ignored for shortcuts
        return GraphUtility.until(g.getOutgoing(from), to);
    }
}
