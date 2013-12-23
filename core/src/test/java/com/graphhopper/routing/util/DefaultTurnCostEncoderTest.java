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
package com.graphhopper.routing.util;

import org.junit.Assert;
import org.junit.Test;

public class DefaultTurnCostEncoderTest
{

    @Test
    public void testEncoding_max127_noBitShift()
    {
        TurnCostEncoder encoder = new DefaultTurnCostEncoder(127);
        testFlags(encoder);
    }

    @Test
    public void testEncoding_max127_arbitraryBitShift()
    {
        TurnCostEncoder encoder = new DefaultTurnCostEncoder(127);
        int newShift = encoder.defineBits(1, 13);
        Assert.assertEquals(13 + 8, newShift);

        testFlags(encoder);
    }

    @Test
    public void testEncoding_max31_max50_multipleFlags()
    {
        TurnCostEncoder encoder1 = new DefaultTurnCostEncoder(31);
        int shift = encoder1.defineBits(0, 0);
        Assert.assertEquals(6, shift);

        TurnCostEncoder encoder2 = new DefaultTurnCostEncoder(50);
        shift = encoder2.defineBits(1, shift);
        Assert.assertEquals(6 + 7, shift);

        int flags_100_r42 = encoder1.flags(false, 100) | encoder2.flags(true, 42);
        int flags_0_128 = encoder1.flags(false, 0) | encoder2.flags(false, 128);
        int flags_0_r9 = encoder1.flags(false, 0) | encoder2.flags(true, 9);
        int flags_r100_r200 = encoder1.flags(true, 100) | encoder2.flags(true, 200);

        Assert.assertFalse(encoder1.isRestricted(flags_100_r42));
        Assert.assertFalse(encoder1.isRestricted(flags_0_128));
        Assert.assertFalse(encoder1.isRestricted(flags_0_r9));
        Assert.assertTrue(encoder1.isRestricted(flags_r100_r200));

        Assert.assertTrue(encoder2.isRestricted(flags_100_r42));
        Assert.assertFalse(encoder2.isRestricted(flags_0_128));
        Assert.assertTrue(encoder2.isRestricted(flags_0_r9));
        Assert.assertTrue(encoder2.isRestricted(flags_r100_r200));

        Assert.assertEquals(31, encoder1.getCosts(flags_100_r42)); //max value is 31 for encoder 1, since it has been initialized with max=31(31 needs 5 bits => 2^5-1 = 31)
        Assert.assertEquals(0, encoder1.getCosts(flags_0_128));
        Assert.assertEquals(0, encoder1.getCosts(flags_0_r9));
        Assert.assertEquals(31, encoder1.getCosts(flags_r100_r200)); //max value is 31 for encoder 1, since it has been initialized with max=31 (31 needs 5 bits => 2^5-1 = 31)

        Assert.assertEquals(42, encoder2.getCosts(flags_100_r42));
        Assert.assertEquals(63, encoder2.getCosts(flags_0_128)); //max value is 63 for encoder 2, since it has been initialized with max=50 (50 needs 6 bits => 2^6-1 = 63)
        Assert.assertEquals(9, encoder2.getCosts(flags_0_r9));
        Assert.assertEquals(63, encoder2.getCosts(flags_r100_r200)); //max value is 63 for encoder 2, since it has been initialized with max=50 (50 needs 6 bits => 2^6-1 = 63)

    }

    @Test
    public void testEncoding_restrictionsOnly()
    {
        TurnCostEncoder encoder1 = new DefaultTurnCostEncoder();
        int shift = encoder1.defineBits(0, 0);
        Assert.assertEquals(1, shift);

        TurnCostEncoder encoder2 = new DefaultTurnCostEncoder(0);
        shift = encoder2.defineBits(1, shift);
        Assert.assertEquals(2, shift);

        int flags_100_r42 = encoder1.flags(false, 100) | encoder2.flags(true, 42);
        int flags_0_128 = encoder1.flags(false, 0) | encoder2.flags(false, 128);
        int flags_0_r9 = encoder1.flags(false, 0) | encoder2.flags(true, 9);
        int flags_r100_r200 = encoder1.flags(true, 100) | encoder2.flags(true, 200);

        Assert.assertFalse(encoder1.isRestricted(flags_100_r42));
        Assert.assertFalse(encoder1.isRestricted(flags_0_128));
        Assert.assertFalse(encoder1.isRestricted(flags_0_r9));
        Assert.assertTrue(encoder1.isRestricted(flags_r100_r200));

        Assert.assertTrue(encoder2.isRestricted(flags_100_r42));
        Assert.assertFalse(encoder2.isRestricted(flags_0_128));
        Assert.assertTrue(encoder2.isRestricted(flags_0_r9));
        Assert.assertTrue(encoder2.isRestricted(flags_r100_r200));

        Assert.assertEquals(0, encoder1.getCosts(flags_100_r42));
        Assert.assertEquals(0, encoder1.getCosts(flags_0_128));
        Assert.assertEquals(0, encoder1.getCosts(flags_0_r9));
        Assert.assertEquals(0, encoder1.getCosts(flags_r100_r200));

        Assert.assertEquals(0, encoder2.getCosts(flags_100_r42));
        Assert.assertEquals(0, encoder2.getCosts(flags_0_128));
        Assert.assertEquals(0, encoder2.getCosts(flags_0_r9));
        Assert.assertEquals(0, encoder2.getCosts(flags_r100_r200));

    }

    public void testFlags( TurnCostEncoder encoder )
    {
        int flags_0 = encoder.flags(false, 0);
        int flags_100 = encoder.flags(false, 100);
        int flags_127 = encoder.flags(false, 127);
        int flags_128 = encoder.flags(false, 128);

        int flags_r_0 = encoder.flags(true, 0);
        int flags_r_100 = encoder.flags(true, 100);
        int flags_r_127 = encoder.flags(true, 127);
        int flags_r_128 = encoder.flags(true, 128);

        Assert.assertFalse(encoder.isRestricted(flags_0));
        Assert.assertFalse(encoder.isRestricted(flags_100));
        Assert.assertFalse(encoder.isRestricted(flags_127));
        Assert.assertFalse(encoder.isRestricted(flags_128));

        Assert.assertTrue(encoder.isRestricted(flags_r_0));
        Assert.assertTrue(encoder.isRestricted(flags_r_100));
        Assert.assertTrue(encoder.isRestricted(flags_r_127));
        Assert.assertTrue(encoder.isRestricted(flags_r_128));

        Assert.assertEquals(0, encoder.getCosts(flags_0));
        Assert.assertEquals(100, encoder.getCosts(flags_100));
        Assert.assertEquals(127, encoder.getCosts(flags_127));
        Assert.assertEquals(127, encoder.getCosts(flags_128));

        Assert.assertEquals(0, encoder.getCosts(flags_r_0));
        Assert.assertEquals(100, encoder.getCosts(flags_r_100));
        Assert.assertEquals(127, encoder.getCosts(flags_r_127));
        Assert.assertEquals(127, encoder.getCosts(flags_r_128));
    }

}
