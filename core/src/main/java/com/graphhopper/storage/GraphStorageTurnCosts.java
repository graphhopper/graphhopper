package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

/**
 * Additionally stores node costs of nodes.
 * 
 * @author Karl Hübner
 */
public class GraphStorageTurnCosts extends GraphStorage implements GraphTurnCosts {

    /* pointer for no cost entry */
    protected final int NO_COST_ENTRY = -1;

    /* additional items for nodes: osmid (splitted), pointer to first entry in
     * node cost table */
    protected final int N_COSTS_PTR;
    
    /* items in turn cost tables: edge from, edge to, costs, pointer to next
     * cost entry of same node */
    protected final int TC_FROM, TC_TO, TC_COSTS, TC_NEXT;

    protected DataAccess turnCosts;
    protected int turnCostsEntryIndex = -1;
    protected int turnCostsEntrySize;
    protected int turnCostsCount;

    public GraphStorageTurnCosts(Directory dir) {
        super(dir);
        N_COSTS_PTR = nextNodeEntryIndex();

        initNodeAndEdgeEntrySize();

        this.turnCosts = dir.findCreate("turnCosts");

        TC_FROM = nextTurnCostsEntryIndex();
        TC_TO = nextTurnCostsEntryIndex();
        TC_COSTS = nextTurnCostsEntryIndex();
        TC_NEXT = nextTurnCostsEntryIndex();
        turnCostsEntrySize = turnCostsEntryIndex + 1;
        turnCostsCount = 0;
    }

    protected final int nextTurnCostsEntryIndex() {
        return ++turnCostsEntryIndex;
    }

    @Override
    GraphStorage segmentSize(int bytes) {
        super.segmentSize(bytes);
        turnCosts.segmentSize(bytes);
        return this;
    }

    @Override
    public GraphStorageTurnCosts create(long nodeCount) {
        super.create(nodeCount);
        long initBytes = Math.max(nodeCount * 4, 100);
        turnCosts.create((long) initBytes * turnCostsEntrySize);
        return this;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        super.setNode(index, lat, lon);
        long tmp = (long) index * nodeEntrySize;
        nodes.setInt(tmp + N_COSTS_PTR, NO_COST_ENTRY);
    }

    @Override
    public void turnCosts(int nodeIndex, int from, int to, int flags) {
        //append
        int newEntryIndex = turnCostsCount;
        turnCostsCount++;
        ensureNodeCostsIndex(newEntryIndex);

        //determine if we already have an cost entry for this node
        int previousEntryIndex = getCostTableAdress(nodeIndex);
        if (previousEntryIndex == NO_COST_ENTRY) {
            //set cost-pointer to this new cost entry
            nodes.setInt((long) nodeIndex * nodeEntrySize + N_COSTS_PTR, newEntryIndex);
        } else {
            int i = 0;
            int tmp = previousEntryIndex;
            while ((tmp = turnCosts.getInt((long) tmp * turnCostsEntrySize + TC_NEXT)) != NO_COST_ENTRY) {
                previousEntryIndex = tmp;
                //search for the last added cost entry
                if (i++ > 1000) {
                    throw new IllegalStateException(
                            "Something unexpected happened. A node probably will not have 1000+ relations.");
                }
            }
            //set next-pointer to this new cost entry
            turnCosts.setInt((long) previousEntryIndex * turnCostsEntrySize + TC_NEXT,
                    newEntryIndex);
        }
        //add entry
        long costsBase = (long) newEntryIndex * turnCostsEntrySize;
        turnCosts.setInt(costsBase + TC_FROM, from);
        turnCosts.setInt(costsBase + TC_TO, to);
        turnCosts.setInt(costsBase + TC_COSTS, flags);
        //next-pointer is NO_COST_ENTRY
        turnCosts.setInt(costsBase + TC_NEXT, NO_COST_ENTRY);
    }

    private int getCostTableAdress(int index) {
        if(index >= nodes() || index < 0){
            return NO_COST_ENTRY;
        }
        return nodes.getInt((long) index * nodeEntrySize + N_COSTS_PTR);
    }

    
    
    @Override
    public int turnCosts(int node, int edgeFrom, int edgeTo) {
        if(edgeFrom != EdgeIterator.NO_EDGE && edgeTo != EdgeIterator.NO_EDGE ){
            int nextCostsIndex = getCostTableAdress(node);
            while (nextCostsIndex != NO_COST_ENTRY) {
                long costsBase = (long) nextCostsIndex * turnCostsEntrySize;
                int from = turnCosts.getInt(costsBase + TC_FROM);
                int to = turnCosts.getInt(costsBase + TC_TO);
                if (edgeFrom == from && edgeTo == to) {
                    return turnCosts.getInt(costsBase + TC_COSTS);
                }
                nextCostsIndex = turnCosts.getInt(costsBase + TC_NEXT);
            }    
        }
        return TurnCostEncoder.noCosts();
    }

    void ensureNodeCostsIndex(int nodeIndex) {
        long deltaCap = (long) nodeIndex * turnCostsEntrySize * 4 - turnCosts.capacity();
        if (deltaCap <= 0)
            return;

        incCapacity(turnCosts, deltaCap);
    }

    @Override
    public boolean loadExisting() {
        if (super.loadExisting()) {
            if (!turnCosts.loadExisting())
                throw new IllegalStateException(
                        "cannot load node costs. corrupt file or directory? " + directory());

            turnCostsEntrySize = turnCosts.getHeader(0);
            turnCostsCount = turnCosts.getHeader(1);
            return true;
        }
        return false;
    }

    @Override
    public void flush() {
        super.flush();
        turnCosts.setHeader(0, turnCostsEntrySize);
        turnCosts.setHeader(1, turnCostsCount);
        turnCosts.flush();
    }

    @Override
    public void close() {
        super.close();
        turnCosts.close();
    }

    @Override
    public long capacity() {
        return super.capacity() + turnCosts.capacity();
    }
}
