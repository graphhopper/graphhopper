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
package com.graphhopper.storage

import com.graphhopper.util.Helper
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Creates a write lock file. Influenced by Lucene code
 *
 * @author Peter Karich
 */
class NativeFSLockFactory @JvmOverloads constructor(private var lockDir: File? = null) : LockFactory {

    override fun setLockDir(lockDir: File) {
        this.lockDir = lockDir
    }

    @Synchronized
    override fun create(fileName: String, writeAccess: Boolean): GHLock {
        val dir = lockDir
            ?: throw RuntimeException("Set lockDir before creating " + (if (writeAccess) "write" else "read") + " locks")

        return NativeLock(dir, fileName, writeAccess)
    }

    @Synchronized
    override fun forceRemove(fileName: String, writeAccess: Boolean) {
        if (lockDir!!.exists()) {
            create(fileName, writeAccess).release()
            val lockFile = File(lockDir, fileName)
            if (lockFile.exists() && !lockFile.delete())
                throw RuntimeException("Cannot delete $lockFile")
        }
    }

    internal class NativeLock(private val lockDir: File, fileName: String, private val writeLock: Boolean) : GHLock {
        private val name = fileName
        private val lockFile = File(lockDir, fileName)
        private var tmpRaFile: RandomAccessFile? = null
        private var tmpChannel: FileChannel? = null
        private var tmpLock: FileLock? = null
        private var failedReason: Exception? = null

        @Synchronized
        override fun tryLock(): Boolean {
            // already locked
            if (lockExists())
                return false

            // on-the-fly: make sure directory exists
            if (!lockDir.exists()) {
                if (!lockDir.mkdirs())
                    throw RuntimeException("Directory $lockDir does not exist and cannot be created to place lock file there: $lockFile")
            }

            if (!lockDir.isDirectory)
                throw IllegalArgumentException("lockDir has to be a directory: $lockDir")

            try {
                failedReason = null
                // we need write access even for read locks - in order to create the lock file!
                tmpRaFile = RandomAccessFile(lockFile, "rw")
            } catch (ex: IOException) {
                failedReason = ex
                return false
            }

            try {
                tmpChannel = tmpRaFile!!.channel
                try {
                    tmpLock = tmpChannel!!.tryLock(0, Long.MAX_VALUE, !writeLock)
                    // OverlappingFileLockException is not an IOException!
                } catch (ex: Exception) {
                    failedReason = ex
                } finally {
                    if (tmpLock == null) {
                        Helper.close(tmpChannel)
                        tmpChannel = null
                    }
                }
            } finally {
                if (tmpChannel == null) {
                    Helper.close(tmpRaFile)
                    tmpRaFile = null
                }
            }
            return lockExists()
        }

        @Synchronized
        private fun lockExists(): Boolean = tmpLock != null

        @Synchronized
        override fun isLocked(): Boolean {
            if (!lockFile.exists())
                return false

            if (lockExists())
                return true

            return try {
                val obtained = tryLock()
                if (obtained)
                    release()
                !obtained
            } catch (ex: Exception) {
                false
            }
        }

        @Synchronized
        override fun release() {
            if (lockExists()) {
                try {
                    failedReason = null
                    tmpLock!!.release()
                } catch (ex: Exception) {
                    throw RuntimeException(ex)
                } finally {
                    tmpLock = null
                    try {
                        tmpChannel!!.close()
                    } catch (ex: Exception) {
                        throw RuntimeException(ex)
                    } finally {
                        tmpChannel = null
                        try {
                            tmpRaFile!!.close()
                        } catch (ex: Exception) {
                            throw RuntimeException(ex)
                        } finally {
                            tmpRaFile = null
                        }
                    }
                }
                lockFile.delete()
            }
        }

        override fun getName(): String = name

        override fun getObtainFailedReason(): Exception? = failedReason

        override fun toString(): String = lockFile.toString()
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            // trying FileLock mechanics in different processes
            val file = File("tmp.lock")

            file.createNewFile()
            val channel = RandomAccessFile(file, "r").channel

            val shared = true
            val lock1 = channel.tryLock(0, Long.MAX_VALUE, shared)

            println("locked $lock1")
            System.`in`.read()

            println("release $lock1")
            lock1.release()
        }
    }
}
