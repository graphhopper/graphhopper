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
package com.graphhopper.routing.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMReader;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.OSMTurnRelation.TurnCostTableEntry;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.BitUtil;

/**
 *
 * @author Peter Karich
 */
public class EncodingManagerTest
{
    @Test
    public void testCompatibility()
    {
        EncodingManager manager = new EncodingManager("CAR,BIKE,FOOT");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("BIKE");
        CarFlagEncoder car = (CarFlagEncoder) manager.getEncoder("CAR");
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("FOOT");
        assertNotEquals(car, bike);
        assertNotEquals(car, foot);
        assertNotEquals(car.hashCode(), bike.hashCode());
        assertNotEquals(car.hashCode(), foot.hashCode());

        EncodingManager manager2 = new EncodingManager();
        FootFlagEncoder foot2 = new FootFlagEncoder();
        manager2.registerEncoder(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        EncodingManager manager3 = new EncodingManager();
        FootFlagEncoder foot3 = new FootFlagEncoder();
        manager3.registerEncoder(foot3);
        assertEquals(foot3, foot2);
        assertEquals(foot3.hashCode(), foot2.hashCode());
    }

    @Test
    public void testEncoderAcceptNoException()
    {
        EncodingManager manager = new EncodingManager("CAR");
        assertTrue(manager.supports("CAR"));
        assertFalse(manager.supports("FOOT"));
    }

    @Test
    public void testTooManyEncoders()
    {
        EncodingManager manager = new EncodingManager();
        for (int i = 0; i < 4; i++)
        {
            manager.registerEncoder(new FootFlagEncoder());
        }
        try
        {
            manager.registerEncoder(new FootFlagEncoder());
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }

    @Test
    public void testCombineRelations()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        wayMap.put("highway", "track");
        OSMWay osmWay = new OSMWay(1, wayMap);

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        EncodingManager manager = new EncodingManager();
        BikeFlagEncoder defaultBike = new BikeFlagEncoder();
        manager.registerEncoder(defaultBike);
        BikeFlagEncoder lessRelationCodes = new BikeFlagEncoder()
        {
            @Override
            public int defineRelationBits( int index, int shift )
            {
                relationCodeEncoder = new EncodedValue("RelationCode2", shift, 2, 1, 0, 3);
                return shift + 2;
            }

            @Override
            public long handleRelationTags( OSMRelation relation, long oldRelFlags )
            {
                if (relation.hasTag("route", "bicycle"))
                    return relationCodeEncoder.setValue(0, 2);
                return relationCodeEncoder.setValue(0, 0);
            }
            @Override            
            int relationWeightCodeToSpeed( int highwaySpeed, int relationCode )
            {
                return highwaySpeed;
            }            
            @Override
            public String toString()
            {
               return "lessRelationsBits";
            }
        };
        manager.registerEncoder(lessRelationCodes);

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        long relFlags = manager.handleRelationTags(osmRel, 0);
        long allow = defaultBike.acceptBit | lessRelationCodes.acceptBit;
        long flags = manager.handleWayTags(osmWay, allow, relFlags);

        assertEquals(18, defaultBike.getSpeed(flags));
        assertEquals(4, lessRelationCodes.getSpeed(flags));
    }

    @Test
    public void testMixBikeTypesAndRelationCombination()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        wayMap.put("highway", "track");
        wayMap.put("tracktype", "grade1");
        OSMWay osmWay = new OSMWay(1, wayMap);

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        EncodingManager manager = new EncodingManager();
        BikeFlagEncoder bikeencoder = new BikeFlagEncoder();
        manager.registerEncoder(bikeencoder);
        MountainBikeFlagEncoder mountainbikeencoder = new MountainBikeFlagEncoder();
        manager.registerEncoder(mountainbikeencoder);
        

        // relation code for network rcn is VERY_NICE for bike and PREFER for mountainbike
        relMap.put("route", "bicycle");
        relMap.put("network", "rcn");
        long relFlags = manager.handleRelationTags(osmRel, 0);
        long allow = bikeencoder.acceptBit | mountainbikeencoder.acceptBit;
        long flags = manager.handleWayTags(osmWay, allow, relFlags);

        //Uninfluenced speed for grade1 bikeencoder = 4 (pushing section) -> smaller than 15 -> VERYNICE -> 18
        assertEquals(20, bikeencoder.getSpeed(flags));
        //Uninfluenced speed for grade1 bikeencoder = 12 -> smaller than 15 -> PREFER -> 18
        assertEquals(18, mountainbikeencoder.getSpeed(flags));
    }
    
