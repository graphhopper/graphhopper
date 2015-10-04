/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.reader;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author Peter Karich, ratrun
 */
public class OSMTagParserTest
{
    @Test
    public void testParseDuration()
    {
        assertEquals(10 * 60, OSMTagParser.parseDuration("00:10"));
        assertEquals(35 * 60, OSMTagParser.parseDuration("35"));
        assertEquals(70 * 60, OSMTagParser.parseDuration("01:10"));
        assertEquals(70 * 60 + 2, OSMTagParser.parseDuration("01:10:02"));
        assertEquals(0, OSMTagParser.parseDuration(null));
        assertEquals(60 * 20 * 60, OSMTagParser.parseDuration("20:00"));
        assertEquals(20 * 60, OSMTagParser.parseDuration("0:20:00"));
        assertEquals((60 * 2 + 20) * 60 + 2, OSMTagParser.parseDuration("02:20:02"));
        assertTrue(87840 * 60 <= OSMTagParser.parseDuration("P2M"));
        assertTrue(87900 * 60 >= OSMTagParser.parseDuration("P2M"));
        assertEquals(2 * 60, OSMTagParser.parseDuration("PT2M"));
        assertEquals((5 * 60 + 12) * 60 + 36, OSMTagParser.parseDuration("PT5H12M36S"));
    }

    @Test
    public void testWrongDurationFormats()
    {
        try
        {
            OSMTagParser.parseDuration("PT5h12m36s");
            fail("parseDuration didn't throw when I expected it to");
        } catch (IllegalArgumentException expectedException)
        {
            assertEquals(expectedException.getMessage(), "Cannot parse duration tag value: PT5h12m36s");
        }
        try
        {
            OSMTagParser.parseDuration("oh");
            fail("parseDuration didn't throw when I expected it to");
        } catch (IllegalArgumentException expectedException)
        {
            assertEquals(expectedException.getMessage(), "Cannot parse duration tag value: oh");
        }
        try
        {
            OSMTagParser.parseDuration("01:10:2");
            fail("parseDuration didn't throw when I expected it to");
        } catch (IllegalArgumentException expectedException)
        {
            assertEquals(expectedException.getMessage(), "Cannot parse duration tag value: 01:10:2");
        }

    }
}
