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

class FerrySpeedCalculatorTest {

    @Test
    void testSpeed() {
        double speedFactor = 2;
        double maxSpeed = 55;
        double longSpeed = 30;
        double shortSpeed = 20;
        double unknownSpeed = 5;
        FerrySpeedCalculator c = new FerrySpeedCalculator(speedFactor, maxSpeed, longSpeed, shortSpeed, unknownSpeed);

        // no distance -> speed only depends on duration (distinguish between missing/short/long duration)
        checkSpeed(c, null, null, unknownSpeed);
        checkSpeed(c, "0", null, unknownSpeed);
        checkSpeed(c, "1800", null, shortSpeed);
        checkSpeed(c, "7200", null, longSpeed);
        // no duration -> speed depends on distance
        checkSpeed(c, null, 100.0, speedFactor / 2);
        checkSpeed(c, "0", 100.0, speedFactor / 2);
        checkSpeed(c, null, 1000.0, unknownSpeed);
        checkSpeed(c, "0", 1000.0, unknownSpeed);

        // valid
        checkSpeed(c, "3600", 30000.0, Math.round(30 / 1.4));
        checkSpeed(c, "7200", 30000.0, Math.round(15 / 1.4));
        // above max (capped to max)
        checkSpeed(c, "3600", 90000.0, maxSpeed);
        // below smallest storable non-zero value
        checkSpeed(c, "7200", 1000.0, speedFactor / 2);

        // suspicious slow speed (still depends on distance)
        checkSpeed(c, "180000", 100.0, speedFactor / 2);
        checkSpeed(c, "1800000", 1000.0, unknownSpeed);
    }

    private void checkSpeed(FerrySpeedCalculator calc, String duration, Double distance, double expected) {
        ReaderWay way = new ReaderWay(0L);
        if (duration != null)
            way.setTag("duration:seconds", duration);
        if (distance != null)
            way.setTag("estimated_distance", distance);
        assertEquals(expected, calc.getSpeed(way));
    }

    // ORS-GH MOD START
    @Test
    void testMaxSpeedTag() {
        double speedFactor = 2;
        double maxSpeed = 30;
        double longSpeed = 30;
        double shortSpeed = 20;
        double unknownSpeed = 5;
        FerrySpeedCalculator c = new FerrySpeedCalculator(speedFactor, maxSpeed, longSpeed, shortSpeed, unknownSpeed);

        // valid
        checkMaxSpeed(c, "14", 10);
        // above max (capped to max)
        checkMaxSpeed(c, "45", maxSpeed);
        // below smallest storable non-zero value
        checkMaxSpeed(c, "1", speedFactor / 2);
        // invalid value
        checkMaxSpeed(c, "foo", unknownSpeed);
    }

    private void checkMaxSpeed(FerrySpeedCalculator calc, String maxspeed, double expected) {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("maxspeed", maxspeed);
        assertEquals(expected, calc.getSpeed(way));
    }
    // ORS-GH MOD END
}