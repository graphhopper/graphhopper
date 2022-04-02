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

import com.graphhopper.reader.ReaderWay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FerrySpeedCalculatorTest {

    @Test
    void testSpeed() {
        double minSpeed = 1;
        double maxSpeed = 55;
        double unknownSpeed = 5;
        FerrySpeedCalculator c = new FerrySpeedCalculator(minSpeed, maxSpeed, unknownSpeed);

        // speed_from_duration is set (edge_distance is not even needed)
        checkSpeed(c, 30.0, null, Math.round(30 / 1.4));
        checkSpeed(c, 45.0, null, Math.round(45 / 1.4));
        // above max (when including waiting time) (capped to max)
        checkSpeed(c, 100.0, null, maxSpeed);
        // below smallest storable non-zero value
        checkSpeed(c, 0.5, null, minSpeed);

        // no speed_from_duration, but edge_distance is present
        // minimum speed for short ferries
        checkSpeed(c, null, 100.0, minSpeed);
        // unknown speed for longer ones
        checkSpeed(c, null, 1000.0, unknownSpeed);

        // no speed, no distance -> error. this should never happen as we always set the edge distance.
        assertThrows(IllegalStateException.class, () -> checkSpeed(c, null, null, unknownSpeed));
    }

    private void checkSpeed(FerrySpeedCalculator calc, Double speedFromDuration, Double edgeDistance, double expected) {
        ReaderWay way = new ReaderWay(0L);
        if (speedFromDuration != null)
            way.setTag("speed_from_duration", speedFromDuration);
        if (edgeDistance != null)
            way.setTag("edge_distance", edgeDistance);
        assertEquals(expected, calc.getSpeed(way));
    }

}