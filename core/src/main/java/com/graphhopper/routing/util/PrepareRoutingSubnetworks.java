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
import com.graphhopper.util.*;
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
    private int minCarNetworkSize = 0;
    private int subNetworks = -1;
    private final AtomicInteger maxEdgesPerNode = new AtomicInteger(0);

    public PrepareRoutingSubnetworks( GraphStorage g, EncodingManager em )
    {
        this.g = g;
        if (em.getVehicleCount() == 0)
            throw new IllegalStateException("No vehicles found");
        else if (em.getVehicleCount() > 1)
            edgeFilter = EdgeFilter.ALL_EDGES;
        else
            edgeFilter = new DefaultEdgeFilter(em.getSingle());
    }

    public PrepareRoutingSubnetworks setMinNetworkSize( int minNetworkSize )
    {
        this.minNetworkSize = minNetworkSize;
        return this;
    }
    public PrepareRoutingSubnetworks setMinCarNetworkSize( int minCarNetworkSize )
    {
        this.minCarNetworkSize = minCarNetworkSize;
        return this;
    }

    public void doWork()
    {
        int del = removeZeroDegreeNodes();
        int deadnet = 0;
        if (this.minCarNetworkSize > 0)
        {
            StopWatch sw = new StopWatch().start();
            deadnet = removeOneWayDeadEndNetworks(this.minCarNetworkSize);
            logger.info("removeOneWayDeadEndNetworks: " + sw.stop().getSeconds() + "s");
        }
        
        Map<Integer, Integer> map = findSubnetworks();
        keepLargeNetworks(map);

        int unvisited = RemoveUnvisited(g, g.getEncodingManager().getEncoder("car"), findSubnetworks());

        logger.info("optimize to remove subnetworks (" + map.size() + "), zero-degree-nodes (" + del + "), "
                + "dead-end-oneway-nodes (" + deadnet + "), "
                + "unvisited (" + unvisited + "), "
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
        final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        final AtomicInteger integ = new AtomicInteger(0);
        int locs = g.getNodes();
        final GHBitSet bs = new GHBitSetImpl(locs);
        EdgeExplorer explorer = g.createEdgeExplorer(edgeFilter);
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
    
    int removeOneWayDeadEndNetworks(final int minSize)
    {
        int removed = 0;

        FlagEncoder encoder = g.getEncodingManager().getEncoder("car");

        EdgeExplorer inExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
        EdgeExplorer outExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
        
        AllEdgesIterator edgeIterator = g.getAllEdges();
       
        while(edgeIterator.next())
        {
            if ((edgeIterator.getEdge() % 100000) == 100000)
                logger.info("removeOneWayDeadEndNetworks " + edgeIterator.getEdge());

            boolean forward = encoder.isBool(edgeIterator.getFlags(), encoder.K_FORWARD);
            boolean backward = encoder.isBool(edgeIterator.getFlags(), encoder.K_BACKWARD);
            
            if (forward && !backward)
            {
                int node = edgeIterator.getAdjNode();
                if (g.isNodeRemoved(node))
                    continue;

                if (subNodeCount(inExplorer, node, minSize) < minSize)
                {
                    removed += removeOneWay(inExplorer, node);
                }
            }
            else if (!forward && backward)
            {
                int node = edgeIterator.getBaseNode();

                if (g.isNodeRemoved(node))
                    continue;

                if (subNodeCount(outExplorer, node, minSize) < minSize)
                {
                    removed += removeOneWay(outExplorer, node);
                }
            }
        }
        return removed;
    }
    
    private int removeOneWay(final EdgeExplorer explorer, final int start)
    {
        final long flagEncoderDirectionMask = 3l;
        final AtomicInteger integ = new AtomicInteger(0);
        
        new XFirstSearch()
        {
            protected boolean checkAdjacent( EdgeIteratorState edge )
            {
                long oldFlags=edge.getFlags();
                long newFlags=oldFlags & (~flagEncoderDirectionMask);
                edge.setFlags(newFlags);
                integ.incrementAndGet();
                return true;
            }
        }.start(explorer, start, false);
        
        return integ.get();
    }
    
    /**
     * Remove one-way nodes that drives to dead-end
     * <p/>
     * @return removed nodes
     */
    /*
    int removeOneWayDeadEndNetworks2(final int minSize)
    {
        int removed = 0;
        int locs = g.getNodes();

        FlagEncoder encoder = g.getEncodingManager().getEncoder("car");

        HashSet<Integer> visitedInNodes = new HashSet<Integer>();
        HashSet<Integer> visitedOutNodes = new HashSet<Integer>();

        EdgeExplorer explorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder));
        EdgeExplorer inExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
        EdgeExplorer outExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
        for (int start = 0; start < locs; start++)
        {
            if ((start+1 % 100000) == 100000)
            {
                logger.info("removeOneWayDeadEndNetworks " + start + "/" + locs);
            }
            
            if (g.isNodeRemoved(start))
                continue;
            
            EdgeIterator edgeIterator = explorer.setBaseNode(start);
            
            while (edgeIterator.next())
            {
                boolean forward = encoder.isBool(edgeIterator.getFlags(), encoder.K_FORWARD);
                boolean backward = encoder.isBool(edgeIterator.getFlags(), encoder.K_BACKWARD);

                if (forward && !backward)
                {
                    int node = edgeIterator.getAdjNode();
                    if (g.isNodeRemoved(node))
                        continue;

                    if (visitedInNodes.contains(node))
                        continue;

                    visitedInNodes.add(node);

                    if (subNodeCount(inExplorer, node, minSize) < minSize)
                    {
                        removed++;
                        g.markNodeRemoved(node);
                    }
                }
                else if (!forward && backward)
                {
                    int node = edgeIterator.getBaseNode();

                    if (g.isNodeRemoved(node))
                        continue;

                    if (visitedOutNodes.contains(node))
                        continue;
                    
                    visitedOutNodes.add(node);

                    if (subNodeCount(outExplorer, node, minSize) < minSize)
                    {
                        removed++;
                        g.markNodeRemoved(node);
                    }
                }
            }
        }
        return removed;
    }
    */
    private int subNodeCount(final EdgeExplorer explorer, final int start, final int stopNodeCount)
    {
        final AtomicInteger integ = new AtomicInteger(0);
        
        new XFirstSearch()
        {
            @Override
            protected final boolean goFurther( int nodeId )
            {
                return integ.incrementAndGet() < stopNodeCount;
            }
        }.start(explorer, start, false);
        
        return integ.get();
    }

    private static int RemoveUnvisited(GraphStorage g, FlagEncoder encoder, Map<Integer, Integer> map)
    {
        int removed = 0;
        EdgeExplorer inExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
        EdgeExplorer outExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));

        removed += RemoveUnvisited(g, inExplorer, map);
        removed += RemoveUnvisited(g, outExplorer, map);
        
        return removed;
    }
    
    private static int RemoveUnvisited(GraphStorage g, final EdgeExplorer explorer, final Map<Integer, Integer> map)
    {
        int removed = 0;
        final HashSet<Integer> visitedNodes = new HashSet<Integer>();
        
        for (Entry<Integer, Integer> e : map.entrySet())
        {
            int mapStart = e.getKey();
            new XFirstSearch()
            {
                @Override
                protected final boolean goFurther( int nodeId )
                {
                    visitedNodes.add(nodeId);
                    return true;
                }
            }.start(explorer, mapStart, false);
        }

        int locs = g.getNodes();
        for (int start = 0; start < locs; start++)
        {
            if (!visitedNodes.contains(start))
            {
                removed++;
                g.markNodeRemoved(start);
            }
        }
        return removed;
    }
}
