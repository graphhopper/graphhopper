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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// todonow: parameterize for little/big endian, but do we ever need this?

/**
 * Test interface for {@link NewDataAccess}
 */
interface NewDataAccessTest {

    String getDefaultPath();

    NewDataAccess create(String path, int bytesPerSegment);

    NewDataAccess load(String path);

    default NewDataAccess create(int bytesPerSegment) {
        return create(getDefaultPath(), bytesPerSegment);
    }

    @Test
    default void illegalBytesPerSegment() {
        assertIllegalArgument(() -> create(-1), "bytesPerSegment must be >= 2");
        assertIllegalArgument(() -> create(0), "bytesPerSegment must be >= 2");
        assertIllegalArgument(() -> create(1), "bytesPerSegment must be >= 2");
        assertDoesNotThrow(() -> create(2));
        assertIllegalArgument(() -> create(3), "bytesPerSegment must be a power of two, but got: 3");
        assertDoesNotThrow(() -> create(4));
        assertIllegalArgument(() -> create(15), "bytesPerSegment must be a power of two, but got: 15");
        assertDoesNotThrow(() -> create(16));
        assertDoesNotThrow(() -> create(1 << 6));
        assertIllegalArgument(() -> create(Integer.MAX_VALUE), "bytesPerSegment must be a power of two, but got: 2147483647");
    }

    @Test
    default void outOfRange() {
        NewDataAccess da = create(8);
        assertThrows(IndexOutOfBoundsException.class, () -> da.setInt(0, 19));
        da.ensureCapacity(3);
        da.setInt(0, 19);
        assertThrows(IndexOutOfBoundsException.class, () -> da.setInt(6, 21));
    }

    @Test
    default void readWriteInts() {
        NewDataAccess da = create(4);
        da.ensureCapacity(4);
        da.setInt(0, 19);
        assertEquals(19, da.getInt(0));
        da.ensureCapacity(8);
        da.setInt(4, 12);
        assertEquals(12, da.getInt(4));
    }

    @Test
    default void readWriteInts_acrossSegments() {
        NewDataAccess da = create(2);
        da.ensureCapacity(4);
        da.setInt(0, 1 << 25);
        assertEquals(33554432, da.getInt(0));

        da = create(4);
        da.ensureCapacity(5);
        da.setInt(1, 658948);
        assertEquals(658948, da.getInt(1));
    }

    @Test
    default void flushAndLoad(@TempDir Path path) {
        String pathStr = path.resolve("da_test_file").toAbsolutePath().toString();
        NewDataAccess da = create(pathStr, 16);
        da.ensureCapacity(40);
        for (int i = 0; i < 10; i++)
            da.setInt(4 * i, i * 10_000_000);
        da.flush();

        da = load(pathStr);
        assertNotNull(da);
        for (int i = 0; i < 10; i++)
            assertEquals(i * 10_000_000, da.getInt(4 * i));
    }

    default void assertIllegalArgument(Executable executable, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, executable);
        assertEquals(message, e.getMessage());
    }
}