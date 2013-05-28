package com.graphhopper.routing.util;

/**
 * en-/decodes turn costs into a integer which can be stored
 * 
 * int - the 32 bits are parted from left (high) to right (low) bits as following:
 * 4 bits - turn costs flags (e.g. fourth bit means 'restriction for cars', third bit means 'restriction for bike', first and second 
 *      bit are free for later use)
 * 14 bits - costs describing the time this turn would cost by using a bike  
 * 14 bits - costs describing the time this turn would cost by using a car
 * 
 * @author Karl Hübner
 *
 */
public final class TurnCostEncoder {

    public final static int RESTRICTION_CAR = 0x01;
    public final static int RESTRICTION_BIKE = 0x02;
    public final static int RESTRICTION_BOTH = RESTRICTION_CAR | RESTRICTION_BIKE;
    public final static int COSTS_MAX = 0x3FFF;

    /**
     * @return a new turn costs flag which holds a restriction for both car and bike
     */
    public static int restriction(){
        return compose(COSTS_MAX, COSTS_MAX, RESTRICTION_BOTH);
    }
    
    private static int compose(int costsBike, int costsCar, int flags){
        return flags << 28 | costsBike << 14 | costsCar ;
    }

    /**
     * @return a new turn costs flag which is holds no costs 
     */
    public static int noCosts() {
        return 0;
    }
    
    /**
     * @return a new turn costs flag which holds the time this turn would cost for car and bike
     */
    public static int costs(double costsBike, double costsCar){
        return costs(costsBike, costsCar, 0);
    }
    
    /**
     * @return a new turn costs flag which holds the time this turn would cost for car and bike
     */
    public static int costs(double costsBike, double costsCar, int flags){
        if(costsCar < 0 || costsCar > COSTS_MAX ){
            throw new IllegalArgumentException("time of the turn by car must be bigger or equal than 0 and less or equal than 4095");
        }
        
        if(costsBike < 0 || costsBike > COSTS_MAX ){
            throw new IllegalArgumentException("time of the turn by bike must be bigger or equal than 0 and less or equal than 4095");
        }
        return compose((int)Math.ceil(costsBike), (int)Math.ceil(costsCar), flags);
    }
    
    /**
     * @param flags holds the costs of this turn
     * @return <code>true</code> if this turn is restricted in both fastest and shortest routing and for both cars and bikes
     */
    public boolean isTurnRestricted(int flags){
        return ((flags >> 28) & RESTRICTION_BOTH) == RESTRICTION_BOTH;
    }
    
    /**
     * @param flags holds the costs of this turn
     * @return <code>true</code> if this turn is restricted in both fastest and shortest routing for bikes
     */
    public boolean isTurnRestrictedForBike(int flags){
        return ((flags >> 28) & RESTRICTION_BIKE) == RESTRICTION_BIKE;
    }
    
    /**
     * @param flags holds the costs of this turn
     * @return <code>true</code> if this turn is restricted in both fastest and shortest routing for cars
     */
    public boolean isTurnRestrictedForCar(int flags){
        return ((flags >> 28) & RESTRICTION_CAR) == RESTRICTION_CAR;
    }
    
    /**
     * @param flags holds the time this turn will cost for cars 
     * @return the time in seconds of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsForCar(int flags){
        if(isTurnRestrictedForCar(flags)){
            return Double.MAX_VALUE;
        }
        return (flags & 0x00003FFF);
    }
    
    /**
     * @param flags holds the time this turn will cost for bikes
     * @return the time in seconds of the turn costs decoded by <code>flags</code> 
     */
    public double getTurnCostsForBike(int flags){
        if(isTurnRestrictedForBike(flags)){
            return Double.MAX_VALUE;
        }
        return (flags & 0x0FFFC000) >> 14;
    }
}
