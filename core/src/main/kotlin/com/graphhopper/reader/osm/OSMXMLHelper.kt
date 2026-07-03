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
import com.graphhopper.reader.ReaderNode
import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderRelation.Member
import com.graphhopper.reader.ReaderWay
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * @author Peter Karich
 */
object OSMXMLHelper {

    @JvmStatic
    @Throws(XMLStreamException::class)
    fun createNode(id: Long, parser: XMLStreamReader): ReaderNode {
        val node = ReaderNode(id,
            parser.getAttributeValue(null, "lat").toDouble(),
            parser.getAttributeValue(null, "lon").toDouble())

        parser.nextTag()
        readTags(node, parser)
        return node
    }

    @JvmStatic
    @Throws(XMLStreamException::class)
    fun createWay(id: Long, parser: XMLStreamReader): ReaderWay {
        val way = ReaderWay(id)
        parser.nextTag()
        readNodes(way, parser)
        readTags(way, parser)
        return way
    }

    @Throws(XMLStreamException::class)
    private fun readNodes(way: ReaderWay, parser: XMLStreamReader) {
        var event = parser.eventType
        while (event != XMLStreamConstants.END_DOCUMENT && parser.localName == "nd") {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read node reference
                val ref = parser.getAttributeValue(null, "ref")
                way.nodes.add(ref.toLong())
            }

            event = parser.nextTag()
        }
    }

    @Throws(XMLStreamException::class)
    private fun readTags(re: ReaderElement, parser: XMLStreamReader) {
        var event = parser.eventType
        while (event != XMLStreamConstants.END_DOCUMENT && parser.localName == "tag") {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read tag
                val key = parser.getAttributeValue(null, "k")
                val value = parser.getAttributeValue(null, "v")
                // ignore tags with empty values
                if (value != null && value.isNotEmpty())
                    re.setTag(key, value)
            }

            event = parser.nextTag()
        }
    }

    @JvmStatic
    @Throws(XMLStreamException::class)
    fun createRelation(id: Long, parser: XMLStreamReader): ReaderRelation {
        val rel = ReaderRelation(id)

        parser.nextTag()
        readMembers(rel, parser)
        readTags(rel, parser)
        return rel
    }

    @Throws(XMLStreamException::class)
    private fun readMembers(rel: ReaderRelation, parser: XMLStreamReader) {
        var event = parser.eventType
        while (event != XMLStreamConstants.END_DOCUMENT && parser.localName.equals("member", ignoreCase = true)) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read member
                rel.add(createMember(parser))
            }

            event = parser.nextTag()
        }
    }

    @JvmStatic
    fun createMember(parser: XMLStreamReader): Member {
        val typeName = parser.getAttributeValue(null, "type")
        var type = ReaderElement.Type.NODE
        if (typeName.startsWith("w")) {
            type = ReaderElement.Type.WAY
        } else if (typeName.startsWith("r")) {
            type = ReaderElement.Type.RELATION
        }
        val ref = parser.getAttributeValue(null, "ref").toLong()
        val role = parser.getAttributeValue(null, "role")
        return Member(type, ref, role)
    }
}
