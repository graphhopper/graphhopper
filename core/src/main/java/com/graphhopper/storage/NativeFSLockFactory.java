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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Creates a write lock file. Influenced by Lucene code
 * <p>
 *
 * @author Peter Karich
 */
public class NativeFSLockFactory implements LockFactory {
    private File lockDir;

    public NativeFSLockFactory() {
    }

    public NativeFSLockFactory(File dir) {
        this.lockDir = dir;
    }

    public static void main(String[] args) throws IOException {
        // trying FileLock mechanics in different processes
        File file = new File("tmp.lock");

        file.createNewFile();
        FileChannel channel = new RandomAccessFile(file, "r").getChannel();

        boolean shared = true;
        FileLock lock1 = channel.tryLock(0, Long.MAX_VALUE, shared);

        System.out.println("locked " + lock1);
        System.in.read();

        System.out.println("release " + lock1);
        lock1.release();
    }

    @Override
    public void setLockDir(File lockDir) {
        this.lockDir = lockDir;
    }

    @Override
    public synchronized GHLock create(String fileName, boolean writeAccess) {
        if (lockDir == null)
            throw new RuntimeException("Set lockDir before creating " + (writeAccess ? "write" : "read") + " locks");

        return new NativeLock(lockDir, fileName, writeAccess);
    }

    @Override
    public synchronized void forceRemove(String fileName, boolean writeAccess) {
        if (lockDir.exists()) {
            create(fileName, writeAccess).release();
            File lockFile = new File(lockDir, fileName);
            if (lockFile.exists() && !lockFile.delete())
                throw new RuntimeException("Cannot delete " + lockFile);
        }
    }

    static class NativeLock implements GHLock {
        private final String name;
        private final File lockDir;
        private final File lockFile;
        private final boolean writeLock;
        private RandomAccessFile tmpRaFile;
        private FileChannel tmpChannel;
        private FileLock tmpLock;
        private Exception failedReason;

        public NativeLock(File lockDir, String fileName, boolean writeLock) {
            this.name = fileName;
            this.lockDir = lockDir;
            this.lockFile = new File(lockDir, fileName);
            this.writeLock = writeLock;
        }

        @Override
        public synchronized boolean tryLock() {
            // already locked
            if (lockExists())
                return false;

            // on-the-fly: make sure directory exists
            if (!lockDir.exists()) {
                if (!lockDir.mkdirs())
                    throw new RuntimeException("Directory " + lockDir + " does not exist and cannot be created to place lock file there: " + lockFile);
            }

            if (!lockDir.isDirectory())
                throw new IllegalArgumentException("lockDir has to be a directory: " + lockDir);

            try {
                failedReason = null;
                // we need write access even for read locks - in order to create the lock file!
                tmpRaFile = new RandomAccessFile(lockFile, "rw");
            } catch (IOException ex) {
                failedReason = ex;
                return false;
            }

            try {
                tmpChannel = tmpRaFile.getChannel();
                try {
                    tmpLock = tmpChannel.tryLock(0, Long.MAX_VALUE, !writeLock);
                    // OverlappingFileLockException is not an IOException!
                } catch (Exception ex) {
                    failedReason = ex;
                } finally {
                    if (tmpLock == null) {
                        Helper.close(tmpChannel);
                        tmpChannel = null;
                    }
                }
            } finally {
                if (tmpChannel == null) {
                    Helper.close(tmpRaFile);
                    tmpRaFile = null;
                }
            }
            return lockExists();
        }

        private synchronized boolean lockExists() {
            return tmpLock != null;
        }

        @Override
        public synchronized boolean isLocked() {
            if (!lockFile.exists())
                return false;

            if (lockExists())
                return true;

            try {
                boolean obtained = tryLock();
                if (obtained)
                    release();
                return !obtained;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public synchronized void release() {
            if (lockExists()) {
                try {
                    failedReason = null;
                    tmpLock.release();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    tmpLock = null;
                    try {
                        tmpChannel.close();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    } finally {
                        tmpChannel = null;
                        try {
                            tmpRaFile.close();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            tmpRaFile = null;
                        }
                    }
                }
                lockFile.delete();
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Exception getObtainFailedReason() {
            return failedReason;
        }

        @Override
        public String toString() {
            return lockFile.toString();
        }
    }
}
