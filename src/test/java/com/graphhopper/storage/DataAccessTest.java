/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.util.Helper;
import java.io.File;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public abstract class DataAccessTest {

    private File folder = new File("./target/tmp/");
    protected String directory;
    protected String name = "dataacess";

    public abstract DataAccess createDataAccess(String location);

    @Before
    public void setUp() {
        Helper.deleteDir(folder);
        folder.mkdirs();
        directory = folder.getAbsolutePath() + "/";
    }

    @After
    public void tearDown() {
        Helper.deleteDir(folder);
    }

    @Test
    public void testLoadFlush() {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        da.createNew(300);
        da.setInt(7, 123);
        assertEquals(123, da.getInt(7));
        da.setInt(10, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10));
        da.flush();

        // check noValue clearing
        assertEquals(0, da.getInt(2));
        assertEquals(0, da.getInt(3));
        assertEquals(123, da.getInt(7));
        assertEquals(Integer.MAX_VALUE / 3, da.getInt(10));
        da.close();

        // cannot load data if already closed
        assertFalse(da.loadExisting());

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7));
        da.close();
    }

    @Test
    public void testLoadClose() {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        // throw some undefined exception if no ensureCapacity was called
        try {
            da.setInt(2, 321);
        } catch (Exception ex) {
        }

        da.createNew(300);
        da.setInt(2, 321);
        da.flush();
        da.close();
        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(321, da.getInt(2));
        da.close();
    }

    @Test
    public void testHeader() {
        DataAccess da = createDataAccess(name);
        da.createNew(300);
        da.setHeader(7, 123);
        assertEquals(123, da.getHeader(7));
        da.setHeader(10, Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, da.getHeader(10));
        da.flush();

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getHeader(7));
        da.close();
    }

    @Test
    public void testEnsureCapacity() {
        DataAccess da = createDataAccess(name);
        da.createNew(128);
        da.setInt(31, 200);
        try {
            // this should fail with an index out of bounds exception
            da.setInt(32, 220);
            assertFalse(true);
        } catch (Exception ex) {
        }
        assertEquals(200, da.getInt(31));
        da.ensureCapacity(2 * 128);
        assertEquals(200, da.getInt(31));
        // now it shouldn't fail now
        da.setInt(32, 220);
        assertEquals(220, da.getInt(32));

        // ensure some bigger area
        da = createDataAccess(name);
        da.createNew(200 * 4);
        da.ensureCapacity(600 * 4);
    }

    @Test
    public void testCopy() {
        DataAccess da1 = createDataAccess(name);
        da1.createNew(1001 * 4);
        da1.setInt(1, 1);
        da1.setInt(123, 321);
        da1.setInt(1000, 1111);

        DataAccess da2 = createDataAccess(name + "2");
        da2.createNew(10);
        da1.copyTo(da2);
        assertEquals(1, da2.getInt(1));
        assertEquals(321, da2.getInt(123));
        assertEquals(1111, da2.getInt(1000));

        da2.setInt(1, 2);
        assertEquals(2, da2.getInt(1));
        da2.flush();
        da1.flush();
        // make sure they are independent!
        assertEquals(1, da1.getInt(1));
    }

    @Test
    public void testSegments() {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.createNew(10);
        assertEquals(1, da.getSegments());
        da.ensureCapacity(500);
        int olds = da.getSegments();
        assertTrue(olds > 3);

        da.setInt(400 / 4, 321);
        da.flush();
        da.close();

        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(olds, da.getSegments());
        assertEquals(321, da.getInt(400 / 4));
    }

    @Test
    public void testTrimTo() {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(128);
        da.createNew(128 * 11);
        da.setInt(1, 10);
        da.setInt(27, 200);
        da.setInt(31, 301);
        da.setInt(32, 302);
        da.setInt(337, 4000);

        // now 11 segments: (337 + 1) * 4 = 1352
        assertEquals(11, da.getSegments());
        assertEquals(11 * 128, da.capacity());

        // now 3 segments
        da.trimTo(128 * 2 + 1);
        assertEquals(3, da.getSegments());

        // now 2 segments
        da.trimTo(128 * 2);
        assertEquals(2, da.getSegments());
        assertEquals(301, da.getInt(31));
        assertEquals(302, da.getInt(32));

        // now only one segment
        da.trimTo(128 * 1);
        assertEquals(1, da.getSegments());
        assertEquals(301, da.getInt(31));
        try {
            assertEquals(302, da.getInt(32));
            assertTrue(false);
        } catch (Exception ex) {
        }

        // at least one segment
        da.trimTo(0);
        assertEquals(1, da.getSegments());
    }

    @Test
    public void testSegmentSize() {
        DataAccess da = createDataAccess(name);
        da.setSegmentSize(20);
        assertEquals(128, da.getSegmentSize());
    }

    @Test
    public void testRenameNoFlush() {
        DataAccess da = createDataAccess(name);
        da.createNew(100);
        da.setInt(17, 17);
        try {
            da.rename(name + "wow");
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testRenameFlush() {
        DataAccess da = createDataAccess(name);
        da.createNew(100);
        da.setInt(17, 17);
        da.flush();
        assertTrue(new File(directory + name).exists());
        da.rename(name + "wow");
        assertFalse(new File(directory + name).exists());
        assertTrue(new File(directory + name + "wow").exists());
        assertEquals(17, da.getInt(17));

        da = createDataAccess(name + "wow");
        assertTrue(da.loadExisting());
        assertEquals(17, da.getInt(17));
    }
}
