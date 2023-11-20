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

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class MMapDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name, int segmentSize) {
        return new MMapDataAccess(name, directory, true, segmentSize);
    }

    @Test
    public void textMixRAM2MMAP() {
        DataAccess da = new RAMDataAccess(name, directory, true, -1);
        assertFalse(da.loadExisting());
        da.create(100);
        da.setInt(7 * 4, 123);
        da.flush();
        da.close();
        da = createDataAccess(name);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7 * 4));
        da.close();
    }

    @Test
    public void textMixMMAP2RAM() {
        DataAccess da = createDataAccess(name);
        assertFalse(da.loadExisting());
        da.create(100);
        da.setInt(7 * 4, 123);

        // TODO "memory mapped flush" is expensive and not required. only writing the header is required.
        da.flush();
        da.close();
        da = new RAMDataAccess(name, directory, true, -1);
        assertTrue(da.loadExisting());
        assertEquals(123, da.getInt(7 * 4));
        da.close();
    }

    @Test
    public void testByteBufferCleaner() {
        MethodHandle bufferCleaner = MMapDataAccess.getByteBufferCleaner();
        assertNotNull(bufferCleaner);
        try {
            bufferCleaner.invokeExact(ByteBuffer.allocate(10));
            fail("Cleaner invocation should fail for a non-direct buffer");
        } catch (Throwable t) {
            assertInstanceOf(IllegalArgumentException.class, t);
            assertEquals("buffer is non-direct", t.getMessage());
        }
    }
}
