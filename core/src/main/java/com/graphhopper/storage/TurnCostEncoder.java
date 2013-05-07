package com.graphhopper.storage;

public final class TurnCostEncoder {

    /**
     * @return a new turn costs flag which holds a restriction for both shortest and fastest routing
     */
    public static int restriction(){
        return 0x80008000;
    }

    /**
     * @return a new turn costs flag which is holds no costs 
     */
    public static int noCosts() {
        return 0;
    }
    
    /**
     * @return a new turn costs flag which holds the distance and time this turn would cost
     */
    public static int flags(double distance, double time){
        int flags = 0x00000000;
        if(distance == Double.MAX_VALUE){
            flags |= 0x80000000;
        }else if(distance < 0 || distance > 0x0FFF ){
            throw new IllegalArgumentException("distance of the turn must be bigger than 0 and less than 4095");
        }else{
            flags |= (((int)Math.ceil(distance)) << 16);
        }
        
        if(time == Double.MAX_VALUE){
            flags |= 0x00008000;
        }else if(distance < 0 || distance > 0x0FFF ){
            throw new IllegalArgumentException("time costs of the turn must be bigger than 0 and less than 4095");
        }else{
            flags |= (((int)Math.ceil(distance)));
        }
        return flags;
    }
    
    /**
     * @param flags holds the costs of this turn
     * @return <code>true</code> if this turn is restricted in both fastest and shortest rotuing
     */
    public boolean isTurnRestricted(int flags){
        return (flags & 0x80008000) == 0x80008000;
    }
    
    /**
     * @param flags holds the distance this turn will cost
     * @return the distance in meters of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsInDistance(int flags){
        if((flags & 0x8000000) == 0x80000000){
            return Double.MAX_VALUE;
        }
        return (flags & 0x0FFF0000) >> 16;
    }
    
    /**
     * @param flags holds the time this turn will cost
     * @return the time in seconds of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsInTime(int flags){
        if((flags & 0x00008000) == 0x00008000){
            return Double.MAX_VALUE;
        }
        return (flags & 0x0000FFF) >> 16;
    }
}
