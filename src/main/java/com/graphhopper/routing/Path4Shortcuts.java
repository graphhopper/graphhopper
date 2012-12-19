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

import com.graphhopper.routing.util.PrepareTowerNodesShortcuts;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import gnu.trove.list.array.TIntArrayList;

/**
 * Unpack shortcuts of paths between tower nodes.
 *
 * @see PrepareTowerNodesShortcuts
 * @author Peter Karich,
 */
public class Path4Shortcuts extends PathBidirRef {

    protected boolean reverse = true;

    public Path4Shortcuts(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    @Override public void reverseOrder() {
        reverse = !reverse;
        super.reverseOrder();
    }

    @Override
    public void calcWeight(EdgeIterator mainIter) {
        super.calcWeight(mainIter);
        EdgeSkipIterator iter = (EdgeSkipIterator) mainIter;
        if (iter.skippedEdge() >= 0)
            handleSkippedEdge(iter);
    }

    protected void handleSkippedEdge(EdgeSkipIterator iter) {
        int from = iter.baseNode();
        int to = iter.node();

        // TODO move this swapping to expand where it belongs (necessary because of usage 'getOutgoing')
        if (reverse) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // find edge 'from'-skippedEdge
        boolean success = expand(from, to, iter.skippedEdge(), false);
        if (!success) {
            // find edge 'to'-skippedEdge
            success = expand(to, from, iter.skippedEdge(), true);
            if (!success)
                throw new IllegalStateException("skipped edge " + iter.skippedEdge() + " not found for "
                        + iter.baseNode() + "<->" + iter.node() + "? " + BitUtil.toBitString(iter.flags(), 8));
        }
    }

    protected boolean expand(int from, int to, int skippedEdge, boolean reverse) {
        int avoidNode = from;
        EdgeIterator tmpIter = g.getEdgeProps(skippedEdge, from);
        if (tmpIter.isEmpty())
            return false;

        int node = tmpIter.baseNode();
        TIntArrayList tmpNodeList = new TIntArrayList();
        while (true) {
            tmpNodeList.add(node);
            tmpIter = g.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == avoidNode) {
                if (!tmpIter.next())
                    throw new IllegalStateException("node should have two degree:" + node);
            }

            avoidNode = node;
            node = tmpIter.node();
            // TODO introduce edge filter here too?
            if (((LevelGraph) g).getLevel(tmpIter.node()) >= 0 || tmpIter.node() == to)
                break;
        }

        if (reverse)
            tmpNodeList.reverse();

        for (int i = 0; i < tmpNodeList.size(); i++) {
            add(tmpNodeList.get(i));
        }
        return true;
    }
}
