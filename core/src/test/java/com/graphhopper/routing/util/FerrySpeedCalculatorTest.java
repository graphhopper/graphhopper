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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EdgeBytesAccessArray;
import com.graphhopper.routing.ev.FerrySpeed;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.FerrySpeedCalculator.getSpeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FerrySpeedCalculatorTest {

    final DecimalEncodedValue ferrySpeedEnc = FerrySpeed.create();
    final EncodingManager em = new EncodingManager.Builder().add(ferrySpeedEnc).build();
    final FerrySpeedCalculator calc = new FerrySpeedCalculator(ferrySpeedEnc);

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("edge_distance", 30000.0);
        way.setTag("speed_from_duration", 30 / 0.5);

        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        calc.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(44, ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess));

        way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("way_distance", 50000.0);
        way.setTag("speed_from_duration", 50 / (35.0 / 60));
        edgeAccess = new EdgeBytesAccessArray(4);
        // calculate speed from tags: speed_from_duration * 1.4 (+ rounded using the speed factor)
        calc.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(62, ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess));

        // test for very short and slow 0.5km/h still realistic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("way_distance", 100.0);
        way.setTag("speed_from_duration", 0.1 / (12.0 / 60));

        // we can't store 0.5km/h, but we expect the lowest possible speed
        edgeAccess = new EdgeBytesAccessArray(4);
        calc.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        ferrySpeedEnc.setDecimal(false, edgeId, edgeAccess, 2.5);
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess), 1e-1);

        // test for missing duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("edge_distance", 100.0);
        calc.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        // we use the unknown speed
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess));
    }

    @Test
    void testRawSpeed() {
        // speed_from_duration is set (edge_distance is not even needed)
        checkSpeed(30.0, null, Math.round(30 / 1.4));
        checkSpeed(45.0, null, Math.round(45 / 1.4));
        // above max (when including waiting time) (capped to max)
        checkSpeed(100.0, null, ferrySpeedEnc.getMaxStorableDecimal());
        // below smallest storable non-zero value
        checkSpeed(0.5, null, ferrySpeedEnc.getSmallestNonZeroValue());

        // no speed_from_duration, but edge_distance is present
        // minimum speed for short ferries
        checkSpeed(null, 100.0, ferrySpeedEnc.getSmallestNonZeroValue());
        // unknown speed for longer ones
        checkSpeed(null, 1000.0, 6);

        // no speed, no distance -> error. this should never happen as we always set the edge distance.
        assertThrows(IllegalStateException.class, () -> checkSpeed(null, null, 6));
    }

    private void checkSpeed(Double speedFromDuration, Double edgeDistance, double expected) {
        ReaderWay way = new ReaderWay(0L);
        if (speedFromDuration != null)
            way.setTag("speed_from_duration", speedFromDuration);
        if (edgeDistance != null)
            way.setTag("edge_distance", edgeDistance);
        assertEquals(expected, FerrySpeedCalculator.minmax(getSpeed(way), ferrySpeedEnc));
    }
}
