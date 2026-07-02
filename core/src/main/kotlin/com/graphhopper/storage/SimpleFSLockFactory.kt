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

import java.io.File
import java.io.IOException

/**
 * Creates a write lock file. Influenced by Lucene code
 *
 * @author Peter Karich
 */
class SimpleFSLockFactory @JvmOverloads constructor(private var lockDir: File? = null) : LockFactory {

    override fun setLockDir(lockDir: File) {
        this.lockDir = lockDir
    }

    @Synchronized
    override fun create(fileName: String, writeAccess: Boolean): GHLock {
        // TODO no read access-only support
        val dir = lockDir ?: throw RuntimeException("Set lockDir before creating locks")

        return SimpleLock(dir, fileName)
    }

    @Synchronized
    override fun forceRemove(fileName: String, writeAccess: Boolean) {
        if (lockDir!!.exists()) {
            val lockFile = File(lockDir, fileName)
            if (lockFile.exists() && !lockFile.delete())
                throw RuntimeException("Cannot delete $lockFile")
        }
    }

    internal class SimpleLock(private val lockDir: File, fileName: String) : GHLock {
        private val lockFile = File(lockDir, fileName)
        private val name = fileName
        private var failedReason: IOException? = null

        @Synchronized
        override fun tryLock(): Boolean {
            // make sure directory exists, do it on-the-fly (not possible when setLockDir is called)
            if (!lockDir.exists()) {
                if (!lockDir.mkdirs())
                    throw RuntimeException("Directory $lockDir does not exist and cannot be created to place lock file there: $lockFile")
            }

            // this test can only be performed after the dir has created!
            if (!lockDir.isDirectory)
                throw IllegalArgumentException("lockDir has to be a directory: $lockDir")

            return try {
                lockFile.createNewFile()
            } catch (ex: IOException) {
                failedReason = ex
                false
            }
        }

        @Synchronized
        override fun isLocked(): Boolean = lockFile.exists()

        @Synchronized
        override fun release() {
            if (isLocked() && lockFile.exists() && !lockFile.delete())
                throw RuntimeException("Cannot release lock file: $lockFile")
        }

        override fun getName(): String = name

        @Synchronized
        override fun getObtainFailedReason(): Exception? = failedReason

        override fun toString(): String = lockFile.toString()
    }
}
