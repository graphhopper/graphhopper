package com.graphhopper.routing.util;

/**
 * en-/decodes turn costs into a integer which can be stored
 * 
 * int - the 32 bits are parted from left (high) to right (low) bits as following:
 * 8 bits - turn costs flags (e.g. first bit means 'restriction', other flags may used later to separate car/bike/truck restrictions)
 * 12 bits - costs describing the distance this turn would cost (may be used by shortest path search) 
 * 12 bits - costs describing the time this turn would cost (may be used by fastest path search)
 * 
 * @author Karl Hübner
 *
 */
public final class TurnCostEncoder {

    public final static int RESTRICTION = 0x01;
    public final static int CAR = 0x02;
    public final static int BIKE = 0x04;
    public final static int TRUCK = 0x08;
    public final static int ALL_VEHICLES = CAR | BIKE | TRUCK;
    public final static int COSTS_MAX = 0x0FFF;

    /**
     * @return a new turn costs flag which holds a restriction for both shortest and fastest routing
     */
    public static int restriction(){
        return compose(COSTS_MAX, COSTS_MAX, RESTRICTION | ALL_VEHICLES);
    }
    
    public static int compose(int costsInDistance, int costsInTime, int flags){
        return flags << 24 | costsInDistance << 12 | costsInTime ;
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
    public static int flags(double distance, double time, int flags){
        if(distance < 0 || distance > 0x0FFF ){
            throw new IllegalArgumentException("distance of the turn must be bigger or equal than 0 and less or equal than 4095");
        }
        
        if(time < 0 || time > 0x0FFF ){
            throw new IllegalArgumentException("time of the turn must be bigger or equal than 0 and less or equal than 4095");
        }
        return compose((int)Math.ceil(distance), (int)Math.ceil(time), flags);
    }
    
    /**
     * @param flags holds the costs of this turn
     * @return <code>true</code> if this turn is restricted in both fastest and shortest rotuing
     */
    public boolean isTurnRestricted(int flags){
        return (flags & RESTRICTION) == RESTRICTION;
    }
    
    /**
     * @param flags holds the distance this turn will cost
     * @return the distance in meters of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsInDistance(int flags){
        if((flags & RESTRICTION) == RESTRICTION){
            return Double.MAX_VALUE;
        }
        return (flags & 0x00FFF000) >> 12;
    }
    
    /**
     * @param flags holds the time this turn will cost
     * @return the time in seconds of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsInTime(int flags){
        if((flags & RESTRICTION) == RESTRICTION){
            return Double.MAX_VALUE;
        }
        return (flags & 0x0000FFF);
    }
}
