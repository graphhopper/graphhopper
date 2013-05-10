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
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;

/**
 * Recursivly unpack shortcuts.
 *
 * @see PrepareContractionHierarchies
 * @author Peter Karich
 */
public class Path4CH extends PathBidirRef {

    private WeightCalculation calc;

    public Path4CH(Graph g, EdgePropertyEncoder encoder, WeightCalculation calc) {
        super(g, encoder);
        this.calc = calc;
    }

    @Override
    protected void processDistance(int tmpEdge, int endNode) {
        EdgeIterator mainIter = graph.getEdgeProps(tmpEdge, endNode);

        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        expandEdge((EdgeSkipIterator) mainIter, false);
    }

    @Override
    public void calcDistance(EdgeIterator mainIter) {
        distance += calc.revertWeight(mainIter.distance(), mainIter.flags());
    }

    private void expandEdge(EdgeSkipIterator mainIter, boolean revert) {
        if (!mainIter.isShortcut()) {
            calcDistance(mainIter);
            int flags = mainIter.flags();
            calcTime(calc.revertWeight(mainIter.distance(), flags), flags);
            addEdge(mainIter.edge());
            return;
        }

        int skippedEdge1 = mainIter.skippedEdge1();
        int skippedEdge2 = mainIter.skippedEdge2();
        int from = mainIter.baseNode(), to = mainIter.adjNode();
        if (revert) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // getEdgeProps could possibly return an empty edge if the shortcut is available for both directions
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
}
