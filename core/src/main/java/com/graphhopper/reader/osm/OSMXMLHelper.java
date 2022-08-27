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
package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderRelation.Member;
import com.graphhopper.reader.ReaderWay;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.*;

/**
 * @author Peter Karich
 */
public class OSMXMLHelper {
    private static final String TYPE_DECODE = "nwr";
    private static final LongArrayList EMPTY_LONG_ARRAY_LIST = new LongArrayList(0);

    public static ReaderNode createNode(long id, XMLStreamReader parser) throws XMLStreamException {
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        parser.nextTag();
        return new ReaderNode(id, lat, lon, readTags(parser));
    }

    public static ReaderWay createWay(long id, XMLStreamReader parser) throws XMLStreamException {
        parser.nextTag();
        LongArrayList nodes = readNodes(parser);
        Map<String, Object> tags = readTags(parser);
        return new ReaderWay(id, nodes, tags);
    }

    private static LongArrayList readNodes(XMLStreamReader parser) throws XMLStreamException {
        LongArrayList nodes = EMPTY_LONG_ARRAY_LIST;
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("nd")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read node reference
                String ref = parser.getAttributeValue(null, "ref");
                if (nodes.isEmpty())
                    nodes = new LongArrayList(5);
                nodes.add(Long.parseLong(ref));
            }

            event = parser.nextTag();
        }
        return nodes;
    }

    private static Map<String, Object> readTags(XMLStreamReader parser) throws XMLStreamException {
        Map<String, Object> tags = Collections.emptyMap();
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("tag")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read tag
                String key = parser.getAttributeValue(null, "k");
                String value = parser.getAttributeValue(null, "v");
                // ignore tags with empty values
                if (value != null && value.length() > 0) {
                    if (tags.isEmpty())
                        tags = new HashMap<>(4);
                    tags.put(key, value);
                }
            }
            event = parser.nextTag();
        }
        return tags;
    }

    public static ReaderRelation createRelation(long id, XMLStreamReader parser) throws XMLStreamException {
        parser.nextTag();
        List<Member> members = readMembers(parser);
        Map<String, Object> tags = readTags(parser);
        return new ReaderRelation(id, members, tags);
    }

    private static List<Member> readMembers(XMLStreamReader parser) throws XMLStreamException {
        List<Member> members = Collections.emptyList();
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equalsIgnoreCase("member")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read member
                if (members.isEmpty())
                    members = new ArrayList<>(5);
                members.add(createMember(parser));
            }
            event = parser.nextTag();
        }
        return members;
    }

    public static Member createMember(XMLStreamReader parser) {
        String typeName = parser.getAttributeValue(null, "type");
        int type = TYPE_DECODE.indexOf(typeName.charAt(0));
        long ref = Long.parseLong(parser.getAttributeValue(null, "ref"));
        String role = parser.getAttributeValue(null, "role");
        return new ReaderRelation.Member(type, ref, role);
    }
}
