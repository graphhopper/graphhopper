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

package com.graphhopper.reader.dem

import com.graphhopper.util.Helper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipInputStream

open class HGTProvider(dir: String) : AbstractSRTMElevationProvider("", dir, "", Integer.MIN_VALUE, Integer.MAX_VALUE, 3601) {

    @Throws(IOException::class)
    override fun readFile(file: File): ByteArray {
        val stream = Files.newInputStream(file.toPath())
        val zis = ZipInputStream(stream)
        zis.nextEntry
        val buff = BufferedInputStream(zis)
        val os = ByteArrayOutputStream()
        val buffer = ByteArray(0xFFFF)
        var len: Int
        while (buff.read(buffer).also { len = it } > 0) {
            os.write(buffer, 0, len)
        }
        os.flush()
        Helper.close(buff)
        return os.toByteArray()
    }

    override fun getFileName(lat: Double, lon: Double): String? {
        val latInt = Math.floor(lat).toInt()
        val lonInt = Math.floor(lon).toInt()
        return cacheDir.toString() + "/" + (if (lat > 0) "N" else "S") + getPaddedLatString(latInt) + (if (lon > 0) "E" else "W") + getPaddedLonString(lonInt) + ".hgt.zip"
    }

    override fun getDownloadURL(lat: Double, lon: Double): String {
        return getFileName(lat, lon)!!
    }
}
