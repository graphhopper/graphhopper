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
package de.jetsli.graph.routing;

import de.jetsli.graph.routing.util.EdgeFlags;
import de.jetsli.graph.routing.util.WeightCalculation;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.BitUtil;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeSkipIterator;
import de.jetsli.graph.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class PathPrio extends PathBidirRef {

    public PathPrio(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    // TODO remove code duplication!
    @Override
    public void calcWeight(EdgeIterator mainIter, int to) {
        double lowestW = -1;
        double dist = -1;
        int skippedNode = -1;
        int flags = -1;
        EdgeSkipIterator iter = (EdgeSkipIterator) mainIter;
        while (iter.next()) {
            if (iter.node() == to) {
                double tmpW = weightCalculation.getWeight(iter);
                if (lowestW < 0 || lowestW > tmpW) {
                    lowestW = tmpW;
                    dist = iter.distance();
                    flags = iter.flags();
                    skippedNode = iter.skippedNode();
                }
            }
        }

        if (lowestW < 0)
            throw new IllegalStateException("couldn't extract path. distance for " + iter.node()
                    + " to " + to + " not found!?");

        weight += lowestW;
        distance += dist;

        if (skippedNode >= 0) {
            // logger.info("iter(" + currEdge.node + "->" + tmpTo + ") with skipped node:" + iter.skippedNode());
            expand(iter.fromNode(), to, skippedNode, flags);
        }
    }

    private void expand(int from, int to, int skippedNode, int flags) {
        boolean reverse = false;
        int skip = from;
        EdgeIterator tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, flags);
        if (tmpIter == EdgeIterator.EMPTY) {
            skip = to;

            // swap
            int tmp = from;
            from = to;
            to = tmp;
            // search skipped node at the other end of the shortcut!
            reverse = true;
            tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, EdgeFlags.swapDirection(flags));
            if (tmpIter == EdgeIterator.EMPTY)
                throw new IllegalStateException("skipped node " + skippedNode + " not found for " + from + "->" + to + "? " + BitUtil.toBitString(flags, 8));
        }

        TIntArrayList tmp = new TIntArrayList();
        while (true) {
            int node = tmpIter.node();
            tmp.add(node);
            tmpIter = g.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == skip)
                tmpIter.next();

            skip = node;
            if (((PriorityGraph) g).getPriority(tmpIter.node()) >= 0 || tmpIter.node() == to)
                break;
        }

        if (reverse)
            tmp.reverse();

        for (int i = 0; i < tmp.size(); i++) {
            add(tmp.get(i));
        }
    }
}
