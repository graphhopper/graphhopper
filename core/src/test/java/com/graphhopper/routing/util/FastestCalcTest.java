/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
 * @author Peter Karich
 */
public class FastestCalcTest {

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        EdgePropertyEncoder encoder = new CarFlagEncoder();
        FastestCalc instance = new FastestCalc(encoder);
        int flags = encoder.flags(encoder.getMaxSpeed(), true);
        assertEquals(instance.getMinWeight(10), instance.getWeight(10, flags), 1e-8);
    }

    @Test
    public void testSpeed0() {
        EdgePropertyEncoder encoder = new CarFlagEncoder();
        FastestCalc instance = new FastestCalc(encoder);
        assertEquals(1.0 / 0, instance.getWeight(10, encoder.flags(0, true)), 1e-8);
    }
}
