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
package com.graphhopper.reader.osm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 * @author ratrun
 */
public class OSMReaderUtilityTest {
    @Test
    public void testParseDuration() {
        assertEquals(10 * 60, OSMReaderUtility.parseDuration("00:10"));
        assertEquals(35 * 60, OSMReaderUtility.parseDuration("35"));
        assertEquals(70 * 60, OSMReaderUtility.parseDuration("01:10"));
        assertEquals(70 * 60 + 2, OSMReaderUtility.parseDuration("01:10:02"));
        assertEquals(0, OSMReaderUtility.parseDuration(null));
        assertEquals(60 * 20 * 60, OSMReaderUtility.parseDuration("20:00"));
        assertEquals(20 * 60, OSMReaderUtility.parseDuration("0:20:00"));
        assertEquals((60 * 2 + 20) * 60 + 2, OSMReaderUtility.parseDuration("02:20:02"));

        // two months
        assertEquals(31 + 31, OSMReaderUtility.parseDuration("P2M") / (24 * 60 * 60));

        // two minutes
        assertEquals(2 * 60, OSMReaderUtility.parseDuration("PT2M"));
        assertEquals((5 * 60 + 12) * 60 + 36, OSMReaderUtility.parseDuration("PT5H12M36S"));
    }

    @Test
    public void testWrongDurationFormats() {
        assertParsDurationError("PT5h12m36s");
        assertParsDurationError("oh");
        assertParsDurationError("01:10:2");
    }

    private void assertParsDurationError(String value) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> OSMReaderUtility.parseDuration(value));
        assertEquals("Cannot parse duration tag value: " + value, e.getMessage());
    }
}
