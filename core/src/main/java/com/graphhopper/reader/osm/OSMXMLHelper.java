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

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderRelation.Member;
import com.graphhopper.reader.ReaderWay;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * @author Peter Karich
 */
public class OSMXMLHelper {

    public static ReaderNode createNode(long id, XMLStreamReader parser) throws XMLStreamException {
        ReaderNode node = new ReaderNode(id,
                Double.parseDouble(parser.getAttributeValue(null, "lat")),
                Double.parseDouble(parser.getAttributeValue(null, "lon")));

        parser.nextTag();
        readTags(node, parser);
        return node;
    }

    public static ReaderWay createWay(long id, XMLStreamReader parser) throws XMLStreamException {
        ReaderWay way = new ReaderWay(id);
        parser.nextTag();
        readNodes(way, parser);
        readTags(way, parser);
        return way;
    }

    private static void readNodes(ReaderWay way, XMLStreamReader parser) throws XMLStreamException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("nd")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read node reference
                String ref = parser.getAttributeValue(null, "ref");
                way.getNodes().add(Long.parseLong(ref));
            }

            event = parser.nextTag();
        }
    }

    private static void readTags(ReaderElement re, XMLStreamReader parser) throws XMLStreamException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("tag")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read tag
                String key = parser.getAttributeValue(null, "k");
                String value = parser.getAttributeValue(null, "v");
                // ignore tags with empty values
                if (value != null && value.length() > 0)
                    re.setTag(key, value);
            }

            event = parser.nextTag();
        }
    }

    public static ReaderRelation createRelation(long id, XMLStreamReader parser) throws XMLStreamException {
        ReaderRelation rel = new ReaderRelation(id);

        parser.nextTag();
        readMembers(rel, parser);
        readTags(rel, parser);
        return rel;
    }

    private static void readMembers(ReaderRelation rel, XMLStreamReader parser) throws XMLStreamException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equalsIgnoreCase("member")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read member
                rel.add(createMember(parser));
            }

            event = parser.nextTag();
        }

    }

    public static Member createMember(XMLStreamReader parser) {
        String typeName = parser.getAttributeValue(null, "type");
        ReaderElement.Type type = ReaderElement.Type.NODE;
        if (typeName.startsWith("w")) {
            type = ReaderElement.Type.WAY;
        } else if (typeName.startsWith("r")) {
            type = ReaderElement.Type.RELATION;
        }
        long ref = Long.parseLong(parser.getAttributeValue(null, "ref"));
        String role = parser.getAttributeValue(null, "role");
        return new ReaderRelation.Member(type, ref, role);
    }
}
