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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.BitUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class EncodingManagerTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

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
        EncodingManager manager2 = EncodingManager.create(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        FootFlagEncoder foot3 = new FootFlagEncoder();
        EncodingManager manager3 = EncodingManager.create(foot3);
        assertEquals(foot3, foot2);
        assertEquals(foot3.hashCode(), foot2.hashCode());

        try {
            EncodingManager.create("car,car");
            assertTrue("do not allow duplicate flag encoders", false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testEncoderAcceptNoException() {
        EncodingManager manager = EncodingManager.create("car");
        assertTrue(manager.hasEncoder("car"));
        assertFalse(manager.hasEncoder("foot"));
    }

    @Test
    public void testEncoderWithWrongVersionIsRejected() {
        thrown.expect(IllegalArgumentException.class);
        EncodingManager manager = EncodingManager.create("car|version=0");
    }

    @Test
    public void testWrongEncoders() {
        try {
            FootFlagEncoder foot = new FootFlagEncoder();
            EncodingManager.create(foot, foot);
            assertTrue(false);
        } catch (Exception ex) {
            assertEquals("You must not register a FlagEncoder (foot) twice!", ex.getMessage());
        }

        try {
            EncodingManager.create(new FootFlagEncoder(), new CarFlagEncoder(), new BikeFlagEncoder(), new MountainBikeFlagEncoder(), new RacingBikeFlagEncoder());
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoders are requesting 36 bits, more than 32 bits of edge flags"));
        }
    }

    @Test
    public void testToDetailsStringIncludesEncoderVersionNumber() {
        FlagEncoder encoder = new AbstractFlagEncoder(1, 2.0, 3) {
            @Override
            public int getVersion() {
                return 10;
            }

            @Override
            public String toString() {
                return "new_encoder";
            }

            @Override
            protected String getPropertiesString() {
                return "my_properties";
            }

            @Override
            public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
                return 0;
            }

            @Override
            public EncodingManager.Access getAccess(ReaderWay way) {
                return EncodingManager.Access.WAY;
            }

            @Override
            public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access accept, long relationFlags) {
                return edgeFlags;
            }
        };

        EncodingManager subject = EncodingManager.create(encoder);

        assertEquals("new_encoder|my_properties|version=10", subject.toDetailsString());
    }

    @Test
    public void testCombineRelations() {
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "track");
        ReaderRelation osmRel = new ReaderRelation(1);

        BikeFlagEncoder defaultBike = new BikeFlagEncoder();
        BikeFlagEncoder lessRelationCodes = new BikeFlagEncoder() {
            @Override
            public int defineRelationBits(int index, int shift) {
                relationCodeEncoder = new EncodedValueOld("RelationCode2", shift, 2, 1, 0, 3);
                return shift + 2;
            }

            @Override
            public long handleRelationTags(long oldRelFlags, ReaderRelation relation) {
                if (relation.hasTag("route", "bicycle"))
                    return relationCodeEncoder.setValue(0, 2);
                return relationCodeEncoder.setValue(0, 0);
            }

            @Override
            protected int handlePriority(ReaderWay way, double wayTypeSpeed, int priorityFromRelation) {
                return priorityFromRelation;
            }

            @Override
            public String toString() {
                return "less_relations_bits";
            }
        };
        EncodingManager manager = EncodingManager.create(defaultBike, lessRelationCodes);

        // relation code is PREFER
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");
        long relFlags = manager.handleRelationTags(0, osmRel);
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        IntsRef edgeFlags = manager.handleWayTags(osmWay, map, relFlags);

        assertTrue(defaultBike.relationCodeEncoder.getValue(edgeFlags.ints[0])
                > lessRelationCodes.relationCodeEncoder.getValue(edgeFlags.ints[0]));
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
        long relFlags = manager.handleRelationTags(0, osmRel);
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        IntsRef flags = manager.handleWayTags(osmWay, map, relFlags);

        // bike: uninfluenced speed for grade but via network => VERY_NICE                
        // mtb: uninfluenced speed only PREFER
        assertTrue(bikeEncoder.relationCodeEncoder.getValue(flags.ints[0])
                > mtbEncoder.relationCodeEncoder.getValue(flags.ints[0]));
    }

    public void testFullBitMask() {
        BitUtil bitUtil = BitUtil.LITTLE;
        EncodingManager manager = EncodingManager.create("car,foot");
        AbstractFlagEncoder carr = (AbstractFlagEncoder) manager.getEncoder("car");
        assertTrue(bitUtil.toBitString(carr.getNodeBitMask()).endsWith("00000000001111111"));

        AbstractFlagEncoder foot = (AbstractFlagEncoder) manager.getEncoder("foot");
        assertTrue(bitUtil.toBitString(foot.getNodeBitMask()).endsWith("00011111110000000"));
    }

    @Test
    public void testFixWayName() {
        assertEquals("B8, B12", EncodingManager.fixWayName("B8;B12"));
        assertEquals("B8, B12", EncodingManager.fixWayName("B8; B12"));
    }

    @Test
    public void testCompatibilityBug() {
        EncodingManager manager2 = EncodingManager.create(FlagEncoderFactory.DEFAULT, "bike2", 8);
        ReaderWay osmWay = new ReaderWay(1);
        osmWay.setTag("highway", "footway");
        osmWay.setTag("name", "test");

        BikeFlagEncoder singleBikeEnc = (BikeFlagEncoder) manager2.getEncoder("bike2");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        manager2.acceptWay(osmWay, map);
        IntsRef flags = manager2.handleWayTags(osmWay, map, 0);
        double singleSpeed = singleBikeEnc.getSpeed(flags);
        assertEquals(4, singleSpeed, 1e-3);
        assertEquals(singleSpeed, singleBikeEnc.getSpeed(true, flags), 1e-3);

        EncodingManager manager = EncodingManager.create(FlagEncoderFactory.DEFAULT, "bike2,bike,foot", 8);
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("foot");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("bike2");

        map = new EncodingManager.AcceptWay();
        manager.acceptWay(osmWay, map);
        flags = manager.handleWayTags(osmWay, map, 0);
        assertEquals(singleSpeed, bike.getSpeed(flags), 1e-2);
        assertEquals(singleSpeed, bike.getSpeed(true, flags), 1e-2);

        assertEquals(5, foot.getSpeed(flags), 1e-2);
        assertEquals(5, foot.getSpeed(true, flags), 1e-2);
    }

    @Test
    public void testSupportFords() {
        // 1) no encoder crossing fords
        String flagEncodersStr = "car,bike,foot";
        EncodingManager manager = EncodingManager.create(FlagEncoderFactory.DEFAULT, flagEncodersStr, 8);

        assertTrue(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertTrue(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertTrue(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) two encoders crossing fords
        flagEncodersStr = "car,bike|block_fords=false,foot|block_fords=false";
        manager = EncodingManager.create(FlagEncoderFactory.DEFAULT, flagEncodersStr, 8);

        assertTrue(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) Try combined with another tag
        flagEncodersStr = "car|turn_costs=true|block_fords=true,bike,foot|block_fords=false";
        manager = EncodingManager.create(FlagEncoderFactory.DEFAULT, flagEncodersStr, 8);

        assertTrue(((AbstractFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertTrue(((AbstractFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((AbstractFlagEncoder) manager.getEncoder("foot")).isBlockFords());
    }

    @Test
    public void testSharedEncodedValues() {
        EncodingManager manager = EncodingManager.create("car,foot,bike,motorcycle,mtb", 8);

        for (FlagEncoder tmp : manager.fetchEdgeEncoders()) {
            AbstractFlagEncoder encoder = (AbstractFlagEncoder) tmp;
            BooleanEncodedValue accessEnc = encoder.getAccessEnc();
            BooleanEncodedValue roundaboutEnc = manager.getBooleanEncodedValue(EncodingManager.ROUNDABOUT);

            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", "primary");
            way.setTag("junction", "roundabout");
            EncodingManager.AcceptWay aw = new EncodingManager.AcceptWay();
            manager.acceptWay(way, aw);
            IntsRef edgeFlags = manager.handleWayTags(way, aw, 0);
            assertTrue(accessEnc.getBool(false, edgeFlags));
            if (!encoder.toString().equals("foot"))
                assertFalse(encoder.toString(), accessEnc.getBool(true, edgeFlags));
            assertTrue(encoder.toString(), roundaboutEnc.getBool(false, edgeFlags));

            way.clearTags();
            way.setTag("highway", "tertiary");
            way.setTag("junction", "circular");
            aw = new EncodingManager.AcceptWay();
            manager.acceptWay(way, aw);
            edgeFlags = manager.handleWayTags(way, aw, 0);
            assertTrue(accessEnc.getBool(false, edgeFlags));
            if (!encoder.toString().equals("foot"))
                assertFalse(encoder.toString(), accessEnc.getBool(true, edgeFlags));
            assertTrue(encoder.toString(), roundaboutEnc.getBool(false, edgeFlags));
        }
    }
}
