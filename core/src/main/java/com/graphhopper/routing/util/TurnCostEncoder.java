package com.graphhopper.routing.util;

/**
 * Encodes and decodes a turn restriction and turn costs 
 * within a integer flag 
 * 
 * @author karl.huebner
 */
public interface TurnCostEncoder
{
    /**
     * @return true, if, and only if it is encoded in flag 
     */
    boolean isRestricted(int flag);
    
    /**
     * @return the costs in seconds encoded in flag 
     */
    int getCosts(int flag);
    
    /**
     * @return the encoded turn restriction and turn costs 
     */
    int flags(boolean restricted, int costs);
    
    /**
     * sets the shift and index to encode costs/restrictions
     */
    int defineBits( int index, int shift );
    
    /**
     * whether turn costs nor turn restrictions will be encoded by this
     * encoder, should be used for pedestrians  
     */
    static class NoTurnCostsEncoder implements TurnCostEncoder {

        @Override
        public boolean isRestricted( int flag )
        {
            return false;
        }

        @Override
        public int getCosts( int flag )
        {
            return 0;
        }

        @Override
        public int flags( boolean restricted, int costs )
        {
            return 0;
        }

        @Override
        public int defineBits( int index, int shift )
        {
            return shift;
        }
        
    }
    
}
