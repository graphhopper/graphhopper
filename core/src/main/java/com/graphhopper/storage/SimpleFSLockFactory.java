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

import java.io.File;
import java.io.IOException;

/**
 * Creates a write lock file. Influenced by Lucene code
 * <p>
 *
 * @author Peter Karich
 */
public class SimpleFSLockFactory implements LockFactory {
    private File lockDir;

    public SimpleFSLockFactory() {
    }

    public SimpleFSLockFactory(File dir) {
        this.lockDir = dir;
    }

    @Override
    public void setLockDir(File lockDir) {
        this.lockDir = lockDir;
    }

    @Override
    public synchronized GHLock create(String fileName, boolean writeAccess) {
        // TODO no read access-only support
        if (lockDir == null)
            throw new RuntimeException("Set lockDir before creating locks");

        return new SimpleLock(lockDir, fileName);
    }

    @Override
    public synchronized void forceRemove(String fileName, boolean writeAccess) {
        if (lockDir.exists()) {
            File lockFile = new File(lockDir, fileName);
            if (lockFile.exists() && !lockFile.delete())
                throw new RuntimeException("Cannot delete " + lockFile);
        }
    }

    static class SimpleLock implements GHLock {
        private final File lockDir;
        private final File lockFile;
        private final String name;
        private IOException failedReason;

        public SimpleLock(File lockDir, String fileName) {
            this.name = fileName;
            this.lockDir = lockDir;
            this.lockFile = new File(lockDir, fileName);
        }

        @Override
        public synchronized boolean tryLock() {
            // make sure directory exists, do it on-the-fly (not possible when setLockDir is called)
            if (!lockDir.exists()) {
                if (!lockDir.mkdirs())
                    throw new RuntimeException("Directory " + lockDir + " does not exist and cannot created to place lock file there: " + lockFile);
            }

            // this test can only be performed after the dir has created!
            if (!lockDir.isDirectory())
                throw new IllegalArgumentException("lockDir has to be a directory: " + lockDir);

            try {
                return lockFile.createNewFile();
            } catch (IOException ex) {
                failedReason = ex;
                return false;
            }
        }

        @Override
        public synchronized boolean isLocked() {
            return lockFile.exists();
        }

        @Override
        public synchronized void release() {
            if (isLocked() && lockFile.exists() && !lockFile.delete())
                throw new RuntimeException("Cannot release lock file: " + lockFile);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public synchronized Exception getObtainFailedReason() {
            return failedReason;
        }

        @Override
        public String toString() {
            return lockFile.toString();
        }
    }
}
