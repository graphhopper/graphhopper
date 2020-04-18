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

import static com.graphhopper.routing.util.AbstractFlagEncoder.parseSpeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class AbstractFlagEncoderTest {
    @Test
    public void testParseSpeed() {
        assertEquals(40, parseSpeed("40 km/h"), 1e-3);
        assertEquals(40, parseSpeed("40km/h"), 1e-3);
        assertEquals(40, parseSpeed("40kmh"), 1e-3);
        assertEquals(64.374, parseSpeed("40mph"), 1e-3);
        assertEquals(48.28, parseSpeed("30 mph"), 1e-3);
        assertEquals(-1, parseSpeed(null), 1e-3);
        assertEquals(18.52, parseSpeed("10 knots"), 1e-3);
        assertEquals(19, parseSpeed("19 kph"), 1e-3);
        assertEquals(19, parseSpeed("19kph"), 1e-3);

        assertEquals(50, parseSpeed("RO:urban"), 1e-3);

        assertEquals(80, parseSpeed("RU:rural"), 1e-3);

        assertEquals(6, parseSpeed("walk"), 1e-3);
    }
}
