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
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;

/**
 * Holds turn cost tables for each node. The additional field of a node will be used to point
 * towards the first entry within a node cost table to identify turn restrictions, or later, turn
 * getCosts.
 * <p>
 * @author karl.huebner
 */
public class TurnCostStorage implements ExtendedStorage
{
    /* pointer for no cost entry */
    private final int NO_TURN_ENTRY = -1;
    private final long EMPTY_FLAGS = 0L;

    /*
     * items in turn cost tables: edge from, edge to, getCosts, pointer to next
     * cost entry of same node
     */
    private final int TC_FROM, TC_TO, TC_FLAGS, TC_NEXT;

    private DataAccess turnCosts;
    private int turnCostsEntryIndex = -4;
    private int turnCostsEntryBytes;
    private int turnCostsCount;

    private GraphStorage graph;
    private NodeAccess nodeAccess;

    public TurnCostStorage()
    {
        TC_FROM = nextTurnCostsEntryIndex();
        TC_TO = nextTurnCostsEntryIndex();
        TC_FLAGS = nextTurnCostsEntryIndex();
        TC_NEXT = nextTurnCostsEntryIndex();
        turnCostsEntryBytes = turnCostsEntryIndex + 4;
        turnCostsCount = 0;
    }

    @Override
    public void init( GraphStorage graph )
    {
        if (turnCostsCount > 0)
            throw new AssertionError("The turn cost storage must be initialized only once.");

        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        this.turnCosts = this.graph.getDirectory().find("turnCosts");
    }

    private int nextTurnCostsEntryIndex()
    {
        turnCostsEntryIndex += 4;
        return turnCostsEntryIndex;
    }

    @Override
    public void setSegmentSize( int bytes )
    {
        turnCosts.setSegmentSize(bytes);
    }

    @Override
    public void create( long initBytes )
    {
        turnCosts.create((long) initBytes * turnCostsEntryBytes);
    }

    @Override
    public void flush()
    {
        turnCosts.setHeader(0, turnCostsEntryBytes);
        turnCosts.setHeader(1 * 4, turnCostsCount);
        turnCosts.flush();
    }

    @Override
    public void close()
    {
        turnCosts.close();
    }

    @Override
    public long getCapacity()
    {
        return turnCosts.getCapacity();
    }

    @Override
    public boolean loadExisting()
    {
        if (!turnCosts.loadExisting())
            return false;

        turnCostsEntryBytes = turnCosts.getHeader(0);
        turnCostsCount = turnCosts.getHeader(4);
        return true;
    }

    private int getCostTableAdress( int index )
    {
        if (index >= graph.getNodes() || index < 0)
            throw new IllegalStateException("turn cost index cannot be negative " + index);

        return nodeAccess.getAdditionalNodeField(index);
    }

    /**
     * This method adds a new entry which is a turn restriction or cost information via the
     * turnFlags.
     */
    public void addTurnInfo( int nodeIndex, int from, int to, long turnFlags )
    {
        // no need to store turn information
        if (turnFlags == EMPTY_FLAGS)
            return;

        // append
        int newEntryIndex = turnCostsCount;
        turnCostsCount++;
        ensureTurnCostsIndex(newEntryIndex);

        // determine if we already have an cost entry for this node
        int previousEntryIndex = getCostTableAdress(nodeIndex);
        if (previousEntryIndex == NO_TURN_ENTRY)
        {
            // set cost-pointer to this new cost entry
            nodeAccess.setAdditionalNodeField(nodeIndex, newEntryIndex);
        } else
        {
            int i = 0;
            int tmp = previousEntryIndex;
            while ((tmp = turnCosts.getInt((long) tmp * turnCostsEntryBytes + TC_NEXT)) != NO_TURN_ENTRY)
            {
                previousEntryIndex = tmp;
                // search for the last added cost entry
                if (i++ > 1000)
                {
                    throw new IllegalStateException("Something unexpected happened. A node probably will not have 1000+ relations.");
                }
            }
            // set next-pointer to this new cost entry
            turnCosts.setInt((long) previousEntryIndex * turnCostsEntryBytes + TC_NEXT, newEntryIndex);
        }
        // add entry
        long costsBase = (long) newEntryIndex * turnCostsEntryBytes;
        turnCosts.setInt(costsBase + TC_FROM, from);
        turnCosts.setInt(costsBase + TC_TO, to);
        turnCosts.setInt(costsBase + TC_FLAGS, (int) turnFlags);
        // next-pointer is NO_TURN_ENTRY
        turnCosts.setInt(costsBase + TC_NEXT, NO_TURN_ENTRY);
    }

