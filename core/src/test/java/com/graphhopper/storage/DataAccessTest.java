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
package com.graphhopper.storage;

import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import java.io.File;
import java.nio.ByteOrder;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public abstract class DataAccessTest
{
    protected ByteOrder defaultOrder = ByteOrder.LITTLE_ENDIAN;
    private final File folder = new File("./target/tmp/da");
    protected String directory;
    protected String name = "dataacess";

    public abstract DataAccess createDataAccess( String location );

    @Before
    public void setUp()
    {
        if (!Helper.removeDir(folder))
            throw new IllegalStateException("cannot delete folder " + folder);

        folder.mkdirs();
        directory = folder.getAbsolutePath() + "/";
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(folder);
    }

    @Test
    public void testLoadFlush()
    {
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
        assertFalse(da.loadExisting());

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7 * 4));
        da.close();
    }

    @Test
    public void testExceptionIfNoEnsureCapacityWasCalled()
    {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        // throw some undefined exception if no incCapacity was called
        try
        {
            da.setInt(2 * 4, 321);
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }

    @Test
    public void testLoadClose()
    {
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
    public void testHeader()
    {
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
    public void testEnsureCapacity()
    {
        DataAccess da = createDataAccess(name);
        da.create(128);
        da.setInt(31 * 4, 200);

        assertEquals(200, da.getInt(31 * 4));
        da.incCapacity(2 * 128);
        assertEquals(200, da.getInt(31 * 4));
        // now it shouldn't fail
        da.setInt(32 * 4, 220);
        assertEquals(220, da.getInt(32 * 4));
        da.close();

        // ensure some bigger area
        da = createDataAccess(name);
        da.create(200 * 4);
        da.incCapacity(600 * 4);
        da.close();
    }

    @Test
    public void testCopy()
    {
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
    public void testSegments()
    {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.create(10);
        assertEquals(1, da.getSegments());
        da.incCapacity(500);
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
    public void testTrimTo()
    {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.create(128 * 11);
        da.setInt(1 * 4, 10);
        da.setInt(27 * 4, 200);
        da.setInt(31 * 4, 301);
        da.setInt(32 * 4, 302);
        da.setInt(337 * 4, 4000);

        // now 11 segments: (337 + 1) * 4 = 1352
        assertEquals(11, da.getSegments());
        assertEquals(11 * 128, da.getCapacity());

        // now 3 segments
        da.trimTo(128 * 2 + 1);
        assertEquals(3, da.getSegments());

        // now 2 segments
        da.trimTo(128 * 2);
        assertEquals(2, da.getSegments());
        assertEquals(301, da.getInt(31 * 4));
        assertEquals(302, da.getInt(32 * 4));

        // now only one segment
        da.trimTo(128 * 1);
        assertEquals(1, da.getSegments());
        assertEquals(301, da.getInt(31 * 4));

        // at least one segment
        da.trimTo(0);
        assertEquals(1, da.getSegments());
        da.close();
    }

    @Test
    public void testBoundsCheck()
    {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.create(128 * 11);
        da.setInt(32 * 4, 302);

        // make smaller
        da.trimTo(128 * 1);
        try
        {
            assertEquals(302, da.getInt(32 * 4));
            assertTrue(false);
        } catch (Exception ex)
        {
        }
        da.close();

        da = createDataAccess(name);
        da.create(128);
        da.setInt(31 * 4, 200);
        try
        {
            // this should fail with an index out of bounds exception
            da.setInt(32 * 4, 220);
            assertFalse(true);
        } catch (Exception ex)
        {
        }
        da.close();
    }

    @Test
    public void testSegmentSize()
    {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(20);
        assertEquals(128, da.getSegmentSize());
        da.close();
    }

    @Test
    public void testRenameNoFlush()
    {
        DataAccess da = createDataAccess(name);
        da.create(100);
        da.setInt(17 * 4, 17);
        try
        {
            da.rename(name + "wow");
            assertTrue(false);
        } catch (Exception ex)
        {
        }
        da.close();
    }

    @Test
    public void testRenameFlush()
    {
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
    public void testSet_GetBytes()
    {
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
}
