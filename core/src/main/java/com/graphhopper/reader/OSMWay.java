/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.reader;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Map;

/**
 * Represents an OSM Way
 *
 * @author Nop
 */
public class OSMWay extends OSMElement {

    protected TLongList nodes;

    /**
     * Constructor for XML Parser
     * @param id
     * @param parser
     * @throws XMLStreamException
     */
    public OSMWay(long id, XMLStreamReader parser) throws XMLStreamException {
        super(WAY, id, parser);
        nodes = new TLongArrayList();

        parser.nextTag();
        readNodes(parser);
        readTags(parser);
    }

    /**
     * Constructor for PBF Parser
     * @param id
     * @param tags
     */
    public OSMWay( long id, Map<String, String> tags ) {
        super( WAY, id, tags );

        nodes = new TLongArrayList();
    }

    public OSMWay(OSMWay src) {
        super(src);

        nodes = new TLongArrayList(src.nodes);
    }

    public OSMWay() {
        type = WAY;
        nodes = new TLongArrayList();
    }

    public OSMWay(OSMNode geometry[], boolean closed) {
        super(WAY);

        nodes = new TLongArrayList();
        for (int i = 0; i < geometry.length; i++) {
            nodes.add(geometry[i].id());
        }
        // close polygon
        if (closed)
            nodes.add(geometry[0].id());
    }

    protected void readNodes(XMLStreamReader parser) throws XMLStreamException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equals("nd")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read node reference
                String ref = parser.getAttributeValue(null, "ref");
                nodes.add(Long.parseLong(ref));
            }

            event = parser.nextTag();
        }
    }

    public TLongList nodes() {
        return nodes;
    }

    public void addNodeRef(long id) {
        nodes.add(id);
    }

    public String toString() {
        return "Way (" + id + ", " + nodes.size() + " nodes)";
    }

    public void clearNodes() {
        nodes.clear();
    }

    public void setNodes(TLongList nodes) {
        this.nodes = nodes;
    }
}