    /**
     * @return turn flags of the specified node and edge properties.
     */
    public long getTurnCostsFlags( int node, int edgeFrom, int edgeTo )
    {
        if (edgeFrom != EdgeIterator.NO_EDGE && edgeTo != EdgeIterator.NO_EDGE)
        {
            TurnCostIterable tc = new TurnCostIterable(node, edgeFrom, edgeTo);
            if (tc.next())
                return tc.getCostsFlags();
        }
        return EMPTY_FLAGS;
    }

    private void ensureTurnCostsIndex( int nodeIndex )
    {
        long deltaCap = ((long) nodeIndex + 4) * turnCostsEntryBytes - turnCosts.getCapacity();
        if (deltaCap <= 0)
        {
            return;
        }
        turnCosts.incCapacity(deltaCap);
    }

    @Override
    public boolean isRequireNodeField()
    {
        //we require the additional field in the graph to point to the first entry in the node table
        return true;
    }

    @Override
    public boolean isRequireEdgeField()
    {
        return false;
    }

    @Override
    public int getDefaultNodeFieldValue()
    {
        return NO_TURN_ENTRY;
    }

    @Override
    public int getDefaultEdgeFieldValue()
    {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    @Override
    public ExtendedStorage copyTo( ExtendedStorage clonedStorage )
    {
        if (!(clonedStorage instanceof TurnCostStorage))
        {
            throw new IllegalStateException("the extended storage to clone must be the same");
        }

        TurnCostStorage clonedTC = (TurnCostStorage) clonedStorage;

        turnCosts.copyTo(clonedTC.turnCosts);
        clonedTC.turnCostsCount = turnCostsCount;

        return clonedStorage;
    }

    private class TurnCostIterable
    {
        int nodeVia;
        int edgeFrom;
        int edgeTo;
        int iteratorEdgeFrom;
        int iteratorEdgeTo;
        int turnCostIndex;
        long turnCostPtr;

        public TurnCostIterable( int node, int edgeFrom, int edgeTo )
        {
            this.nodeVia = node;
            this.iteratorEdgeFrom = edgeFrom;
            this.iteratorEdgeTo = edgeTo;
            this.edgeFrom = EdgeIterator.NO_EDGE;
            this.edgeTo = EdgeIterator.NO_EDGE;
            this.turnCostIndex = getCostTableAdress(nodeVia);
            this.turnCostPtr = -1L;
        }

        public boolean next()
        {
            int i = 0;
            boolean found = false;
            for (; i < 1000; i++)
            {
                if (turnCostIndex == NO_TURN_ENTRY)
                    break;
                turnCostPtr = (long) turnCostIndex * turnCostsEntryBytes;
                edgeFrom = turnCosts.getInt(turnCostPtr + TC_FROM);
                edgeTo = turnCosts.getInt(turnCostPtr + TC_TO);

                int nextTurnCostIndex = turnCosts.getInt(turnCostPtr + TC_NEXT);
                if (nextTurnCostIndex == turnCostIndex)
                {
                    throw new IllegalStateException("something went wrong: next entry would be the same");
                }
                turnCostIndex = nextTurnCostIndex;

                if (edgeFrom != EdgeIterator.NO_EDGE && edgeTo != EdgeIterator.NO_EDGE
                        && edgeFrom == iteratorEdgeFrom && edgeTo == iteratorEdgeTo)
                {
                    found = true;
                    break;
                }
            }
            // so many turn restrictions on one node? here is something wrong
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of turn cost-list found");
            return found;
        }

        public int getEdgeFrom()
        {
            return edgeFrom;
        }

        public int getEdgeTo()
        {
            return edgeTo;
        }

        public long getCostsFlags()
        {
            return turnCosts.getInt(turnCostPtr + TC_FLAGS);
        }
    }

}
