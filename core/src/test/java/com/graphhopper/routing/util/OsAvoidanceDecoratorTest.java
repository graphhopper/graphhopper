package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

import com.graphhopper.reader.osgb.itn.OSITNWay;
import com.graphhopper.util.InstructionAnnotation;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class OsAvoidanceDecoratorTest {
    OsAvoidanceDecorator osAvoidances = new OsAvoidanceDecorator();

    @Before
    public void defineWayBits() {
        osAvoidances.defineWayBits(0);
    }

    @Test
    public void testBoulderAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "boulders");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Boulders.getValue(), wayFlag);

    }

    @Test
    public void testCliffAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "cliff");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Cliff.getValue(), wayFlag);

    }

    @Test
    public void testMarshAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("wetland", "marsh");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Marsh.getValue(), wayFlag);

    }

    @Test
    public void testMudAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "mud");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Mud.getValue(), wayFlag);

    }

    @Test
    public void testSandAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "sand");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Sand.getValue(), wayFlag);

    }

    @Test
    public void testScreeAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "scree");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Scree.getValue(), wayFlag);

    }

    @Test
    public void testShingleAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "shingle");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Shingle.getValue(), wayFlag);

    }

//    @Test
//    public void testSpoilAttributeStorage() {
//        Way way = new OSITNWay(1L);
//        way.setTag("natural", "spoil");
//        long wayFlag = osAvoidances.handleWayTags(way,0);
//        assertEquals(OsAvoidanceDecorator.AvoidanceType.Spoil.getValue(), wayFlag);
//
//    }

//    @Test
//    public void testTidalWaterAttributeStorage() {
//        Way way = new OSITNWay(1L);
//        way.setTag("natural", "water");
//        way.setTag("tidal", "yes");
//        long wayFlag = osAvoidances.handleWayTags(way,0);
//        assertEquals(OsAvoidanceDecorator.AvoidanceType.TidalWater.getValue(), wayFlag);
//
//    }

   @Test
    public void testInlandWaterAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "water");
        way.setTag("tidal","no");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.InlandWater.getValue(), wayFlag);

    }
   
   @Test
   public void testARoadAttributeStorage() {
       Way way = new OSITNWay(1L);
       way.setTag("highway", "primary");
       long wayFlag = osAvoidances.handleWayTags(way,0);
       assertEquals(OsAvoidanceDecorator.AvoidanceType.ARoad.getValue(), wayFlag);

   }

    @Test
    public void testQuarryOrPitAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "excavation");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        assertEquals(OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue(), wayFlag);

    }

    @Test
    public void testMultiAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("wetland", "marsh");
        way.setTag("natural", "excavation");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        //BITMASK test?
        assertEquals(OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue(), wayFlag - OsAvoidanceDecorator.AvoidanceType.Marsh.getValue());
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Marsh.getValue(), wayFlag  - OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue() );

    }

    @Test
    public void testMultiEqualAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "scree,excavation");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        //BITMASK test?
        assertEquals(OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue(), wayFlag - OsAvoidanceDecorator.AvoidanceType.Scree.getValue());
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Scree.getValue(), wayFlag  - OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue() );

    }

    @Test
    public void testMultiEqualAttributeRetrieval() {
        Way way = new OSITNWay(1L);
        way.setTag("natural", "scree,excavation");
        long wayFlag = osAvoidances.handleWayTags(way,0);
        //BITMASK test?
        assertEquals(OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue(), wayFlag - OsAvoidanceDecorator.AvoidanceType.Scree.getValue());
        assertEquals(OsAvoidanceDecorator.AvoidanceType.Scree.getValue(), wayFlag  - OsAvoidanceDecorator.AvoidanceType.QuarryOrPit.getValue() );
        InstructionAnnotation annotation = osAvoidances.getAnnotation(wayFlag, null);
        assertEquals(" QuarryOrPit Scree", annotation.getMessage());
    }

}