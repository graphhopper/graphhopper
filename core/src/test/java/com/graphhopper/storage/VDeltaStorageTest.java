/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class VDeltaStorageTest
{

    @Test
    public void testWriteDouble()
    {
        // under the hood we will write as vlong but store into a temporary lastValue for delta encoding
        VDeltaStorage storage = new VDeltaStorage();
        storage.writeLong(4);
        storage.writeDouble(10.66768);
        storage.writeDouble(10.36768);
        storage.writeDouble(-10.64768);
        storage.writeDouble(20.66768);

        storage.reset();

        assertEquals(4L, storage.readLong());
        assertEquals(10.66768, storage.readDouble(), 1e-6);
        assertEquals(10.36768, storage.readDouble(), 1e-6);
        assertEquals(-10.64768, storage.readDouble(), 1e-6);
        assertEquals(20.66768, storage.readDouble(), 1e-6);
    }

    @Test
    public void testTrim()
    {
        VDeltaStorage storage = new VDeltaStorage();
        storage.writeLong(-4);
        storage.writeDouble(-10.66768);
        storage.writeDouble(-10.36768);

        assertEquals(10, storage.getBytes().length);
        storage.trimToSize();
        assertEquals(8, storage.getBytes().length);

        // allow write even after trim
        storage.writeDouble(10.35768);
        storage.writeDouble(-179.35768);
        storage.writeDouble(179.35768);
        assertEquals(27, storage.getBytes().length);
        storage.trimToSize();
        assertEquals(20, storage.getBytes().length);

        storage.reset();
        assertEquals(-4, storage.readLong());
        assertEquals(-10.66768, storage.readDouble(), 1e-6);
        assertEquals(-10.36768, storage.readDouble(), 1e-6);
        assertEquals(10.35768, storage.readDouble(), 1e-6);
        assertEquals(-179.35768, storage.readDouble(), 1e-6);
        assertEquals(179.35768, storage.readDouble(), 1e-6);
    }
    
    @Test
    public void testMaxValue()
    {
        // move max protection into writeLong?
        VDeltaStorage storage = new VDeltaStorage();
        storage.writeDouble(Integer.MAX_VALUE + 1L);
        
        storage.reset();
        assertEquals(Double.MAX_VALUE, storage.readDouble(), 1e-6);
    }
}
