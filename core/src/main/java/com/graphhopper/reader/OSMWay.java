package com.graphhopper.reader;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;

/**
 * Represents an OSM Way User: Nop Date: 06.12.2008 Time: 14:13:32
 */
public class OSMWay extends OSMElement {

    protected TLongList nodes;

    public OSMWay(long id, XMLStreamReader parser) throws XMLStreamException {
        super(WAY, id, parser);
        nodes = new TLongArrayList();

        parser.nextTag();
        readNodes(parser);
        readTags(parser);
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