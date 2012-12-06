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
package com.graphhopper.routing.util;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.XFirstSearch;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes nodes which are not part of the largest network. Ie. mostly nodes with no edges at all
 * but also small subnetworks which are nearly always bugs in OSM data.
 *
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworks {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph g;
    private int subNetworks = -1;

    public PrepareRoutingSubnetworks(Graph g) {
        this.g = g;
    }

    public void doWork() {
        int del = deleteZeroDegreeNodes();
        Map<Integer, Integer> map = findSubnetworks();
        keepLargestNetwork(map);
        logger.info("optimize to delete: subnetworks(" + map.size() + "), 0degreeNodes(" + del + ")");
        g.optimize();
        subNetworks = map.size();
    }

    public int getSubNetworks() {
        return subNetworks;
    }

    public Map<Integer, Integer> findSubnetworks() {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        final AtomicInteger integ = new AtomicInteger(0);
        int locs = g.getNodes();
        final MyBitSet bs = new MyBitSetImpl(locs);
        for (int start = 0; start < locs; start++) {
            if (g.isNodeDeleted(start) || bs.contains(start))
                continue;

            new XFirstSearch() {
                @Override protected MyBitSet createBitSet(int size) {
                    return bs;
                }

                @Override
                protected EdgeIterator getEdges(Graph g, int current) {
                    return g.getEdges(current);
                }

                @Override protected boolean goFurther(int nodeId) {
                    boolean ret = super.goFurther(nodeId);
                    if (ret)
                        integ.incrementAndGet();
                    return ret;
                }
            }.start(g, start, false);
            // System.out.println(start + " MAP "+map.size());
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
        MyBitSetImpl bs = new MyBitSetImpl(g.getNodes());
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

            @Override protected EdgeIterator getEdges(Graph g, int current) {
                return g.getEdges(current);
            }

            @Override protected boolean goFurther(int nodeId) {
                g.markNodeDeleted(nodeId);
                return super.goFurther(nodeId);
            }
        }.start(g, start, true);
    }

    /**
     * To avoid large processing and a large HashMap remove nodes with no edges up front
     *
     * @return deleted nodes
     */
    public int deleteZeroDegreeNodes() {
        int deleted = 0;
        int locs = g.getNodes();
        for (int start = 0; start < locs; start++) {
            if (!g.getEdges(start).next()) {
                deleted++;
                g.markNodeDeleted(start);
            }
        }
        return deleted;
    }
}
