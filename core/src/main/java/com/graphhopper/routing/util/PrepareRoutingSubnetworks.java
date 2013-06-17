/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing.util;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.XFirstSearch;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes nodes which are not part of the largest network. Ie. mostly nodes
 * with no edges at all but also small subnetworks which are nearly always bugs
 * in OSM data.
 *
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworks {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph g;
    private int minNetworkSize = 3000;
    private int subNetworks = -1;

    public PrepareRoutingSubnetworks(Graph g) {
        this.g = g;
    }

    public PrepareRoutingSubnetworks minNetworkSize(int minNetworkSize) {
        this.minNetworkSize = minNetworkSize;
        return this;
    }

    public void doWork() {
        logger.info("removeZeroDegreeNodes");
        int del = removeZeroDegreeNodes();
        logger.info("findSubnetworks");
        Map<Integer, Integer> map = findSubnetworks();
        logger.info("keepLargeNetworks");
        keepLargeNetworks(map);
        logger.info("optimize to remove subnetworks (" + map.size() + "), zero-degree-nodes(" + del + ")");
        g.optimize();
        subNetworks = map.size();
    }

    public int subNetworks() {
        return subNetworks;
    }

    public Map<Integer, Integer> findSubnetworks() {
        final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        final AtomicInteger integ = new AtomicInteger(0);
        int locs = g.nodes();
        final GHBitSet bs = new GHBitSetImpl(locs);
        for (int start = 0; start < locs; start++) {
            if (g.isNodeRemoved(start) || bs.contains(start))
                continue;
            new XFirstSearch() {
                @Override protected GHBitSet createBitSet(int size) {
                    return bs;
                }

                @Override protected boolean goFurther(int nodeId) {
                    integ.incrementAndGet();
                    return true;
                }
            }.start(g, start, false);
            map.put(start, integ.get());
            integ.set(0);
        }
        return map;
    }

    /**
     * Deletes all but the larges subnetworks.
     */
    void keepLargeNetworks(Map<Integer, Integer> map) {
        if (map.size() < 2)
            return;

        int biggestStart = -1;
        int maxCount = -1;
        GHBitSetImpl bs = new GHBitSetImpl(g.nodes());
        for (Entry<Integer, Integer> e : map.entrySet()) {
            if (biggestStart < 0) {
                biggestStart = e.getKey();
                maxCount = e.getValue();
                continue;
            }

            if (maxCount < e.getValue()) {
                // new biggest area found. remove old
                removeNetwork(biggestStart, maxCount, bs);

                biggestStart = e.getKey();
                maxCount = e.getValue();
            } else
                removeNetwork(e.getKey(), e.getValue(), bs);
        }
    }

    /**
     * Deletes the complete subnetwork reachable through start
     */
    void removeNetwork(int start, int entries, final GHBitSet bs) {
        if (entries > minNetworkSize) {
            logger.info("did not remove large network (" + entries + ")");
            return;
        }
        new XFirstSearch() {
            @Override protected GHBitSet createBitSet(int size) {
                return bs;
            }

            @Override protected boolean goFurther(int nodeId) {
                g.markNodeRemoved(nodeId);
                return super.goFurther(nodeId);
            }
        }.start(g, start, true);
    }

    /**
     * To avoid large processing and a large HashMap remove nodes with no edges
     * up front
     *
     * @return removed nodes
     */
    int removeZeroDegreeNodes() {
        int removed = 0;
        int locs = g.nodes();
        for (int start = 0; start < locs; start++) {
            if (!g.getEdges(start).next()) {
                removed++;
                g.markNodeRemoved(start);
            }
        }
        return removed;
    }
}
