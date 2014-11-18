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

import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.api.srs.LatLong;
import uk.co.ordnancesurvey.api.srs.OpenCoordConverter;

import com.graphhopper.reader.Node;
import com.graphhopper.util.PointAccess;

/**
 * Represents an OSM Node
 * <p/>
 * 
 * @author Nop
 */
public class OSITNNode extends OSITNElement implements Node {
	private double lat;
	private double lon;
	private static final Logger logger = LoggerFactory
			.getLogger(OSITNNode.class);
	private boolean[] clones = {false,false,false,false};

	public static OSITNNode create(long id, XMLStreamReader parser)
			throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
		OSITNNode node = new OSITNNode(id);

		parser.nextTag();
		node.readTags(parser);
		return node;
	}

	public OSITNNode(long id, PointAccess pointAccess, int accessId) {
		super(id, NODE);

		this.lat = pointAccess.getLatitude(accessId);
		this.lon = pointAccess.getLongitude(accessId);
		if (pointAccess.is3D())
			setTag("ele", pointAccess.getElevation(accessId));
	}
	
	public OSITNNode(long id) {
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
	public void parseCoords(String elementText) throws MismatchedDimensionException, FactoryException, TransformException {
		String elementSeparator = ",";
		parseCoordinateString(elementText, elementSeparator);
	}

	public void parseCoordinateString(String elementText,
			String elementSeparator) throws MismatchedDimensionException, FactoryException, TransformException {
		String[] split = elementText.split(elementSeparator);

		if(3==split.length) {
			setTag("ele", split[2]);
		}
		Double easting = Double.parseDouble(split[0]);
		Double northing = Double.parseDouble(split[1]);
		LatLong wgs84 = OpenCoordConverter.toWGS84(easting, northing);
		lat = wgs84.getLatAngle();
		lon = wgs84.getLongAngle();
		if (logger.isDebugEnabled()) logger.debug(toString());
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
	
	public OSITNNode gradeClone(long nodeId) {
		OSITNNode clone = new OSITNNode(nodeId);
		Map<String, Object> tags = this.getTags();
		clone.setTags(tags);
		clone.lat = this.lat;
		clone.lon = this.lon;
		return clone;
	}

	@Override
	protected void parseCoords(int dimensions, String lineDefinition) {
		throw new UnsupportedOperationException();
	}

}
