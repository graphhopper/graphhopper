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

import com.graphhopper.util.Helper;
import java.io.File;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class SimpleFSLockFactoryTest
{
    private final File lockDir = new File("./target/lockingtest/");

    @Before
    public void setUp()
    {
        lockDir.mkdirs();
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(lockDir);
    }

    @Test
    public void testObtain()
    {
        LockFactory instance = new SimpleFSLockFactory();
        instance.setLockDir(lockDir);
        Lock lock = instance.create("test");
        assertTrue(lock.obtain());
        assertTrue(lock.isLocked());
        assertFalse(lock.obtain());
        assertTrue(lock.isLocked());
        lock.release();
        assertFalse(lock.isLocked());
    }

    @Test
    public void testForceDelete()
    {
        LockFactory instance = new SimpleFSLockFactory();
        instance.setLockDir(lockDir);
        Lock lock = instance.create("testlock");
        assertTrue(lock.obtain());
        assertTrue(lock.isLocked());
        instance.forceRemove(lock.getName());
        assertFalse(lock.isLocked());
    }
}
