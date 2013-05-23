package com.graphhopper.storage;

import static com.graphhopper.util.Helper.nf;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TurnCostIterator;

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
    
    protected boolean supportTurnCosts;
    
    public GraphStorageTurnCosts(Directory dir) {
        this(dir, true);
    }
    
    public GraphStorageTurnCosts(Directory dir, boolean supportTurnCosts) {
        super(dir);
        this.supportTurnCosts = supportTurnCosts;
        
        if(supportTurnCosts){
            N_COSTS_PTR = nextNodeEntryIndex();

            initNodeAndEdgeEntrySize();

            this.turnCosts = dir.findCreate("turnCosts");

            TC_FROM = nextTurnCostsEntryIndex();
            TC_TO = nextTurnCostsEntryIndex();
            TC_COSTS = nextTurnCostsEntryIndex();
            TC_NEXT = nextTurnCostsEntryIndex();
            turnCostsEntrySize = turnCostsEntryIndex + 1;
            turnCostsCount = 0;    
        }else{
            N_COSTS_PTR = TC_FROM = TC_TO = TC_COSTS = TC_NEXT = 0;
        }
        
    }

    protected final int nextTurnCostsEntryIndex() {
        return ++turnCostsEntryIndex;
    }

    @Override
    GraphStorage segmentSize(int bytes) {
        super.segmentSize(bytes);
        if(supportTurnCosts){
            turnCosts.segmentSize(bytes);   
        }        
        return this;
    }

    @Override
    public GraphStorageTurnCosts create(long nodeCount) {
        super.create(nodeCount);
        if(supportTurnCosts){
            long initBytes = Math.max(nodeCount * 4, 100);
            turnCosts.create((long) initBytes * turnCostsEntrySize);    
        }
        return this;
    }

    @Override
    public void setNode(int index, double lat, double lon) {
        super.setNode(index, lat, lon);
        if(supportTurnCosts){
            long tmp = (long) index * nodeEntrySize;
            nodes.setInt(tmp + N_COSTS_PTR, NO_COST_ENTRY);    
        }
    }

    @Override
    public void turnCosts(int nodeIndex, int from, int to, int flags) {
        if(supportTurnCosts){
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
            TurnCostIterator tc = createTurnCostIterable(node, edgeFrom, edgeTo);
            if(tc.next()){
                return tc.costs();
            }  
        }
        return TurnCostEncoder.noCosts();
    }
    
    @Override
    public TurnCostIterator createTurnCostIterable(int node, int edgeFrom, int edgeTo){
        if(supportTurnCosts){
            return new TurnCostIteratable(node, edgeFrom, edgeTo);    
        }else{
            return TurnCostIterator.noTurnCostSupport; 
        }
        
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
            if(supportTurnCosts){
                if (!turnCosts.loadExisting())
                    throw new IllegalStateException(
                            "cannot load node costs. corrupt file or directory? " + directory());

                turnCostsEntrySize = turnCosts.getHeader(0);
                turnCostsCount = turnCosts.getHeader(1);    
            }
            return true;
        }
        return false;
    }

    @Override
    public void flush() {
        super.flush();
        if(supportTurnCosts){
            turnCosts.setHeader(0, turnCostsEntrySize);
            turnCosts.setHeader(1, turnCostsCount);
            turnCosts.flush();    
        }
    }

    @Override
    public void close() {
        super.close();
        if(supportTurnCosts){
            turnCosts.close();
        }
        
    }

    @Override
    public long capacity() {
        if(supportTurnCosts){
            return super.capacity() + turnCosts.capacity();    
        }
        return super.capacity();
    }

    public int entries() {
        return turnCostsCount;
    }

    @Override
    public boolean isTurnCostSupport() {
        return supportTurnCosts;
    }
    
    @Override 
    public String toString() {
        if(isTurnCostSupport()){
            return super.toString()+", turn cost entries: " + nf(turnCostsCount) + "(" + turnCosts.capacity() / Helper.MB + ")";    
        }
        return super.toString();
    }
    
    public class TurnCostIteratable implements TurnCostIterator{
        
        int nodeVia;
        int edgeFrom;
        int edgeTo;
        int iteratorEdgeFrom;
        int iteratorEdgeTo;
        int turnCostIndex;
        long turnCostPtr;
        
        private TurnCostIteratable(int node, int edgeFrom, int edgeTo){
            this.nodeVia = node;
            this.iteratorEdgeFrom = edgeFrom;
            this.iteratorEdgeTo = edgeTo;
            this.edgeFrom = EdgeIterator.NO_EDGE;
            this.edgeTo = EdgeIterator.NO_EDGE;
            this.turnCostIndex = getCostTableAdress(nodeVia);
            this.turnCostPtr = -1L;
        }
        
        @Override
        public boolean next(){
            int i=0;
            boolean found = false;
            for (; i < 1000; i++) {
                if (turnCostIndex == NO_COST_ENTRY)
                    break;
                turnCostPtr = (long) turnCostIndex * turnCostsEntrySize;
                edgeFrom = turnCosts.getInt(turnCostPtr + TC_FROM);
                edgeTo = turnCosts.getInt(turnCostPtr + TC_TO);
                

                int nextTurnCostIndex = turnCosts.getInt(turnCostPtr + TC_NEXT);
                if(nextTurnCostIndex == turnCostIndex){
                    throw new IllegalStateException("something went wrong: next entry would be the same");
                }
                turnCostIndex = nextTurnCostIndex;
                
                if(edgeFrom != EdgeIterator.NO_EDGE && edgeTo != EdgeIterator.NO_EDGE && // 
                        (iteratorEdgeFrom == TurnCostIterator.ANY_EDGE || edgeFrom == iteratorEdgeFrom) && // 
                        (iteratorEdgeTo == TurnCostIterator.ANY_EDGE || edgeTo == iteratorEdgeTo)){
                    found = true;
                    break;
                }
            }
            // so many turn restrictions on one node? here is something wrong
            if (i > 1000)
                throw new IllegalStateException("something went wrong: no end of turn cost-list found");
            return found;
        }
        
        public int edgeFrom(){
            return edgeFrom;
        }
        
        public int edgeTo(){
            return edgeTo;
        }
        
        public int costs(){
            return turnCosts.getInt(turnCostPtr + TC_COSTS);
        }
        
        public TurnCostIterator edgeFrom(int from) {
            turnCosts.setInt(turnCostPtr + TC_FROM, from);
            edgeFrom = from;
            return this;
        }
        
        public TurnCostIterator edgeTo(int to) {
            turnCosts.setInt(turnCostPtr + TC_TO, to);
            edgeTo = to;
            return this;
        }
        
        public TurnCostIterator costs(int costs) {
            turnCosts.setInt(turnCostPtr + TC_COSTS, costs);
            return this;
        }
        
    }
}
