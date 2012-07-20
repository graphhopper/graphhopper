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

        // not working at the moment: addEdgesToSkip2DegreeNodes();
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

    public void addEdgesToSkip2DegreeNodes() {
        // TODO        
        new XFirstSearch() {
            @Override protected MyBitSet createBitSet(int size) {
                return new MyOpenBitSet(g.getNodes());
            }

            @Override protected boolean goFurther(int nodeId) {
                int counter = 0;
                EdgeIdIterator iter = g.getEdges(nodeId);
                while (iter.next()) {
                    // TODO list.set(counter, e);
                    counter++;
                }
                if (counter == 2) {
                    // TODO if edges are both out edges only => report problem to OSM
                    // TODO if edges are both in edges only => do not remove
                    // do not delete - just introduce a new edge
                    // g.markDeleted(nodeId);
                }

                return super.goFurther(nodeId);
            }
        }.start(g, 0, false);
    }
}
