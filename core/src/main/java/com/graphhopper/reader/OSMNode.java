/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.reader;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Map;

/**
 * Represents an OSM Node
 * <p/>
 * @author Nop
 */
public class OSMNode extends OSMElement
{
    private double lat;
    private double lon;

    public OSMNode( long id, XMLStreamReader parser ) throws XMLStreamException
    {
        super(id, NODE, parser);

        // read location
        lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));

        parser.nextTag();
        readTags(parser);
    }

    public OSMNode( long id, Map<String, String> tags, double lat, double lon )
    {
        super(id, NODE, tags);
        this.lat = lat;
        this.lon = lon;
    }

    public OSMNode( long id, double lat, double lon )
    {
        super(id, NODE);

        this.lat = lat;
        this.lon = lon;
    }

    public double getLat()
    {
        return lat;
    }

    public double getLon()
    {
        return lon;
    }

    @Override
    public String toString()
    {
        if (tags == null)
        {
            return "Node (" + id + ")";
        } else
        {
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
