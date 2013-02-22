/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class FootFlagsEncoderTest {

    private FootFlagsEncoder encoder = new FootFlagsEncoder();

    @Test
    public void testGetSpeed() {
        int fl = encoder.flags(10, true);
        assertEquals(10, encoder.getSpeed(fl));
    }
    
    @Test
    public void testBasics() {
        int fl = encoder.flagsDefault(true);
        assertEquals(encoder.getSpeed("mean").intValue(), encoder.getSpeed(fl));
        
        int fl1 = encoder.flagsDefault(false);
        int fl2 = encoder.swapDirection(fl1);
        assertEquals(encoder.getSpeed(fl2), encoder.getSpeed(fl1));
    }
    
    @Test
    public void testCombined() {
        FlagsEncoder carEncoder = new CarFlagsEncoder();
        int fl = encoder.flags(10, true) | carEncoder.flags(100, false);
        assertEquals(10, encoder.getSpeed(fl));
        assertTrue(encoder.isForward(fl));
        assertTrue(encoder.isBackward(fl));
        
        assertEquals(100, carEncoder.getSpeed(fl));        
        assertTrue(carEncoder.isForward(fl));
        assertFalse(carEncoder.isBackward(fl));
    }
}