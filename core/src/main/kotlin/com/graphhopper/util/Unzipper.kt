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
package com.graphhopper.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.LongConsumer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * @author Peter Karich
 */
class Unzipper {
    @Throws(IOException::class)
    fun unzip(from: String, remove: Boolean) {
        val to = Helper.pruneFileEnd(from)
        unzip(from, to, remove)
    }

    @Throws(IOException::class)
    fun unzip(fromStr: String, toStr: String, remove: Boolean): Boolean {
        val from = File(fromStr)
        if (!from.exists() || fromStr == toStr)
            return false

        unzip(FileInputStream(from), File(toStr), null)

        if (remove)
            Helper.removeDir(from)
        return true
    }

    /**
     * @param progressListener updates not in percentage but the number of bytes already read.
     */
    @Throws(IOException::class)
    fun unzip(fromIs: InputStream, toFolder: File, progressListener: LongConsumer?) {
        if (!toFolder.exists())
            toFolder.mkdirs()

        var sumBytes = 0L
        val zis = ZipInputStream(fromIs)
        try {
            var ze = zis.nextEntry
            val buffer = ByteArray(8 * 1024)
            while (ze != null) {
                if (ze.isDirectory) {
                    getVerifiedFile(toFolder, ze).mkdir()
                } else {
                    var factor = 1.0
                    if (ze.compressedSize > 0 && ze.size > 0)
                        factor = ze.compressedSize.toDouble() / ze.size

                    val newFile = getVerifiedFile(toFolder, ze)
                    val fos = FileOutputStream(newFile)
                    try {
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                            // just like Java's compound assignment "sumBytes += len * factor": truncate towards zero
                            sumBytes = (sumBytes + len * factor).toLong()
                            progressListener?.accept(sumBytes)
                        }
                    } finally {
                        fos.close()
                    }
                }

                ze = zis.nextEntry
            }
            zis.closeEntry()
        } finally {
            zis.close()
        }
    }

    // see #1628
    @JvmName("getVerifiedFile")
    @Throws(IOException::class)
    internal fun getVerifiedFile(destinationDir: File, ze: ZipEntry): File {
        val destinationFile = File(destinationDir, ze.name)
        if (!destinationFile.canonicalPath.startsWith(destinationDir.canonicalPath + File.separator))
            throw SecurityException("Zip Entry is outside of the target dir: " + ze.name)
        return destinationFile
    }
}
