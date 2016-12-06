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

import org.junit.Test;

import java.nio.channels.OverlappingFileLockException;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class NativeFSLockFactoryTest extends AbstractLockFactoryTester {
    @Override
    protected LockFactory createLockFactory() {
        return new NativeFSLockFactory(lockDir);
    }

    @Test
    public void testMultiReadObtain() {
        LockFactory instance = createLockFactory();
        instance.setLockDir(lockDir);
        GHLock writeLock1 = instance.create("test", true);
        assertTrue(writeLock1.tryLock());

        // BUT disallow more than one write lock!
        GHLock lock2 = instance.create("test", false);
        assertFalse(lock2.tryLock());

        writeLock1.release();

        assertTrue(lock2.tryLock());

        // http://stackoverflow.com/q/24367887/194609
        // we cannot test 'allow multiple read locks' as multiple reads are only allowed for different processes        
        // Lock lock3 = instance.create("test", false);
        // assertFalse(lock3.tryLock());
        // lock3.release();
        // still the lock should be valid
        assertTrue(lock2.isLocked());

        // disallow write lock if currently reading
        GHLock writeLock4 = instance.create("test", true);
        assertFalse(writeLock4.tryLock());
        assertEquals(OverlappingFileLockException.class, writeLock4.getObtainFailedReason().getClass());
        writeLock4.release();

        assertTrue(lock2.isLocked());
        lock2.release();
    }
}
