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
package com.graphhopper.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class RAMIntDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name, int segmentSize) {
        return new RAMIntDataAccess(name, directory, true, segmentSize);
    }

    @Override
    public void testSet_GetBytes() {
        // setBytes/getBytes still unsupported for RAMIntDataAccess
    }

    @Test
    public void testSetByte_AllOffsetsWithinInt() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        // write an int, then verify each byte individually
        da.setInt(8, 0x04030201);
        assertEquals((byte) 0x01, da.getByte(8));
        assertEquals((byte) 0x02, da.getByte(9));
        assertEquals((byte) 0x03, da.getByte(10));
        assertEquals((byte) 0x04, da.getByte(11));

        // write individual bytes, then read back the int
        da.setByte(16, (byte) 0xAA);
        da.setByte(17, (byte) 0xBB);
        da.setByte(18, (byte) 0xCC);
        da.setByte(19, (byte) 0xDD);
        assertEquals(0xDDCCBBAA, da.getInt(16));
        da.close();
    }

    @Test
    public void testFiveByteIntegerPattern() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        // simulate the geo_ref pattern: setInt for low 4 bytes, setByte for 5th byte
        long geoRef = 0x1F_ABCD_1234L;
        int pos = 20;
        da.setInt(pos, (int) geoRef);
        da.setByte(pos + 4, (byte) (geoRef >> 32));
        // read back
        int low = da.getInt(pos);
        byte high = da.getByte(pos + 4);
        long result = ((long) high << 32) | (low & 0xFFFF_FFFFL);
        assertEquals(geoRef, result);

        // negative geo_ref
        geoRef = -42L;
        da.setInt(pos, (int) geoRef);
        da.setByte(pos + 4, (byte) (geoRef >> 32));
        low = da.getInt(pos);
        high = da.getByte(pos + 4);
        result = ((long) high << 32) | (low & 0xFFFF_FFFFL);
        assertEquals(geoRef, result);
        da.close();
    }
}
