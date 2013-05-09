package com.graphhopper.util;

import com.graphhopper.routing.util.TurnCostEncoder;


/**
 * @author Karl Hübner
 */
public interface TurnCostIterator {

    public static int ANY_EDGE = -2;

    public boolean next();
    
    public int edgeFrom();
    
    public TurnCostIterator edgeFrom(int from);
    
    public int edgeTo();
    
    public TurnCostIterator edgeTo(int to);
   
    public int costs();
    
    public TurnCostIterator costs(int costs);

    /** 
     * will be used when no turn cost support is active
     */
    public static TurnCostIterator noTurnCostSupport = new TurnCostIterator() {
        
        @Override
        public boolean next() {
            return false;
        }
        
        @Override
        public TurnCostIterator edgeTo(int to) {
            return this;
        }
        
        @Override
        public int edgeTo() {
            return EdgeIterator.NO_EDGE;
        }
        
        @Override
        public TurnCostIterator edgeFrom(int from) {
            return this;
        }
        
        @Override
        public int edgeFrom() {
            return EdgeIterator.NO_EDGE;
        }
        
        @Override
        public TurnCostIterator costs(int costs) {
            return this;
        }
        
        @Override
        public int costs() {
            return TurnCostEncoder.noCosts();
        }
    };
    
    
}