    public void testFullBitMask()
    {
        BitUtil bitUtil = BitUtil.LITTLE;
        EncodingManager manager = new EncodingManager("CAR,FOOT");
        AbstractFlagEncoder carr = (AbstractFlagEncoder) manager.getEncoder("CAR");
        assertTrue(bitUtil.toBitString(carr.getNodeBitMask()).endsWith("00000000001111111"));

        AbstractFlagEncoder foot = (AbstractFlagEncoder) manager.getEncoder("FOOT");
        assertTrue(bitUtil.toBitString(foot.getNodeBitMask()).endsWith("00011111110000000"));
    }

    @Test
    public void testApplyNodeTags()
    {
        EncodingManager manager = new EncodingManager();
        CarFlagEncoder car = new CarFlagEncoder();
        manager.registerEncoder(car);
        CarFlagEncoder car2 = new CarFlagEncoder(7, 1)
        {
            protected EncodedValue nodeEncoder;

            @Override
            public int defineNodeBits( int index, int shift )
            {
                shift = super.defineNodeBits(index, shift);
                nodeEncoder = new EncodedValue("nodeEnc", shift, 2, 1, 0, 3);
                return shift + 2;
            }

            @Override
            public long analyzeNodeTags( OSMNode node )
            {
                String tmp = node.getTags().get("test");
                // return negative value to indicate that this is not a barrier
                if (tmp == null)
                    return -nodeEncoder.setValue(0, 1);
                return -nodeEncoder.setValue(0, 2);
            }

            @Override
            public long applyNodeFlags( long wayFlags, long nodeFlags )
            {
                int speed = (int) speedEncoder.getValue(wayFlags);
                int speedDecrease = (int) nodeEncoder.getValue(nodeFlags);
                return speedEncoder.setValue(wayFlags, speed - speedDecrease);
            }
        };
        manager.registerEncoder(car2);

        Map<String, String> nodeMap = new HashMap<String, String>();
        OSMNode node = new OSMNode(1, nodeMap, Double.NaN, Double.NaN);
        Map<String, String> wayMap = new HashMap<String, String>();
        wayMap.put("highway", "secondary");
        OSMWay way = new OSMWay(2, wayMap);

        long wayFlags = manager.handleWayTags(way, manager.acceptWay(way), 0);
        long nodeFlags = manager.analyzeNodeTags(node);
        wayFlags = manager.applyNodeFlags(wayFlags, -nodeFlags);
        assertEquals(60, car.getSpeed(wayFlags));
        assertEquals(59, car2.getSpeed(wayFlags));

        nodeMap.put("test", "something");
        wayFlags = manager.handleWayTags(way, manager.acceptWay(way), 0);
        nodeFlags = manager.analyzeNodeTags(node);
        wayFlags = manager.applyNodeFlags(wayFlags, -nodeFlags);
        assertEquals(58, car2.getSpeed(wayFlags));
        assertEquals(60, car.getSpeed(wayFlags));

        wayMap.put("maxspeed", "130");
        wayFlags = manager.handleWayTags(way, manager.acceptWay(way), 0);
        assertEquals(car.getMaxSpeed(), car2.getSpeed(wayFlags));
        nodeFlags = manager.analyzeNodeTags(node);
        wayFlags = manager.applyNodeFlags(wayFlags, -nodeFlags);
        assertEquals(98, car2.getSpeed(wayFlags));
        assertEquals(100, car.getSpeed(wayFlags));
    }

