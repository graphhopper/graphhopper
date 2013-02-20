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

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class CarFlagsEncoderTest {

    @Test
    public void testBasics() {
        CarFlagsEncoder encoder = new CarFlagsEncoder();
        
        assertTrue(encoder.isForward(encoder.flagsDefault(true)));
        assertTrue(encoder.isBackward(encoder.flagsDefault(true)));

        encoder = new CarFlagsEncoder();
        assertTrue(encoder.isForward(encoder.flagsDefault(false)));
        assertFalse(encoder.isBackward(encoder.flagsDefault(false)));        
    }

    @Test
    public void testSwapDir() {
        CarFlagsEncoder fl = new CarFlagsEncoder();
        int swappedFlags = fl.swapDirection(fl.flagsDefault(true));
        assertTrue(fl.isForward(swappedFlags));
        assertTrue(fl.isBackward(swappedFlags));

        swappedFlags = fl.swapDirection(fl.flagsDefault(false));
        fl = new CarFlagsEncoder();
        assertFalse(fl.isForward(swappedFlags));
        assertTrue(fl.isBackward(swappedFlags));
    }

    @Test
    public void testService() {
        Map<String, Object> p = new HashMap<String, Object>();        
        CarFlagsEncoder fl = new CarFlagsEncoder();
        p.put("car", fl.getSpeed("service"));
        int flags = new AcceptWay(true, false, false, false).toFlags(p);
        assertTrue(fl.isForward(flags));
        assertTrue(fl.isBackward(flags));
        assertTrue(fl.isService(flags));
    }

    @Test
    public void testTime() {
        WeightCalculation wc = FastestCalc.CAR;
        assertEquals(60 * 60, wc.getTime(100000, new CarFlagsEncoder().flags(100, true)));
    }
}