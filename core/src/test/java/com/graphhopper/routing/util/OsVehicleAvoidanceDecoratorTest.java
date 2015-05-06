package com.graphhopper.routing.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.itn.OSITNWay;

public class OsVehicleAvoidanceDecoratorTest {
	OsVehicleAvoidanceDecorator osAvoidances = new OsVehicleAvoidanceDecorator();

    @Before
    public void defineWayBits() {
        osAvoidances.defineWayBits(0);
    }

    @Test
    public void testMotorwayAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("highway", "motorway");
        long wayFlag = osAvoidances.handleWayTags(way);
        assertEquals(OsVehicleAvoidanceDecorator.AvoidanceType.MOTORWAYS.getValue(), wayFlag);
        way.setTag("highway", "Motorway");
        wayFlag = osAvoidances.handleWayTags(way);
        assertEquals(OsVehicleAvoidanceDecorator.AvoidanceType.MOTORWAYS.getValue(), wayFlag);

    }

    @Test
    public void testTollAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("toll", "yes");
        long wayFlag = osAvoidances.handleWayTags(way);
        assertEquals(OsVehicleAvoidanceDecorator.AvoidanceType.TOLL.getValue(), wayFlag);

    }


    @Test
    public void testMultiAttributeStorage() {
        Way way = new OSITNWay(1L);
        way.setTag("highway", "motorway");
        way.setTag("toll", "yes");
        long wayFlag = osAvoidances.handleWayTags(way);
        //BITMASK test?
        assertEquals(OsVehicleAvoidanceDecorator.AvoidanceType.TOLL.getValue(), wayFlag - OsVehicleAvoidanceDecorator.AvoidanceType.MOTORWAYS.getValue());
        assertEquals(OsVehicleAvoidanceDecorator.AvoidanceType.MOTORWAYS.getValue(), wayFlag  - OsVehicleAvoidanceDecorator.AvoidanceType.TOLL.getValue() );

    }

}