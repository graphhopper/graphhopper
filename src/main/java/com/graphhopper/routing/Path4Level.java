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

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;

/**
 * @author Peter Karich,
 */
public class Path4Level extends PathBidirRef {

    public Path4Level(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    // code duplication with PathBidirRef.calcWeight
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
            boolean success = expand(iter.fromNode(), to, skippedNode, flags, false);
            if (!success) {
                success = expand(to, iter.fromNode(), skippedNode, flags, true);
                if (!success)
                    throw new IllegalStateException("skipped node " + skippedNode + " not found for "
                            + to + "->" + iter.fromNode() + "? " + BitUtil.toBitString(flags, 8));
            }
        }
    }

    protected boolean expand(int from, int to, int skippedNode, int flags, boolean reverse) {
        int avoidNode = from;
        EdgeIterator tmpIter = GraphUtility.until(g.getOutgoing(from), skippedNode, flags);
        if (tmpIter == EdgeIterator.EMPTY)
            return false;

        TIntArrayList tmpList = new TIntArrayList();
        while (true) {
            int node = tmpIter.node();
            tmpList.add(node);
            tmpIter = g.getEdges(node);
            tmpIter.next();
            if (tmpIter.node() == avoidNode)
                tmpIter.next();

            avoidNode = node;
            if (((LevelGraph) g).getLevel(tmpIter.node()) >= 0 || tmpIter.node() == to)
                break;
        }

        if (reverse)
            tmpList.reverse();

        for (int i = 0; i < tmpList.size(); i++) {
            add(tmpList.get(i));
        }
        return true;
    }
}
