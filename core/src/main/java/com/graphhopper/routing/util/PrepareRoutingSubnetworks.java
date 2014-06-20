/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.XFirstSearch;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes nodes which are not part of the largest network. Ie. mostly nodes with no edges at all
 * but also small subnetworks which are nearly always bugs in OSM data or indicate otherwise
 * disconnected areas e.g. via barriers - see #86.
 * <p/>
 * @author Peter Karich
 */
public class PrepareRoutingSubnetworks
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphStorage g;
    private final EdgeFilter edgeFilter;
    private int minNetworkSize = 200;
    private int subNetworks = -1;
    private final AtomicInteger maxEdgesPerNode = new AtomicInteger(0);
    private final EncodingManager encodingManager;

    public PrepareRoutingSubnetworks( GraphStorage g, EncodingManager em )
    {
        this.g = g;
        if (em.getVehicleCount() == 0)
            throw new IllegalStateException("No vehicles found");
        else if (em.getVehicleCount() > 1)
            edgeFilter = EdgeFilter.ALL_EDGES;
        else
            edgeFilter = new DefaultEdgeFilter(em.getSingle());
        this.encodingManager = em;
    }

    public PrepareRoutingSubnetworks setMinNetworkSize( int minNetworkSize )
    {
        this.minNetworkSize = minNetworkSize;
        return this;
    }

    public void doWork()
    {
        int del = removeZeroDegreeNodes();
        Map<Integer, Integer> map = findSubnetworks();
        keepLargeNetworks(map);

        int unvisitedDeadEnds = 0;
        if (this.encodingManager.getVehicleCount() == 1)
            unvisitedDeadEnds = RemoveDeadEndUnvisitedNetworks(g, this.encodingManager.getSingle(), minNetworkSize, logger);

        logger.info("optimize to remove subnetworks (" + map.size() + "), zero-degree-nodes (" + del + "), "
                + "unvisited-dead-end-nodes(" + unvisitedDeadEnds + "), "
                + "maxEdges/node (" + maxEdgesPerNode.get() + ")");
        g.optimize();
        subNetworks = map.size();
    }

    public int getSubNetworks()
    {
        return subNetworks;
    }

    public Map<Integer, Integer> findSubnetworks()
    {
        return findSubnetworks(g, g.createEdgeExplorer(edgeFilter), maxEdgesPerNode);
    }
    
    private static Map<Integer, Integer> findSubnetworks(final GraphStorage g, final EdgeExplorer explorer)
    {
        return findSubnetworks(g, explorer, new AtomicInteger(0));
    }

    private static Map<Integer, Integer> findSubnetworks(final GraphStorage g, final EdgeExplorer explorer, final AtomicInteger maxEdgesPerNode)
    {
        final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        final AtomicInteger integ = new AtomicInteger(0);
        int locs = g.getNodes();
        final GHBitSet bs = new GHBitSetImpl(locs);
        for (int start = 0; start < locs; start++)
        {
            if (g.isNodeRemoved(start) || bs.contains(start))
                continue;

            new XFirstSearch()
            {
                int tmpCounter = 0;

                @Override
                protected GHBitSet createBitSet()
                {
                    return bs;
                }

                @Override
                protected final boolean goFurther( int nodeId )
                {
                    if (tmpCounter > maxEdgesPerNode.get())
                        maxEdgesPerNode.set(tmpCounter);

                    tmpCounter = 0;
                    integ.incrementAndGet();
                    return true;
                }

                @Override
                protected final boolean checkAdjacent( EdgeIteratorState iter )
                {
                    tmpCounter++;
                    return true;
                }

            }.start(explorer, start, false);
            map.put(start, integ.get());
            integ.set(0);
        }
        return map;
    }

    /**
     * Deletes all but the larges subnetworks.
     */
    void keepLargeNetworks( Map<Integer, Integer> map )
    {
        if (map.size() < 2)
            return;

        int biggestStart = -1;
        int maxCount = -1;
        GHBitSetImpl bs = new GHBitSetImpl(g.getNodes());
        for (Entry<Integer, Integer> e : map.entrySet())
        {
            if (biggestStart < 0)
            {
                biggestStart = e.getKey();
                maxCount = e.getValue();
                continue;
            }

            if (maxCount < e.getValue())
            {
                // new biggest area found. remove old
                removeNetwork(biggestStart, maxCount, bs);

                biggestStart = e.getKey();
                maxCount = e.getValue();
            } else
            {
                removeNetwork(e.getKey(), e.getValue(), bs);
            }
        }
    }

    /**
     * Deletes the complete subnetwork reachable through start
     */
    void removeNetwork( int start, int entries, final GHBitSet bs )
    {
        if (entries >= minNetworkSize)
        {
            // logger.info("did not remove large network (" + entries + ")");
            return;
        }
        EdgeExplorer explorer = g.createEdgeExplorer(edgeFilter);
        new XFirstSearch()
        {
            @Override
            protected GHBitSet createBitSet()
            {
                return bs;
            }

            @Override
            protected boolean goFurther( int nodeId )
            {
                g.markNodeRemoved(nodeId);
                return super.goFurther(nodeId);
            }
        }.start(explorer, start, true);
    }

    /**
     * To avoid large processing and a large HashMap remove nodes with no edges up front
     * <p/>
     * @return removed nodes
     */
    int removeZeroDegreeNodes()
    {
        int removed = 0;
        int locs = g.getNodes();
        EdgeExplorer explorer = g.createEdgeExplorer();
        for (int start = 0; start < locs; start++)
        {
            EdgeIterator iter = explorer.setBaseNode(start);
            if (!iter.next())
            {
                removed++;
                g.markNodeRemoved(start);
            }
        }
        return removed;
    }
    
    /**
     * Clean small networks that will be never be visited by this explorer
     * See #86
     * For example, small areas like parkings are sometimes connected to the whole network through one-way road
     * This is clearly an error - but is causes the routing to fail when point get connected to this small area
     * This routines removed all these points from the graph
     * The algotithm is to through the graph, build the network map and for each small map remove the network
     * <p/>
     * @return removed nodes;
     */
    public static int RemoveDeadEndUnvisitedNetworks(final GraphStorage g, final FlagEncoder encoder, final int minNetworkSize, final Logger logger)
    {
        int removed = 0;

        StopWatch sw = new StopWatch().start();
        logger.info("RemoveDeadEndUnvisitedNetworks: searching forward");
        removed += RemoveDeadEndUnvisitedNetworks(g, g.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false)), minNetworkSize, logger);
        logger.info("RemoveDeadEndUnvisitedNetworks: forward search completed in " + sw.stop().getSeconds() + "s");
        
        sw.start();
        logger.info("RemoveDeadEndUnvisitedNetworks: searching backward");
        removed += RemoveDeadEndUnvisitedNetworks(g, g.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true)), minNetworkSize, logger);
        logger.info("RemoveDeadEndUnvisitedNetworks: backward search completed in " + sw.stop().getSeconds() + "s");

        return removed;
    }
    
    private static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map)
    {
        Comparator<K> valueComparator =  new Comparator<K>() {
            public int compare(K k1, K k2)
            {
                int compare = map.get(k2).compareTo(map.get(k1));
                if (compare == 0) return 1;
                else return compare;
            }
        };
        Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }
    
    private static int RemoveDeadEndUnvisitedNetworks(final GraphStorage g, final EdgeExplorer explorer, final int minNetworkSize, final Logger logger)
    {
        final AtomicInteger removed = new AtomicInteger(0);
        
        // Lest find subnetworks according to this explorer
        // Sort the map by largest networks first
        Map<Integer, Integer> map = sortByValues(findSubnetworks(g, explorer));
        if (map.size() < 2)
            return 0;
        
        //  big networks will populate bs first so these nodes won't be deleted
        final GHBitSetImpl bs = new GHBitSetImpl(g.getNodes());
        boolean first = true;
        for (Entry<Integer, Integer> e : map.entrySet())
        {
            int mapStart = e.getKey();
            int subnetSize = e.getValue();
            
            final boolean removeNetwork = (!first) && (subnetSize < minNetworkSize);
            if (first)
                first = false;
            
            if (removeNetwork)
                logger.info("Removing dead-end network: " + subnetSize  + " nodes starting from nodeid=" + mapStart);

            new XFirstSearch()
            {
                @Override
                protected GHBitSet createBitSet()
                {
                    return bs;
                }

                @Override
                protected final boolean goFurther(int nodeId)
                {
                    if (removeNetwork)
                    {
                        // This remaining node is member of a small disconnected network
                        g.markNodeRemoved(nodeId);
                        removed.incrementAndGet();
                    }
                    return super.goFurther(nodeId);
                }
            }.start(explorer, mapStart, false);
        }

        return removed.get();
    }
}
