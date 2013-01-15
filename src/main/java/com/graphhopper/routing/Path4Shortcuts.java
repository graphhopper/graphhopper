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
 * Unpack shortcuts of paths between tower nodes. At the moment unused as we
 * handle tower and pillar nodes while import. We'll see if it could help us
 * later.
 *
 * @see PrepareTowerNodesShortcuts
 * @author Peter Karich
 */
public class Path4Shortcuts extends PathBidirRef {

    public Path4Shortcuts(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    @Override
    protected void processWeight(int tmpEdge, int endNode) {
        EdgeIterator mainIter = graph.getEdgeProps(tmpEdge, endNode);
        calcWeight(mainIter);
        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        EdgeSkipIterator iter = (EdgeSkipIterator) mainIter;        
        if (EdgeIterator.Edge.isValid(iter.skippedEdge())) {
            handleSkippedEdge(iter);
        } else {
            // only add if it is not a shortcut            
            addEdge(mainIter.edge());
        }
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
        boolean success = expandEdge(from, to, iter.skippedEdge(), false);
        if (!success) {
            // find edge 'to'-skippedEdge
            success = expandEdge(to, from, iter.skippedEdge(), true);
            if (!success)
                throw new IllegalStateException("skipped edge " + iter.skippedEdge() + " not found for "
                        + iter.baseNode() + "<->" + iter.node() + "? " + BitUtil.toBitString(iter.flags(), 8));
        }
    }

    protected boolean expandEdge(int from, int to, int skippedEdge, boolean reverse) {
        int avoidNode = from;
        EdgeIterator tmpIter = graph.getEdgeProps(skippedEdge, from);
        if (tmpIter.isEmpty())
            return false;

        int node = tmpIter.baseNode();
        TIntArrayList tmpEdgeList = new TIntArrayList();
        tmpEdgeList.add(tmpIter.edge());
        while (true) {            
            tmpIter = graph.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == avoidNode) {
                if (!tmpIter.next())
                    throw new IllegalStateException("pillar node should have two degree:" + node);
            }
            avoidNode = tmpIter.baseNode();
            node = tmpIter.node();
            tmpEdgeList.add(tmpIter.edge());
            // TODO introduce edge filter here too?
            if (((LevelGraph) graph).getLevel(node) >= 0 || node == to)
                break;            
        }

        if (reverse)
            tmpEdgeList.reverse();

        for (int i = 0; i < tmpEdgeList.size(); i++) {
            addEdge(tmpEdgeList.get(i));
        }
        return true;
    }
}
