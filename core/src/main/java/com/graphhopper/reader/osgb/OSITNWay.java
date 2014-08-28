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
package com.graphhopper.reader.osgb;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.graphhopper.reader.Way;

import java.util.Map;

/**
 * Represents an OSM Way
 * <p/>
 * @author Nop
 */
public class OSITNWay extends OSITNElement implements Way
{
    protected final TLongList nodes = new TLongArrayList(5);

    /**
     * Constructor for XML Parser
     */
    public static OSITNWay create( long id, XMLStreamReader parser ) throws XMLStreamException
    {
        OSITNWay way = new OSITNWay(id);
        parser.nextTag();
        way.readTags(parser);
        way.setTag("highway", "motorway");
        System.err.println(way.toString());
        return way;
    }

    public OSITNWay( long id )
    {
        super(id, WAY);
    }

    public TLongList getNodes()
    {
        return nodes;
    }

    @Override
    public String toString()
    {
        return "Way (" + getId() + ", " + nodes.size() + " nodes)";
    }

	@Override
	protected void parseCoords(String elementText) {
		// TODO Auto-generated method stub
		//bounding box?
		//throw new UnsupportedOperationException();
	}

	@Override
	protected void parseNetworkMember(String elementText) {
		throw new UnsupportedOperationException();
		
	}

	@Override
	protected void addDirectedNode(String nodeId, String orientation) {
		String idStr = nodeId.substring(5);
		long id = Long.parseLong(idStr);
		nodes.add(id);
	}
}
