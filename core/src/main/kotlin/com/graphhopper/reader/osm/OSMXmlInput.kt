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
import java.io.IOException
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * OSM input implementation for XML format (.osm, .osm.gz, etc.)
 */
internal class OSMXmlInput(private val inputStream: InputStream) : OSMInput {
    private var xmlParser: XMLStreamReader? = null
    private var eof = false
    private var fileheader: OSMFileHeader? = null

    @Throws(XMLStreamException::class)
    fun open(): OSMXmlInput {
        val factory = XMLInputFactory.newInstance()
        val parser = factory.createXMLStreamReader(inputStream, "UTF-8")
        xmlParser = parser

        val event = parser.next()
        if (event != XMLStreamConstants.START_ELEMENT || !parser.localName.equals("osm", ignoreCase = true)) {
            throw IllegalArgumentException("File is not a valid OSM stream")
        }
        // See https://wiki.openstreetmap.org/wiki/PBF_Format#Definition_of_the_OSMHeader_fileblock
        var timestamp: String? = parser.getAttributeValue(null, "osmosis_replication_timestamp")

        if (timestamp == null)
            timestamp = parser.getAttributeValue(null, "timestamp")

        if (timestamp != null) {
            fileheader = OSMFileHeader().also { it.setTag("timestamp", timestamp) }
        }

        eof = false
        return this
    }

    @Throws(XMLStreamException::class)
    override fun getNext(): ReaderElement? {
        if (eof)
            throw IllegalStateException("EOF reached")

        val parser = xmlParser!!
        var event = parser.next()
        if (fileheader != null) {
            val copyfileheader: ReaderElement = fileheader!!
            fileheader = null
            return copyfileheader
        }

        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                val idStr = parser.getAttributeValue(null, "id")
                if (idStr != null) {
                    val name = parser.localName
                    when (name[0]) {
                        'n' ->
                            // note vs. node
                            if ("node" == name) {
                                return OSMXMLHelper.createNode(idStr.toLong(), parser)
                            }

                        'w' -> return OSMXMLHelper.createWay(idStr.toLong(), parser)

                        'r' -> return OSMXMLHelper.createRelation(idStr.toLong(), parser)
                    }
                }
            }
            event = parser.next()
        }
        parser.close()
        eof = true
        return null
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            xmlParser!!.close()
        } catch (ex: XMLStreamException) {
            throw IOException(ex)
        } finally {
            eof = true
            inputStream.close()
        }
    }
}
