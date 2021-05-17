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

import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public abstract class DataAccessTest {
    private final File folder = new File("./target/tmp/da");
    protected ByteOrder defaultOrder = ByteOrder.LITTLE_ENDIAN;
    protected String directory;
    protected String name = "dataacess";

    public abstract DataAccess createDataAccess(String location);

    @BeforeEach
    public void setUp() {
        if (!Helper.removeDir(folder))
            throw new IllegalStateException("cannot delete folder " + folder);

        folder.mkdirs();
        directory = folder.getAbsolutePath() + "/";
    }

    @AfterEach
    public void tearDown() {
        Helper.removeDir(folder);
    }

    @Test
    public void testLoadFlush() {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        da.create(300);
        da.setInt(7 * 4, 123);
        assertEquals(123, da.getInt(7 * 4));
        da.setInt(10 * 4, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10 * 4));
        da.flush();

        // check noValue clearing
        assertEquals(0, da.getInt(2 * 4));
        assertEquals(0, da.getInt(3 * 4));
        assertEquals(123, da.getInt(7 * 4));
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10 * 4));
        da.close();

        // cannot load data if already closed
        try {
            da.loadExisting();
            assertTrue(false);
        } catch (Exception ex) {
            assertEquals("already closed", ex.getMessage());
        }

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7 * 4));
        da.close();
    }

    @Test
    public void testExceptionIfNoEnsureCapacityWasCalled() {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        // throw some undefined exception if no ensureCapacity was called
        try {
            da.setInt(2 * 4, 321);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testLoadClose() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        da.setInt(2 * 4, 321);
        da.flush();
        da.close();

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(321, da.getInt(2 * 4));
        da.close();
    }

    @Test
    public void testHeader() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        da.setHeader(7 * 4, 123);
        assertEquals(123, da.getHeader(7 * 4));
        da.setHeader(10 * 4, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getHeader(10 * 4));

        da.setHeader(11 * 4, Helper.degreeToInt(123.321));
        assertEquals(123.321, Helper.intToDegree(da.getHeader(11 * 4)), 1e-4);
        da.flush();
        da.close();

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getHeader(7 * 4));
        da.close();
    }

    @Test
    public void testEnsureCapacity() {
        DataAccess da = createDataAccess(name);
        da.create(128);
        da.setInt(31 * 4, 200);

        assertEquals(200, da.getInt(31 * 4));
        da.ensureCapacity(2 * 128);
        assertEquals(200, da.getInt(31 * 4));
        // now it shouldn't fail
        da.setInt(32 * 4, 220);
        assertEquals(220, da.getInt(32 * 4));
        da.close();

        // ensure some bigger area
        da = createDataAccess(name);
        da.create(200 * 4);
        da.ensureCapacity(600 * 4);
        da.close();
    }

    @Test
    public void testCopy() {
        DataAccess da1 = createDataAccess(name);
        da1.create(1001 * 4);
        da1.setHeader(4, 12);
        da1.setInt(1 * 4, 1);
        da1.setInt(123 * 4, 321);
        da1.setInt(1000 * 4, 1111);

        DataAccess da2 = createDataAccess(name + "2");
        da2.create(10);
        da1.copyTo(da2);
        assertEquals(12, da2.getHeader(4));
        assertEquals(1, da2.getInt(1 * 4));
        assertEquals(321, da2.getInt(123 * 4));
        assertEquals(1111, da2.getInt(1000 * 4));

        da2.setInt(1 * 4, 2);
        assertEquals(2, da2.getInt(1 * 4));
        da2.flush();
        da1.flush();
        // make sure they are independent!
        assertEquals(1, da1.getInt(1 * 4));
        da1.close();
        da2.close();
    }

    @Test
    public void testSegments() {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.create(10);
        assertEquals(1, da.getSegments());
        da.ensureCapacity(500);
        int olds = da.getSegments();
        assertTrue(olds > 3);

        da.setInt(400, 321);
        da.flush();
        da.close();

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(olds, da.getSegments());
        assertEquals(321, da.getInt(400));
        da.close();
    }

    @Test
    public void testSegmentSize() {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(20);
        assertEquals(128, da.getSegmentSize());
        da.close();
    }

    @Test
    public void testRenameNoFlush() {
        DataAccess da = createDataAccess(name);
        da.create(100);
        da.setInt(17 * 4, 17);
        try {
            da.rename(name + "wow");
            assertTrue(false);
        } catch (Exception ex) {
        }
        da.close();
    }

    @Test
    public void testRenameFlush() {
        DataAccess da = createDataAccess(name);
        da.create(100);
        da.setInt(17 * 4, 17);
        da.flush();
        assertTrue(new File(directory + name).exists());
        da.rename(name + "wow");
        assertFalse(new File(directory + name).exists());
        assertTrue(new File(directory + name + "wow").exists());
        assertEquals(17, da.getInt(17 * 4));
        da.close();

        da = createDataAccess(name + "wow");
        assertTrue(da.loadExisting());
        assertEquals(17, da.getInt(17 * 4));
        da.close();
    }

    @Test
    public void testSet_GetBytes() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        assertEquals(128, da.getSegmentSize());
        byte[] bytes = BitUtil.BIG.fromInt(Integer.MAX_VALUE / 3);
        da.setBytes(8, bytes, bytes.length);
        bytes = new byte[4];
        da.getBytes(8, bytes, bytes.length);
        assertEquals(Integer.MAX_VALUE / 3, BitUtil.BIG.toInt(bytes));

        da.setBytes(127, bytes, bytes.length);
        da.getBytes(127, bytes, bytes.length);
        assertEquals(Integer.MAX_VALUE / 3, BitUtil.BIG.toInt(bytes));

        da.close();

        long bytePos = 4294967296L + 11111;
        int segmentSizePower = 24;
        int segmentSizeInBytes = 1 << segmentSizePower;
        int indexDivisor = segmentSizeInBytes - 1;
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        assertEquals(256, bufferIndex);
        assertEquals(11111, index);
    }

    @Test
    public void testSet_GetByte() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        da.setByte(8, (byte) 120);
        assertEquals(120, da.getByte(8));
        da.close();
    }

    @Test
    public void testSet_Get_Short_Long() {
        DataAccess da = createDataAccess(name);
        da.create(300);
        da.setShort(6, (short) (Short.MAX_VALUE / 5));
        da.setShort(8, (short) (Short.MAX_VALUE / 7));
        da.setShort(10, (short) (Short.MAX_VALUE / 9));
        da.setShort(14, (short) (Short.MAX_VALUE / 10));
        int unsignedShort = (int) Short.MAX_VALUE + 5;
        da.setShort(12, (short) unsignedShort);

        assertEquals(Short.MAX_VALUE / 5, da.getShort(6));
        assertEquals(Short.MAX_VALUE / 7, da.getShort(8));
        assertEquals(Short.MAX_VALUE / 9, da.getShort(10));
        assertEquals(Short.MAX_VALUE / 10, da.getShort(14));
        assertEquals(unsignedShort, (int) da.getShort(12) & 0x0000FFFF);

        // currently RAMIntDA does not support arbitrary byte positions
        if (!(da instanceof RAMIntDataAccess)) {
            da.setShort(7, (short) (Short.MAX_VALUE / 3));
            assertEquals(Short.MAX_VALUE / 3, da.getShort(7));
            // should be overwritten
            assertNotEquals(Short.MAX_VALUE / 3, da.getShort(8));

            long pointer = da.getSegmentSize() - 1;
            da.setShort(pointer, (short) (Short.MAX_VALUE / 3));
            assertEquals(Short.MAX_VALUE / 3, da.getShort(pointer));
        }
        da.close();
    }
}
