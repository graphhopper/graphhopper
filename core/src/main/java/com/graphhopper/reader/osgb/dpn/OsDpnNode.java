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
package com.graphhopper.reader.osgb.dpn;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import uk.co.ordnancesurvey.api.srs.GeodeticPoint;
import uk.co.ordnancesurvey.api.srs.MapPoint;
import uk.co.ordnancesurvey.api.srs.OSGrid2LatLong;
import uk.co.ordnancesurvey.api.srs.OutOfRangeException;

import com.graphhopper.reader.Node;
import com.graphhopper.reader.osgb.OSITNElement;
import com.graphhopper.util.PointAccess;

/**
 * Represents an OSM Node
 * <p/>
 * 
 * @author Nop
 */
public class OsDpnNode extends OSITNElement implements Node {
	private double lat;
	private double lon;
	private static OSGrid2LatLong coordConvertor = new OSGrid2LatLong();

	public static OsDpnNode create(long id, XMLStreamReader parser)
			throws XMLStreamException {
		// int attributeCount = parser.getAttributeCount();
		// for (int i = 0; i < attributeCount; i++) {
		// QName attributeName = parser.getAttributeName(i);
		// System.err.println("QName:" + attributeName);
		// }
		OsDpnNode node = new OsDpnNode(id);

		parser.nextTag();
		node.readTags(parser);
		return node;
	}

	public OsDpnNode(long id, PointAccess pointAccess, int accessId) {
		super(id, NODE);

		this.lat = pointAccess.getLatitude(accessId);
		this.lon = pointAccess.getLongitude(accessId);
		if (pointAccess.is3D())
			setTag("ele", pointAccess.getElevation(accessId));
	}

	public OsDpnNode(long id) {
		super(id, NODE);

	}

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public double getEle() {
		Object ele = getTags().get("ele");
		if (ele == null)
			// return Double.NaN;
			return 1d;
		return (Double) ele;
	}

	@Override
	public void setTag(String name, Object value) {
		if ("ele".equals(name)) {
			if (value == null)
				value = null;
			else if (value instanceof String) {
				String str = (String) value;
				str = str.trim().replaceAll("\\,", ".");
				if (str.isEmpty())
					value = null;
				else
					try {
						value = Double.parseDouble(str);
					} catch (NumberFormatException ex) {
						return;
					}
			} else
				// force cast
				value = ((Number) value).doubleValue();
		}
		super.setTag(name, value);
	}

	@Override
	public String toString() {
		StringBuilder txt = new StringBuilder();
		txt.append("Node: ");
		txt.append(getId());
		txt.append(" lat=");
		txt.append(getLat());
		txt.append(" lon=");
		txt.append(getLon());
		if (!getTags().isEmpty()) {
			txt.append("\n");
			txt.append(tagsToString());
		}
		return txt.toString();
	}

	@Override
	protected void parseCoords(String elementText) {
		// TODO plugin OSGBCOordConverter
		String[] split = elementText.split(",");

		Double easting = Double.parseDouble(split[0]);
		Double northing = Double.parseDouble(split[1]);
		GeodeticPoint wgs84 = toWGS84(easting, northing);
		lat = wgs84.getLatAngle();
		lon = wgs84.getLongAngle();
		System.err.println(toString());
	}

	@Override
	protected void parseNetworkMember(String elementText) {
		throw new UnsupportedOperationException("Nodes should not have members");
	}

	@Override
	protected void addDirectedNode(String nodeId, String grade, String orientation) {
		throw new UnsupportedOperationException(
				"Nodes should not have directed nodes");
	}
	
	@Override
	protected void addDirectedLink(String nodeId, String orientation) {
		throw new UnsupportedOperationException(
				"Nodes should not have directed links");
	}
	
	private static GeodeticPoint toWGS84(double easting, double northing) {
		MapPoint osgb36Pt = new MapPoint(easting, northing);
		GeodeticPoint wgs84Pt = null;
		try {
			wgs84Pt = coordConvertor.transformHiRes(osgb36Pt);
		} catch (OutOfRangeException ore) {
			//REALLY? 
			//TODO should this be where the lowres route goes?
		}
		if (null==wgs84Pt) {
			try {
				wgs84Pt = coordConvertor.transformLoRes(osgb36Pt);
			} catch(OutOfRangeException ore) {
				
			}
		}
		return wgs84Pt;
	}

	@Override
	protected void parseCoordinateString(String elementText,
			String elementSeparator) {
		throw new UnsupportedOperationException();
		
	}

	@Override
	protected void parseCoords(int dimensions, String lineDefinition) {
		throw new UnsupportedOperationException();
	}

}
