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

import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public abstract class AbstractLockFactoryTester {
    protected final File lockDir = new File("./target/lockingtest/");

    protected abstract LockFactory createLockFactory();

    @BeforeEach
    public void setUp() {
        lockDir.mkdirs();
    }

    @AfterEach
    public void tearDown() {
        Helper.removeDir(lockDir);
    }

    @Test
    public void testObtain() {
        LockFactory instance = createLockFactory();
        instance.setLockDir(lockDir);
        GHLock lock = instance.create("test", true);
        assertTrue(lock.tryLock());
        assertTrue(lock.isLocked());
        assertFalse(lock.tryLock());
        assertTrue(lock.isLocked());

        GHLock lock2 = instance.create("test", true);
        assertFalse(lock2.tryLock());
        assertTrue(lock2.isLocked());

        // fails for SimpleFSLockFactory:
        // although it is locked do not allow release:
        // lock2.release();
        // assertTrue(lock.isLocked());
        lock.release();
        assertFalse(lock.isLocked());
    }

    @Test
    public void testForceDelete() {
        LockFactory instance = createLockFactory();
        instance.setLockDir(lockDir);
        GHLock lock = instance.create("testlock", true);
        assertTrue(lock.tryLock());
        assertTrue(lock.isLocked());

        // on windows we cannot forcefully remove an unreleased lock
        if (Constants.WINDOWS)
            lock.release();

        instance.forceRemove(lock.getName(), true);
        assertFalse(lock.isLocked());
    }
}
