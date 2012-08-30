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
package de.jetsli.graph.reader;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeFilter;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.EdgeUpdateIterator;
import de.jetsli.graph.util.GraphUtility;

/**
 * @author Peter Karich
 */
public class PrepareRoutingShortcuts {

    private final PriorityGraph g;

    public PrepareRoutingShortcuts(PriorityGraph g) {
        this.g = g;
    }

    public void doWork() {
        EdgeFilter old = g.getEdgeFilter();
        g.setEdgeFilter(null);
        createShortcuts();
        g.setEdgeFilter(old);
    }

    /**
     * Create short cuts to skip 2 degree nodes and make graph traversal for routing more efficient
     */
    private void createShortcuts() {
        int locs = g.getNodes();
        MyBitSet bs = new MyTBitSet();
        for (int l = 0; l < locs; l++) {
            Res lRes = new Res();
            Res rRes = new Res();
            if (bs.contains(l) || !findFirstLeftAndRight(g.getEdges(l), lRes, rRes))
                continue;

            bs.add(l);
            int skip = lRes.n;
            findEnd(bs, lRes, l, rRes.n);
            findEnd(bs, rRes, l, skip);

            // check if an edge already exists which is shorter than the shortcut
            EdgeUpdateIterator iter = g.getEdges(lRes.n);
            EdgeIterator tmp = GraphUtility.until(iter, rRes.n);
            if (tmp != EdgeIterator.EMPTY && tmp.flags() == rRes.flags) {
                iter = (EdgeUpdateIterator) tmp;
                // TODO distance vs. weight
                if (iter.distance() > lRes.d + rRes.d) {
                    // logger.info("shorter exists for " + lRes.n + "->" + rRes.n + ": " + (lRes.d + rRes.d));
                    // update the distance
                    iter.distance(lRes.d + rRes.d);
                }
            } else {
                // finally create the shortcut
                g.edge(lRes.n, rRes.n, lRes.d + rRes.d, rRes.flags);
            }
        }
    }

    boolean findFirstLeftAndRight(EdgeIterator iter, Res lRes, Res rRes) {
        while (iter.next()) {
            if (lRes.n < 0) {
                lRes.n = iter.node();
                lRes.flags = iter.flags();
            } else if (rRes.n < 0) {
                rRes.flags = iter.flags();
                if (lRes.flags != EdgeFlags.swapDirection(rRes.flags))
                    return false;

                rRes.n = iter.node();
            } else {
                // more than 2 edges
                return false;
            }
        }
        // less than 2 nodes
        if (rRes.n < 0)
            return false;
        return true;
    }

    void findEnd(MyBitSet bs, Res res, int start, int skip) {
        while (true) {
            EdgeIterator iter = g.getEdges(start);
            double tmpD = 0;
            int tmpF = 0;
            int tmpN = -1;
            for (int count = 0; iter.next(); count++) {
                if (count >= 2)
                    return;

                if (tmpN < 0 && skip != iter.node()) {
                    skip = start;
                    tmpN = start = iter.node();
                    tmpD = iter.distance();
                    tmpF = iter.flags();
                }
            }

            if (tmpN < 0 || bs.contains(tmpN))
                return;

            if (res.flags != tmpF)
                return;

            g.setPriority(skip, -1);
            bs.add(skip);
            res.d += tmpD;
            res.n = tmpN;
        }
    }

    class Res {

        int n = -1;
        double d;
        int flags;
    }
}
