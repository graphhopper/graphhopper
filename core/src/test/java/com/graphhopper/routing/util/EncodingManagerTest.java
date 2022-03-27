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

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class EncodingManagerTest {

    @Test
    public void duplicateNamesNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> EncodingManager.create("car,car"));
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
            assertEquals("FlagEncoder already exists: foot", ex.getMessage());
        }
    }

    @Test
    public void testSupportFords() {
        // 1) no encoder crossing fords
        String flagEncoderStrings = "car,bike,foot";
        EncodingManager manager = EncodingManager.create(flagEncoderStrings);

        assertFalse(((CarFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertFalse(((BikeFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((FootFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) two encoders crossing fords
        flagEncoderStrings = "car, bike|block_fords=true, foot|block_fords=false";
        manager = EncodingManager.create(flagEncoderStrings);

        assertFalse(((CarFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertTrue(((BikeFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((FootFlagEncoder) manager.getEncoder("foot")).isBlockFords());

        // 2) Try combined with another tag
        flagEncoderStrings = "car|turn_costs=true|block_fords=true, bike, foot|block_fords=false";
        manager = EncodingManager.create(flagEncoderStrings);

        assertTrue(((CarFlagEncoder) manager.getEncoder("car")).isBlockFords());
        assertFalse(((BikeFlagEncoder) manager.getEncoder("bike")).isBlockFords());
        assertFalse(((FootFlagEncoder) manager.getEncoder("foot")).isBlockFords());
    }

    @Test
    public void validEV() {
        for (String str : Arrays.asList("blup_test", "test", "test12", "tes$0", "car_test_test", "small_car$average_speed")) {
            assertTrue(EncodingManager.isValidEncodedValue(str), str);
        }

        for (String str : Arrays.asList("Test", "12test", "test|3", "car__test", "blup_te.st_", "car___test", "car$$access",
                "test{34", "truck__average_speed", "blup.test", "test,21", "täst", "blup.two.three", "blup..test")) {
            assertFalse(EncodingManager.isValidEncodedValue(str), str);
        }

        for (String str : Arrays.asList("break", "switch")) {
            assertFalse(EncodingManager.isValidEncodedValue(str), str);
        }
    }
}
