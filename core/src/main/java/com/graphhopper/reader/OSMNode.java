package com.graphhopper.reader;

import com.graphhopper.coll.LongIntMap;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Represents an OSM Node User: Nop Date: 06.12.2008 Time: 14:13:32
 */
public class OSMNode extends OSMElement {

    private double lat;
    private double lon;

    public OSMNode(long id, XMLStreamReader parser) throws XMLStreamException {
        super(NODE, id, parser);

        // read location
        lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));

        parser.nextTag();
        readTags(parser);
    }

    public OSMNode(double lat, double lon) {
        super(NODE);

        this.lat = lat;
        this.lon = lon;
    }

    public OSMNode() {
        super(NODE);
    }

    public double lat() {
        return lat;
    }

    public double lon() {
        return lon;
    }

    public String toString() {
        if (tags == null) {
            return "Node (" + id + ")";
        } else {
//            return "Node (" + id + ", " + tags.size() + " tags)";
            StringBuilder txt = new StringBuilder();
            txt.append("Node: ");
            txt.append(id);
            txt.append(" lat=");
            txt.append(lat);
            txt.append(" lon=");
            txt.append(lon);
            txt.append("\n");
            txt.append(tagsToString());
            return txt.toString();
        }

        //return "Node at " + lat + ", " + lon;
    }
}
