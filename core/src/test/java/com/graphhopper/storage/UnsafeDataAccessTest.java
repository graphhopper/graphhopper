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
import org.junit.Test;

import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class UnsafeDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name) {
        return new UnsafeDataAccess(name, directory, defaultOrder).setSegmentSize(128);
    }

    @Override
    public void testExceptionIfNoEnsureCapacityWasCalled() {
        // SKIP as unsafe failes with SIGSEGV and not with an exception!
    }

    @Override
    public void testBoundsCheck() {
        // SKIP as unsafe has no bounds checks
    }

    @Test
    public void testNativeOrder() {
        BitUtil bitUtil = BitUtil.get(ByteOrder.nativeOrder());
        long address = UnsafeDataAccess.UNSAFE.allocateMemory(8);
        long val = 123123123123L * 123L;

        byte[] bytes = new byte[8];
        bitUtil.fromLong(bytes, val);

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            for (int i = 7; i >= 0; i--) {
                UnsafeDataAccess.UNSAFE.putByte(address + i, bytes[i]);
            }
        } else {
            // not tested:
            for (int i = 0; i < 8; i++) {
                UnsafeDataAccess.UNSAFE.putByte(address + i, bytes[i]);
            }
        }

        long tmp = UnsafeDataAccess.UNSAFE.getLong(address);
        assertEquals(val, tmp);
        UnsafeDataAccess.UNSAFE.freeMemory(address);
    }
}
