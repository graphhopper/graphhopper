/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class EncodingManagerTest {

    @Test
    public void testCompatibility() {
        EncodingManager manager = EncodingManager.create("car,bike,foot");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("bike");
        CarFlagEncoder car = (CarFlagEncoder) manager.getEncoder("car");
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("foot");
        assertNotEquals(car, bike);
        assertNotEquals(car, foot);
        assertNotEquals(car.hashCode(), bike.hashCode());
        assertNotEquals(car.hashCode(), foot.hashCode());

        FootFlagEncoder foot2 = new FootFlagEncoder();
        EncodingManager.create(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        FootFlagEncoder foot3 = new FootFlagEncoder();
        EncodingManager.create(foot3);
        assertEquals(foot3, foot2);
        assertEquals(foot3.hashCode(), foot2.hashCode());

        try {
            EncodingManager.create("car,car");
            fail("there should have been an exception, do not allow duplicate flag encoders");
        } catch (Exception ex) {
            // ok
        }
    }

    @Test
    public void testEncoderAcceptNoException() {
        EncodingManager manager = EncodingManager.create("car");
        assertTrue(manager.hasEncoder("car"));
        assertFalse(manager.hasEncoder("foot"));
    }

    @Test
    public void testWrongEncoders() {
        try {
            FootFlagEncoder foot = new FootFlagEncoder();
            EncodingManager.create(foot, foot);
            fail("There should have been an exception");
        } catch (Exception ex) {
            assertEquals("You must not register a FlagEncoder (foot) twice or for two EncodingManagers!", ex.getMessage());
        }
    }

    @Test
    public void testToDetailsStringIncludesEncoderVersionNumber() {
        FlagEncoder encoder = new AbstractFlagEncoder(1, 2.0, 0) {
            @Override
            public TransportationMode getTransportationMode() {
                return TransportationMode.BIKE;
            }

            @Override
            public int getVersion() {
                return 10;
            }

            @Override
            protected String getPropertiesString() {
                return "my_properties";
            }

            @Override
            public EncodingManager.Access getAccess(ReaderWay way) {
                return EncodingManager.Access.WAY;
            }

            @Override
            public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access accept) {
                return edgeFlags;
            }

            @Override
            public String toString() {
                return "new_encoder";
            }
        };

        EncodingManager subject = EncodingManager.create(encoder);

        assertEquals("new_encoder|my_properties|version=10", subject.toFlagEncodersAsString());
    }

    @Test
    public void testCombineRelations() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        ReaderRelation osmRel = new ReaderRelation(1);

        BikeFlagEncoder defaultBike = new BikeFlagEncoder();
        BikeFlagEncoder lessRelationCodes = new BikeFlagEncoder() {
            @Override
            public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access) {
                if (bikeRouteEnc.getEnum(false, edgeFlags) != RouteNetwork.MISSING)
                    priorityEnc.setDecimal(false, edgeFlags, PriorityCode.getFactor(2));
                return edgeFlags;
            }

            @Override
            public String toString() {
                return "less_relations_bits";
            }
        };
        EncodingManager manager = new EncodingManager.Builder().add(lessRelationCodes).add(defaultBike).build();

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        IntsRef relFlags = manager.handleRelationTags(osmRel, manager.createRelationFlags());
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        IntsRef edgeFlags = manager.handleWayTags(osmWay, map, relFlags);

        EnumEncodedValue enc = manager.getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);

        assertTrue(defaultBike.priorityEnc.getDecimal(false, edgeFlags)
                > lessRelationCodes.priorityEnc.getDecimal(false, edgeFlags));
    }

    @Test
    public void testMixBikeTypesAndRelationCombination() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        osmWay.setTag("tracktype", "grade1");

        ReaderRelation osmRel = new ReaderRelation(1);

        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
        MountainBikeFlagEncoder mtbEncoder = new MountainBikeFlagEncoder();
        EncodingManager manager = EncodingManager.create(bikeEncoder, mtbEncoder);

        // relation code for network rcn is VERY_NICE for bike and PREFER for mountainbike
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "rcn");
        IntsRef relFlags = manager.handleRelationTags(osmRel, manager.createRelationFlags());
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        IntsRef edgeFlags = manager.handleWayTags(osmWay, map, relFlags);

        // bike: uninfluenced speed for grade but via network => VERY_NICE                
        // mtb: uninfluenced speed only PREFER
        assertTrue(bikeEncoder.priorityEnc.getDecimal(false, edgeFlags)
                > mtbEncoder.priorityEnc.getDecimal(false, edgeFlags));
    }

    @Test
    public void testFixWayName() {
        assertEquals("B8, B12", EncodingManager.fixWayName("B8;B12"));
        assertEquals("B8, B12", EncodingManager.fixWayName("B8; B12"));
    }

    @Test
    public void testCompatibilityBug() {
        EncodingManager manager2 = EncodingManager.create(new DefaultFlagEncoderFactory(), "bike2");
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "footway");
        osmWay.setTag("name", "test");

        BikeFlagEncoder singleBikeEnc = (BikeFlagEncoder) manager2.getEncoder("bike2");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager2.acceptWay(osmWay, map);
        IntsRef flags = manager2.handleWayTags(osmWay, map, manager2.createRelationFlags());
        double singleSpeed = singleBikeEnc.avgSpeedEnc.getDecimal(false, flags);
        assertEquals(4, singleSpeed, 1e-3);
        assertEquals(singleSpeed, singleBikeEnc.avgSpeedEnc.getDecimal(true, flags), 1e-3);

        EncodingManager manager = EncodingManager.create(new DefaultFlagEncoderFactory(), "bike2,bike,foot");
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("foot");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("bike2");

        map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        flags = manager.handleWayTags(osmWay, map, manager.createRelationFlags());
        assertEquals(singleSpeed, bike.avgSpeedEnc.getDecimal(false, flags), 1e-2);
        assertEquals(singleSpeed, bike.avgSpeedEnc.getDecimal(true, flags), 1e-2);

        assertEquals(5, foot.avgSpeedEnc.getDecimal(false, flags), 1e-2);
        assertEquals(5, foot.avgSpeedEnc.getDecimal(true, flags), 1e-2);
    }

    @Test
    public void testSupportFords() {
        // 1) no encoder crossing fords
        String flagEncodersStr = "car,bike,foot";
        EncodingManager manager = EncodingManager.create(new DefaultFlagEncoderFactory(), flagEncodersStr);

        assertFalse(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) two encoders crossing fords
        flagEncodersStr = "car,bike|block_fords=true,foot|block_fords=false";
        manager = EncodingManager.create(new DefaultFlagEncoderFactory(), flagEncodersStr);

        assertFalse(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertTrue(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) Try combined with another tag
        flagEncodersStr = "car|turn_costs=true|block_fords=true,bike,foot|block_fords=false";
        manager = EncodingManager.create(new DefaultFlagEncoderFactory(), flagEncodersStr);

        assertTrue(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());
    }

    @Test
    public void testSharedEncodedValues() {
        EncodingManager manager = EncodingManager.create("car,foot,bike,motorcycle,mtb");

        for (FlagEncoder tmp : manager.fetchEdgeEncoders()) {
            AbstractFlagEncoder encoder = (AbstractFlagEncoder) tmp;
            BooleanEncodedValue accessEnc = encoder.getAccessEnc();
            BooleanEncodedValue roundaboutEnc = manager.getBooleanEncodedValue(Roundabout.KEY);

            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", "primary");
            way.setTag("junction", "roundabout");
            EncodingManager.AcceptWay aw = new EncodingManager.AcceptWay();
            manager.acceptWay(way, aw);
            IntsRef edgeFlags = manager.handleWayTags(way, aw, manager.createRelationFlags());
            assertTrue(accessEnc.getBool(false, edgeFlags));
            if (!encoder.toString().equals("foot"))
                assertFalse(encoder.toString(), accessEnc.getBool(true, edgeFlags));
            assertTrue(encoder.toString(), roundaboutEnc.getBool(false, edgeFlags));

            way.clearTags();
            way.setTag("highway", "tertiary");
            way.setTag("junction", "circular");
            aw = new EncodingManager.AcceptWay();
            manager.acceptWay(way, aw);
            edgeFlags = manager.handleWayTags(way, aw, manager.createRelationFlags());
            assertTrue(accessEnc.getBool(false, edgeFlags));
            if (!encoder.toString().equals("foot"))
                assertFalse(encoder.toString(), accessEnc.getBool(true, edgeFlags));
            assertTrue(encoder.toString(), roundaboutEnc.getBool(false, edgeFlags));
        }
    }

    @Test
    public void validEV() {
        for (String str : Arrays.asList("blup_test", "test", "test12", "tes$0", "car_test_test", "small_car$average_speed")) {
            assertTrue(str, EncodingManager.isValidEncodedValue(str));
        }

        for (String str : Arrays.asList("Test", "12test", "test|3", "car__test", "blup_te.st_", "car___test", "car$$access",
                "test{34", "truck__average_speed", "blup.test", "test,21", "täst", "blup.two.three", "blup..test")) {
            assertFalse(str, EncodingManager.isValidEncodedValue(str));
        }

        for (String str : Arrays.asList("break", "switch")) {
            assertFalse(str, EncodingManager.isValidEncodedValue(str));
        }
    }
}
