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
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.GraphUtility;
import de.jetsli.graph.util.XFirstSearch;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class PrepareRouting {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph g;

    public PrepareRouting(Graph g) {
        this.g = g;
    }

    public int doWork() {
        Map<Integer, Integer> map = findSubnetworks();
        keepLargestNetwork(map);
//        introduceShortcuts();
        logger.info("optimize...");
        g.optimize();
        return map.size();
    }

    public Map<Integer, Integer> findSubnetworks() {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        final AtomicInteger integ = new AtomicInteger(0);
        int locs = g.getNodes();
        final MyBitSet bs = new MyOpenBitSet(locs);
        for (int start = 0; start < locs; start++) {
            if (bs.contains(start))
                continue;

            new XFirstSearch() {
                @Override protected MyBitSet createBitSet(int size) {
                    return bs;
                }

                @Override
                protected EdgeIdIterator getEdges(Graph g, int current) {
                    return g.getEdges(current);
                }

                @Override protected boolean goFurther(int nodeId) {
                    boolean ret = super.goFurther(nodeId);
                    if (ret)
                        integ.incrementAndGet();
                    return ret;
                }
            }.start(g, start, true);

            map.put(start, integ.get());
            integ.set(0);
        }
        return map;
    }

    /**
     * Deletes all but the larges subnetworks.
     */
    public void keepLargestNetwork(Map<Integer, Integer> map) {
        if (map.size() < 2)
            return;

        int biggestStart = -1;
        int count = -1;
        MyTBitSet bs = new MyTBitSet(g.getNodes());
        for (Entry<Integer, Integer> e : map.entrySet()) {
            if (biggestStart < 0) {
                biggestStart = e.getKey();
                count = e.getValue();
                continue;
            }

            if (count < e.getValue()) {
                deleteNetwork(biggestStart, e.getValue(), bs);

                biggestStart = e.getKey();
                count = e.getValue();
            } else
                deleteNetwork(e.getKey(), e.getValue(), bs);
        }
    }

    /**
     * Deletes the complete subnetwork reachable through start
     */
    public void deleteNetwork(int start, int entries, final MyBitSet bs) {
        new XFirstSearch() {
            @Override protected MyBitSet createBitSet(int size) {
                return bs;
            }

            @Override protected EdgeIdIterator getEdges(Graph g, int current) {
                return g.getEdges(current);
            }

            @Override protected boolean goFurther(int nodeId) {
                g.markNodeDeleted(nodeId);
                return super.goFurther(nodeId);
            }
        }.start(g, start, true);
    }

    /**
     * Create short cuts to skip 2 degree nodes and make graph traversal for routing more efficient
     */
    public void createShortcuts() {
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
            EdgeIdIterator iter = g.getEdges(lRes.n);
            iter = GraphUtility.until(iter, rRes.n);
            if (iter != EdgeIdIterator.EMPTY && iter.flags() == rRes.flags) {
                // update the distance?
                if (iter.distance() > lRes.d + rRes.d) {
                    logger.info("shorter exists for " + lRes.n + "->" + rRes.n + ": " + (lRes.d + rRes.d));
                    // TODO iter.distance(lRes.d + rRes.d);
                }
            } else
                // finally create the shortcut
                g.edge(lRes.n, rRes.n, lRes.d + rRes.d, rRes.flags);
        }
    }

    boolean findFirstLeftAndRight(EdgeIdIterator iter, Res lRes, Res rRes) {
        while (iter.next()) {
            if (lRes.n < 0) {
                lRes.n = iter.nodeId();
                lRes.flags = iter.flags();
            } else if (rRes.n < 0) {
                rRes.flags = iter.flags();
                if (lRes.flags != CarFlags.swapDirection(rRes.flags))
                    return false;

                rRes.n = iter.nodeId();
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
            EdgeIdIterator iter = g.getEdges(start);
            double tmpD = 0;
            int tmpF = 0;
            int tmpN = -1;
            for (int count = 0; iter.next(); count++) {
                if (count >= 2)
                    return;

                if (tmpN < 0 && skip != iter.nodeId()) {
                    // TODO g.setPriority(start, -1);
                    skip = start;
                    tmpN = start = iter.nodeId();
                    tmpD = iter.distance();
                    tmpF = iter.flags();
                }
            }

            if (tmpN < 0 || bs.contains(tmpN))
                return;

            if (res.flags != tmpF)
                return;
            res.d += tmpD;
            res.n = tmpN;
            bs.add(res.n);
        }
    }

    class Res {

        int n = -1;
        double d;
        int flags;
    }
}
