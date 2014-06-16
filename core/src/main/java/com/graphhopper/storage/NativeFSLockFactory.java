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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Creates a write lock file. Influenced by Lucene code
 * <p>
 * @author Peter Karich
 */
public class NativeFSLockFactory implements LockFactory
{
    private File lockDir;

    public NativeFSLockFactory()
    {
    }

    public NativeFSLockFactory( File dir )
    {
        this.lockDir = dir;
    }

    @Override
    public void setLockDir( File lockDir )
    {
        if (!lockDir.isDirectory())
            throw new IllegalArgumentException("lockDir has to be a directory");

        this.lockDir = lockDir;
    }

    @Override
    public Lock create( String fileName )
    {
        if (lockDir == null)
            throw new RuntimeException("Set lockDir before creating locks");

        return new NativeLock(lockDir, fileName);
    }

    @Override
    public void forceRemove( String fileName )
    {
        if (lockDir.exists())
        {
            create(fileName).release();
            File lockFile = new File(lockDir, fileName);
            if (!lockFile.delete())
                throw new RuntimeException("Cannot delete " + lockFile);
        }
    }

    static class NativeLock implements Lock
    {
        private RandomAccessFile tmpRaFile;
        private FileChannel tmpChannel;
        private FileLock tmpLock;

        private final String name;
        private final File lockDir;
        private final File lockFile;
        private Exception failedReason;

        public NativeLock( File lockDir, String fileName )
        {
            this.name = fileName;
            this.lockDir = lockDir;
            this.lockFile = new File(lockDir, fileName);
        }

        @Override
        public synchronized boolean obtain()
        {
            // already locked
            if (lockExists())
                return false;

            // on-the-fly: make sure directory exists
            if (!lockDir.exists())
            {
                if (!lockDir.mkdirs())
                    throw new RuntimeException("Directory " + lockDir + " does not exist and cannot created to place lock file there: " + lockFile);
            }

            try
            {
                tmpRaFile = new RandomAccessFile(lockFile, "rw");
            } catch (IOException ex)
            {
                failedReason = ex;
                return false;
            }

            try
            {
                tmpChannel = tmpRaFile.getChannel();
                try
                {
                    tmpLock = tmpChannel.tryLock();
                    // OverlappingFileLockException is not IOException!
                } catch (Exception ex)
                {
                    failedReason = ex;
                } finally
                {
                    if (tmpLock == null)
                    {
                        Helper.close(tmpChannel);
                        tmpChannel = null;
                    }
                }
            } finally
            {
                if (tmpChannel == null)
                {
                    Helper.close(tmpRaFile);
                    tmpRaFile = null;
                }
            }
            return lockExists();
        }

        private synchronized boolean lockExists()
        {
            return tmpLock != null;
        }

        @Override
        public synchronized boolean isLocked()
        {
            if (!lockFile.exists())
                return false;

            if (lockExists())
                return true;

            try
            {
                boolean obtained = obtain();
                if (!obtained)
                    release();
                return !obtained;
            } catch (Exception ex)
            {
                return false;
            }
        }

        @Override
        public synchronized void release()
        {
            if (lockExists())
            {
                try
                {
                    tmpLock.release();
                } catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                } finally
                {
                    tmpLock = null;
                    try
                    {
                        tmpChannel.close();
                    } catch (Exception ex)
                    {
                        throw new RuntimeException(ex);
                    } finally
                    {
                        tmpChannel = null;
                        try
                        {
                            tmpRaFile.close();
                        } catch (Exception ex)
                        {
                            throw new RuntimeException(ex);
                        } finally
                        {
                            tmpRaFile = null;
                        }
                    }
                }
                lockFile.delete();
            }
        }

        @Override
        public String getName()
        {
            return name;
        }

        public Exception getFailedReason()
        {
            return failedReason;
        }

        @Override
        public String toString()
        {
            return lockFile.toString();
        }
    }
}
