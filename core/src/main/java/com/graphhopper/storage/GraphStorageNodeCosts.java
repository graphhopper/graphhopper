package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;

/**
 * Additionally stores node costs of nodes.
 * 
 * @author Karl Hübner
 */
public class GraphStorageNodeCosts extends GraphStorage implements GraphNodeCosts {

    /* pointer for no cost entry */
    protected final int NO_COST_ENTRY = -1;

    /* additional items for nodes: osmid (splitted), pointer to first entry in
     * node cost table */
    protected final int N_OSMID_L, N_OSMID_R, N_COSTS_PTR;

    /* additional items for edges: osmid (splitted) */
    protected final int E_OSMID_L, E_OSMID_R;

    /* items in node cost tables: edge from, edge to, costs, pointer to next
     * cost entry of same node */
    protected final int NC_FROM, NC_TO, NC_COSTS, NC_NEXT;

    protected DataAccess nodesCosts;
    protected int nodesCostsEntryIndex = -1;
    protected int nodesCostsEntrySize;
    protected int nodesCostsCount;

    public GraphStorageNodeCosts(Directory dir) {
        super(dir);
        N_OSMID_L = nextNodeEntryIndex();
        N_OSMID_R = nextNodeEntryIndex();
        N_COSTS_PTR = nextNodeEntryIndex();

        E_OSMID_L = nextEdgeEntryIndex();
        E_OSMID_R = nextEdgeEntryIndex();

        initNodeAndEdgeEntrySize();

        this.nodesCosts = dir.findCreate("nodesCosts");

        NC_FROM = nextNodesCostsEntryIndex();
        NC_TO = nextNodesCostsEntryIndex();
        NC_COSTS = nextNodesCostsEntryIndex();
        NC_NEXT = nextNodesCostsEntryIndex();
        nodesCostsEntrySize = nodesCostsEntryIndex + 1;
        nodesCostsCount = 0;
    }

    protected final int nextNodesCostsEntryIndex() {
        return ++nodesCostsEntryIndex;
    }

    @Override
    GraphStorage segmentSize(int bytes) {
        super.segmentSize(bytes);
        nodesCosts.segmentSize(bytes);
        return this;
    }

    @Override
    public GraphStorageNodeCosts create(long nodeCount) {
        super.create(nodeCount);
        long initBytes = Math.max(nodeCount * 4, 100);
        nodesCosts.create((long) initBytes * nodesCostsEntrySize);
        return this;
    }

    @Override
    public void setNode(int index, double lat, double lon, long osmId) {
        super.setNode(index, lat, lon);
        long tmp = (long) index * nodeEntrySize;
        nodes.setInt(tmp + N_OSMID_L, Helper.longToIntLeft(osmId));
        nodes.setInt(tmp + N_OSMID_R, Helper.longToIntRight(osmId));
        nodes.setInt(tmp + N_COSTS_PTR, NO_COST_ENTRY);
    }

    @Override
    public EdgeIterator edge(int a, int b, double distance, int flags, long osmid) {
        EdgeIterator edge = super.edge(a, b, distance, flags);
        edges.setInt((long) edge.edge() * edgeEntrySize + E_OSMID_L, Helper.longToIntLeft(osmid));
        edges.setInt((long) edge.edge() * edgeEntrySize + E_OSMID_R, Helper.longToIntRight(osmid));
        return edge;
    }

    @Override
    public void addNodeCostEntry(int nodeIndex, int from, int to, double costs) {
        //append
        int newEntryIndex = nodesCostsCount;
        nodesCostsCount++;
        ensureNodeCostsIndex(newEntryIndex);

        //determine if we already have an cost entry for this node
        int previousEntryIndex = getCostTableAdress(nodeIndex);
        if (previousEntryIndex == NO_COST_ENTRY) {
            //set cost-pointer to this new cost entry
            nodes.setInt((long) nodeIndex * nodeEntrySize + N_COSTS_PTR, newEntryIndex);
        } else {
            int i = 0;
            int tmp = previousEntryIndex;
            while ((tmp = nodesCosts.getInt((long) tmp * nodesCostsEntrySize + NC_NEXT)) != NO_COST_ENTRY) {
                previousEntryIndex = tmp;
                //search for the last added cost entry
                if (i++ > 1000) {
                    throw new IllegalStateException(
                            "Something unexpected happened. A node probably will not have 1000+ relations.");
                }
            }
            //set next-pointer to this new cost entry
            nodesCosts.setInt((long) previousEntryIndex * nodesCostsEntrySize + NC_NEXT,
                    newEntryIndex);
        }
        //add entry
        long costsBase = (long) newEntryIndex * nodesCostsEntrySize;
        nodesCosts.setInt(costsBase + NC_FROM, from);
        nodesCosts.setInt(costsBase + NC_TO, to);
        nodesCosts.setInt(costsBase + NC_COSTS, Helper.doubleToInt(costs));
        //next-pointer is NO_COST_ENTRY
        nodesCosts.setInt(costsBase + NC_NEXT, NO_COST_ENTRY);
    }

    private int getCostTableAdress(int index) {
        return nodes.getInt((long) index * nodeEntrySize + N_COSTS_PTR);
    }

    @Override
    public double getTurnCosts(int node, int edgeFrom, int edgeTo) {
        int nextCostsIndex = nodes.getInt((long) node * nodeEntrySize + N_COSTS_PTR);
        while (nextCostsIndex != NO_COST_ENTRY) {
            long costsBase = (long) nextCostsIndex * nodesCostsEntrySize;
            int from = nodesCosts.getInt(costsBase + NC_FROM);
            int to = nodesCosts.getInt(costsBase + NC_TO);
            if (edgeFrom == from && edgeTo == to) {
                return Helper.intToDouble(nodesCosts.getInt(costsBase + NC_COSTS));
            }
            nextCostsIndex = nodesCosts.getInt(costsBase + NC_NEXT);
        }
        return 0;
    }

    void ensureNodeCostsIndex(int nodeIndex) {
        long deltaCap = (long) nodeIndex * nodesCostsEntrySize * 4 - nodesCosts.capacity();
        if (deltaCap <= 0)
            return;

        incCapacity(nodesCosts, deltaCap);
    }

    @Override
    public long getOsmId(int index) {
        ensureNodeIndex(index);
        int osmid_l = nodes.getInt((long) index * nodeEntrySize + N_OSMID_L);
        int osmid_r = nodes.getInt((long) index * nodeEntrySize + N_OSMID_R);
        return Helper.intToLong(osmid_l, osmid_r);
    }

    @Override
    public long getEdgeOsmId(int edge) {
        int osmid_l = edges.getInt((long) edge * edgeEntrySize + E_OSMID_L);
        int osmid_r = edges.getInt((long) edge * edgeEntrySize + E_OSMID_R);
        return Helper.intToLong(osmid_l, osmid_r);
    }

    @Override
    public boolean loadExisting() {
        if (super.loadExisting()) {
            if (!nodesCosts.loadExisting())
                throw new IllegalStateException(
                        "cannot load node costs. corrupt file or directory? " + directory());

            nodesCostsEntrySize = nodesCosts.getHeader(0);
            nodesCostsCount = nodesCosts.getHeader(1);
            return true;
        }
        return false;
    }

    @Override
    public void flush() {
        super.flush();
        nodesCosts.setHeader(0, nodesCostsEntrySize);
        nodesCosts.setHeader(1, nodesCostsCount);
        nodesCosts.flush();
    }

    @Override
    public void close() {
        super.close();
        nodesCosts.close();
    }

    @Override
    public long capacity() {
        return super.capacity() + nodesCosts.capacity();
    }
}
