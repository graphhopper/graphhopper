package com.graphhopper.routing.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TurnCostEncoderTest {

    private static final int CAR_100 = 100;
    private static final int BIKE_300 = 300;
    private static final int MAX_COSTS = 16383;
    
    private TurnCostEncoder turnCostEncoder = new TurnCostEncoder();
    
    @Test public void restriction(){
        int turnCostFlag = TurnCostEncoder.restriction();
        
        assertTrue(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
    }
    
    @Test public void composeRestrictionBoth(){
        int turnCostFlag = TurnCostEncoder.flags(BIKE_300, CAR_100, TurnCostEncoder.RESTRICTION_BOTH);
        
        assertTrue(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
    }
    
    @Test public void composeRestrictionCarOnly(){
        int turnCostFlag = TurnCostEncoder.flags(BIKE_300, CAR_100, TurnCostEncoder.RESTRICTION_CAR);
        
        assertFalse(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(BIKE_300, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
    }
    
    @Test public void composeRestrictionBikeOnly(){
        int turnCostFlag = TurnCostEncoder.flags(BIKE_300, CAR_100, TurnCostEncoder.RESTRICTION_BIKE);
        
        assertFalse(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertTrue(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(Double.MAX_VALUE, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(CAR_100, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
    }
    
    @Test public void composeTurnCostsOnly(){
        int turnCostFlag = TurnCostEncoder.flags(BIKE_300, CAR_100, 0);
        
        assertFalse(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(BIKE_300, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(CAR_100, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
    }
    
    @Test public void errorNegativeCosts(){
        try{
            TurnCostEncoder.flags(-BIKE_300, CAR_100, 0);
            fail();
        }catch(Exception e){
            //ok
        }
        
        try{
            TurnCostEncoder.flags(BIKE_300, -CAR_100, 0);
            fail();
        }catch(Exception e){
            //ok
        }
        
    }
    
    @Test public void maxCosts(){
        try{
            TurnCostEncoder.flags(MAX_COSTS+1, MAX_COSTS, 0);
            fail();
        }catch(Exception e){
            //ok
        }
        
        try{
            TurnCostEncoder.flags(MAX_COSTS, MAX_COSTS+1, 0);
            fail();
        }catch(Exception e){
            //ok
        }
        
        int turnCostFlag = TurnCostEncoder.flags(MAX_COSTS, MAX_COSTS, 0);
        
        assertFalse(turnCostEncoder.isTurnRestricted(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForBike(turnCostFlag));
        assertFalse(turnCostEncoder.isTurnRestrictedForCar(turnCostFlag));
        assertEquals(MAX_COSTS, turnCostEncoder.getTurnCostsForBike(turnCostFlag),0.0d);
        assertEquals(MAX_COSTS, turnCostEncoder.getTurnCostsForCar(turnCostFlag),0.0d);
        
    }
}