    /**
     * Tests the combination of different turn cost flags by different encoders.
     */
    @Test
    public void testTurnFlagCombination()
    {
        final TurnCostTableEntry turnCostEntry_car = new TurnCostTableEntry();
        final TurnCostTableEntry turnCostEntry_foot = new TurnCostTableEntry();
        final TurnCostTableEntry turnCostEntry_bike = new TurnCostTableEntry();

        EncodingManager manager = new EncodingManager();
        CarFlagEncoder car = new CarFlagEncoder()
        {
            public Collection<TurnCostTableEntry> analyzeTurnRelation( OSMTurnRelation turnRelation, OSMReader osmReader )
            {
                return Collections.singleton(turnCostEntry_car); //simulate by returning one turn cost entry directly
            };
        };
        FootFlagEncoder foot = new FootFlagEncoder()
        {
            public Collection<TurnCostTableEntry> analyzeTurnRelation( OSMTurnRelation turnRelation, OSMReader osmReader )
            {
                return Collections.singleton(turnCostEntry_foot); //simulate by returning one turn cost entry directly
            };
        };
        BikeFlagEncoder bike = new BikeFlagEncoder()
        {
            public Collection<TurnCostTableEntry> analyzeTurnRelation( OSMTurnRelation turnRelation, OSMReader osmReader )
            {
                return Collections.singleton(turnCostEntry_bike); //simulate by returning one turn cost entry directly
            };
        };

        manager.maxTurnCost = 127; //allow turn costs

        manager.registerEncoder(bike);
        manager.registerEncoder(foot);
        manager.registerEncoder(car);

        //turn cost entries for car and foot are for the same relations (same viaNode, edgeFrom and edgeTo), turn cost entry for bike is for another relation (different viaNode) 
        turnCostEntry_car.edgeFrom = 1;
        turnCostEntry_foot.edgeFrom = 1;
        turnCostEntry_bike.edgeFrom = 2;

        //calculating arbitrary flags using the encoders
        turnCostEntry_car.flags = car.getTurnFlags(true, 20);
        turnCostEntry_foot.flags = foot.getTurnFlags(true, 0);
        turnCostEntry_bike.flags = bike.getTurnFlags(false, 10);

        //we expect two different entries: the first one is a combination of turn flags of car and foot, since they provide the same relation, the other one is for bike only
        long assertFlag1 = turnCostEntry_car.flags | turnCostEntry_foot.flags;
        long assertFlag2 = turnCostEntry_bike.flags;

        //RUN: analyze = combine flags of all encoders
        Collection<TurnCostTableEntry> entries = manager.analyzeTurnRelation(null, null);

        assertEquals(2, entries.size()); //we expect two different turnCost entries

        for (TurnCostTableEntry entry : entries)
        {
            if (entry.edgeFrom == 1)
            { //the first entry provides turn flags for car and foot only 
                assertEquals(assertFlag1, entry.flags);
                assertTrue(car.isTurnRestricted(entry.flags));
                assertFalse(foot.isTurnRestricted(entry.flags));
                assertFalse(bike.isTurnRestricted(entry.flags));

                assertEquals(20, car.getTurnCosts(entry.flags));
                assertEquals(0, foot.getTurnCosts(entry.flags));
                assertEquals(0, bike.getTurnCosts(entry.flags));
            } else if (entry.edgeFrom == 2)
            { //the 2nd entry provides turn flags for bike only
                assertEquals(assertFlag2, entry.flags);
                assertFalse(car.isTurnRestricted(entry.flags));
                assertFalse(foot.isTurnRestricted(entry.flags));
                assertFalse(bike.isTurnRestricted(entry.flags));

                assertEquals(0, car.getTurnCosts(entry.flags));
                assertEquals(0, foot.getTurnCosts(entry.flags));
                assertEquals(10, bike.getTurnCosts(entry.flags));
            }
        }

    }

}
