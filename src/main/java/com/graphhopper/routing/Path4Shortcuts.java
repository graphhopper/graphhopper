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

import com.graphhopper.routing.util.PrepareLongishPathShortcuts;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;

/**
 * Unpack shortcuts of lengthy paths.
 *
 * @see PrepareLongishPathShortcuts
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
        if (iter.skippedNode() >= 0)
            handleSkippedNode(iter);
    }

    protected void handleSkippedNode(EdgeSkipIterator iter) {
        int from = iter.fromNode();
        int to = iter.node();
        int flags = iter.flags();
        // TODO move this swapping to expand where it belongs (necessary because of usage 'getOutgoing')
        if (reverse) {
            int tmp = from;
            from = to;
            to = tmp;
        }

        // find edge 'from'-skippedNode
        boolean success = expand(from, to, iter.skippedNode(), flags, false);
        if (!success) {
            // find edge 'to'-skippedNode
            success = expand(to, from, iter.skippedNode(), flags, true);
            if (!success)
                throw new IllegalStateException("skipped node " + iter.skippedNode() + " not found for "
                        + iter.fromNode() + "<->" + iter.node() + "? " + BitUtil.toBitString(iter.flags(), 8));
        }
    }

    protected boolean expand(int from, int to, int skippedNode, int flags, boolean reverse) {
        int avoidNode = from;
        EdgeIterator tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, flags);
        if (tmpIter == EdgeIterator.EMPTY)
            return false;

        TIntArrayList tmpNodeList = new TIntArrayList();
        while (true) {
            int node = tmpIter.node();
            tmpNodeList.add(node);
            tmpIter = g.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == avoidNode)
                tmpIter.next();

            avoidNode = node;
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
