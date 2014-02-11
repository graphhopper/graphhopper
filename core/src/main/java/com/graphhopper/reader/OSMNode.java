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

import com.graphhopper.util.Helper;
import com.graphhopper.util.PointAccess;
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
    private final double lat;
    private final double lon;
    private double ele;

    public OSMNode( long id, XMLStreamReader parser ) throws XMLStreamException
    {
        this(id, Double.parseDouble(parser.getAttributeValue(null, "lat")),
                Double.parseDouble(parser.getAttributeValue(null, "lon")));

        String eleStr = parser.getAttributeValue(null, "ele");
        if(!Helper.isEmpty(eleStr))
            this.ele = Double.parseDouble(eleStr);
        
        parser.nextTag();
        readTags(parser);
    }

    public OSMNode( long id, Map<String, String> tags, double lat, double lon )
    {
        this(id, lat, lon);
        replaceTags(tags);
    }

    public OSMNode( long id, PointAccess pointAccess, int accessId )
    {
        super(id, NODE);

        this.lat = pointAccess.getLatitude(accessId);
        this.lon = pointAccess.getLongitude(accessId);
        if (pointAccess.is3D())
            this.ele = pointAccess.getElevation(accessId);
        else
            this.ele = Double.NaN;
    }

    public OSMNode( long id, double lat, double lon )
    {
        super(id, NODE);

        this.lat = lat;
        this.lon = lon;
        this.ele = Double.NaN;
    }

    public double getLat()
    {
        return lat;
    }

    public double getLon()
    {
        return lon;
    }

    public double getEle()
    {
        return ele;
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
