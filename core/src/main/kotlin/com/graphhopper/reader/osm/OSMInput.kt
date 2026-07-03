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
package com.graphhopper.reader.osm

import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.osm.pbf.PbfReader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLStreamException

/**
 * Interface for reading OSM data from various file formats.
 */
interface OSMInput : AutoCloseable {
    @Throws(XMLStreamException::class)
    fun getNext(): ReaderElement?

    companion object {
        /**
         * Opens an OSM file, automatically detecting the format (PBF or XML) based on file contents.
         *
         * @param file          the OSM file to open
         * @param workerThreads number of worker threads for PBF parsing (ignored for XML)
         * @param skipOptions   options to skip certain element types during parsing
         * @return an OSMInput instance for the detected format
         */
        @JvmStatic
        @Throws(IOException::class, XMLStreamException::class)
        fun open(file: File, workerThreads: Int, skipOptions: SkipOptions): OSMInput {
            val decoded = decode(file)
            return if (decoded.isBinary) {
                PbfReader(decoded.inputStream, workerThreads, skipOptions).start()
            } else {
                OSMXmlInput(decoded.inputStream).open()
            }
        }

        @Throws(IOException::class)
        private fun decode(file: File): DecodedInput {
            val name = file.name

            val ips: InputStream
            try {
                ips = BufferedInputStream(FileInputStream(file), 50000)
            } catch (e: FileNotFoundException) {
                throw RuntimeException(e)
            }
            ips.mark(10)

            // check file header
            val header = ByteArray(6)
            if (ips.read(header) < 0)
                throw IllegalArgumentException("Input file is not of valid type " + file.path)

            if (header[0] == 31.toByte() && header[1] == (-117).toByte()) {
                // GZIP
                ips.reset()
                return DecodedInput(GZIPInputStream(ips, 50000), false)
            } else if (header[0] == 0.toByte() && header[1] == 0.toByte() && header[2] == 0.toByte()
                && header[4] == 10.toByte() && header[5] == 9.toByte()
                && (header[3] == 13.toByte() || header[3] == 14.toByte())) {
                // PBF
                ips.reset()
                return DecodedInput(ips, true)
            } else if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
                // ZIP
                ips.reset()
                val zip = ZipInputStream(ips)
                zip.nextEntry
                return DecodedInput(zip, false)
            } else if (name.endsWith(".osm") || name.endsWith(".xml")) {
                // Plain XML
                ips.reset()
                return DecodedInput(ips, false)
            } else if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
                // BZIP2 - requires optional dependency
                val clName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream"
                try {
                    @Suppress("UNCHECKED_CAST")
                    val clazz = Class.forName(clName) as Class<InputStream>
                    ips.reset()
                    val ctor = clazz.getConstructor(InputStream::class.java, Boolean::class.javaPrimitiveType)
                    return DecodedInput(ctor.newInstance(ips, true), false)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Cannot instantiate $clName", e)
                }
            } else {
                throw IllegalArgumentException("Input file is not of valid type " + file.path)
            }
        }
    }
}

/**
 * Helper class to return both the decoded input stream and whether it's binary (PBF) format.
 */
private class DecodedInput(val inputStream: InputStream, val isBinary: Boolean)
