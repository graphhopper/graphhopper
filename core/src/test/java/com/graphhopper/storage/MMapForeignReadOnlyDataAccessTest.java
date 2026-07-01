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

import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the read-only fixed-size {@link MMapForeignReadOnlyDataAccess}. Does not extend
 * {@link DataAccessTest} because this class intentionally only fulfills the read half of the
 * {@link DataAccess} contract — write operations are expected to throw.
 */
public class MMapForeignReadOnlyDataAccessTest {
    private static final int SEGMENT_SIZE = 128;
    private final File folder = new File("./target/tmp/da-ro");
    private String directory;
    private final String name = "ro-test";

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

    /** Builds a small file with the resizable writer, flushes, and closes it. */
    private void writeFixture() {
        DataAccess da = new MMapForeignMemoryDataAccess(name, directory, true, SEGMENT_SIZE);
        da.create(SEGMENT_SIZE * 4L);
        da.setInt(0, 1);
        da.setInt(4, 2);
        da.setInt(7 * 4, 123);
        da.setInt(10 * 4, Integer.MAX_VALUE / 3);
        da.setShort(60, (short) -4321);
        da.setByte(70, (byte) 0x7F);
        byte[] payload = {1, 2, 3, 4, 5};
        da.setBytes(80, payload, payload.length);
        da.setHeader(0, 0xCAFEBABE);
        da.flush();
        da.close();
    }

    @Test
    public void readsValuesWrittenByResizableWriter() {
        writeFixture();
        MMapForeignReadOnlyDataAccess da = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        try {
            assertTrue(da.loadExisting()); // no-op, but contract returns true
            assertEquals(1, da.getInt(0));
            assertEquals(2, da.getInt(4));
            assertEquals(123, da.getInt(7 * 4));
            assertEquals(Integer.MAX_VALUE / 3, da.getInt(10 * 4));
            assertEquals((short) -4321, da.getShort(60));
            assertEquals((byte) 0x7F, da.getByte(70));
            byte[] out = new byte[5];
            da.getBytes(80, out, out.length);
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, out);
            assertEquals(0xCAFEBABE, da.getHeader(0));
            assertEquals(DAType.MMAP_RO, da.getType());
            assertTrue(da.getCapacity() >= SEGMENT_SIZE * 4L);
            assertTrue(da.getSegments() >= 4);
        } finally {
            da.close();
        }
    }

    @Test
    public void preloadDoesNotChangeReadResults() {
        writeFixture();
        MMapForeignReadOnlyDataAccess da = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, true);
        try {
            assertEquals(123, da.getInt(7 * 4));
        } finally {
            da.close();
        }
    }

    @Test
    public void writeOperationsThrow() {
        writeFixture();
        MMapForeignReadOnlyDataAccess da = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        try {
            assertThrows(UnsupportedOperationException.class, () -> da.create(64));
            assertThrows(UnsupportedOperationException.class, () -> da.setInt(0, 99));
            assertThrows(UnsupportedOperationException.class, () -> da.setShort(0, (short) 99));
            assertThrows(UnsupportedOperationException.class, () -> da.setByte(0, (byte) 99));
            assertThrows(UnsupportedOperationException.class, () -> da.setBytes(0, new byte[]{1}, 1));
            assertThrows(UnsupportedOperationException.class, da::flush);
            assertThrows(UnsupportedOperationException.class, () -> da.trimTo(0));
            // grow past current capacity throws
            assertThrows(UnsupportedOperationException.class,
                    () -> da.ensureCapacity(da.getCapacity() + 1));
            // ensureCapacity within current capacity returns false (no-op)
            assertFalse(da.ensureCapacity(da.getCapacity()));
            assertFalse(da.ensureCapacity(0));
        } finally {
            da.close();
        }
    }

    @Test
    public void loadOnMissingFileFails() {
        assertThrows(IllegalStateException.class,
                () -> MMapForeignReadOnlyDataAccess.load("does-not-exist", directory, SEGMENT_SIZE, false));
    }

    @Test
    public void loadOnEmptyFileFails() throws Exception {
        File empty = new File(directory + name);
        assertTrue(empty.createNewFile());
        assertThrows(IllegalStateException.class,
                () -> MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false));
    }

    @Test
    public void closeIsIdempotent() {
        writeFixture();
        MMapForeignReadOnlyDataAccess da = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        da.close();
        da.close(); // must not throw
        assertTrue(da.isClosed());
    }

    @Test
    public void loadExistingAfterCloseThrows() {
        writeFixture();
        MMapForeignReadOnlyDataAccess da = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        da.close();
        assertThrows(IllegalStateException.class, da::loadExisting);
    }

    @Test
    public void multipleReadersOnSameFile() {
        writeFixture();
        MMapForeignReadOnlyDataAccess a = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        MMapForeignReadOnlyDataAccess b = MMapForeignReadOnlyDataAccess.load(name, directory, SEGMENT_SIZE, false);
        try {
            assertEquals(123, a.getInt(7 * 4));
            assertEquals(123, b.getInt(7 * 4));
        } finally {
            a.close();
            b.close();
        }
    }
}
