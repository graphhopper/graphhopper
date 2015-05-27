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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.RoutingElement;

/**
 * Base class for all OSM objects
 * <p/>
 *
 * @author Nop
 * @author Peter
 */
public abstract class OsDpnElement implements RoutingElement {
    public static final int NODE = 0;
    public static final int WAY = 1;
    public static final int RELATION = 2;
    private final int type;
    private final String id;
    private final Map<String, Object> properties = new HashMap<String, Object>(5);
    private static final Logger logger = LoggerFactory.getLogger(OsDpnElement.class);

    private boolean nameSet = false;

    protected OsDpnElement(String id, int type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    protected void readTags(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException,
    FactoryException, TransformException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT
                && (event != XMLStreamConstants.END_ELEMENT || !exitElement(parser)))
            if (event == XMLStreamConstants.CHARACTERS)
                event = parser.next();
            else if (event == XMLStreamConstants.START_ELEMENT)
                // logger.info("LOCALNAME: {}", parser.getLocalName());
                switch (parser.getLocalName()) {
                case "pos":
                case "coordinates": {
                    event = handleCoordinates(parser);
                    break;
                }
                case "networkMember": {
                    event = handleNetworkMember(parser);
                    break;
                }
                case "posList": {
                    event = handleMultiDimensionCoords(parser);
                    break;
                }
                case "startNode":
                case "endNode": {
                    event = handleNode(parser);
                    break;
                }
                case "directedLink": {
                    event = handleDirectedLink(parser);
                    break;
                }
                case "instruction": {
                    setTag("type", "restriction");
                    event = handleTag("restriction", parser);
                    break;
                }
                case "surfaceType": {
                    event = handleSurfaceType(parser);
                    break;
                }
                case "descriptiveTerm": {
                    event = handleDescriptiveTerm(parser);
                    break;
                }
                case "name":
                case "alternativeName": {
                    event = handleName(parser);
                    break;
                }
                case "physicalLevel": {
                    event = handlePhysicalLevel(parser);
                    break;
                }
                case "rightOfUse": {
                    event = handleRightOfUse(parser);
                    break;
                }
                case "potentialHazardCrossed": {
                    event = handlePotentialHazard(parser);
                    break;
                }

                case "adoptedByNationalCycleRoute":
                case "adoptedByOtherCycleRoute":
                case "adoptedByRecreationalRoute":
                case "withinAccessLand": {
                    event = handleAdditionalRights(parser);
                    break;
                }
                default: {
                    event = parser.next();
                }
                }
            else
                // logger.trace("EVENT:" + event);
                event = parser.next();
    }

    protected int handleAdditionalRights(XMLStreamReader parser) throws XMLStreamException {
        return parser.next();
    }

    //
    // protected int handleAccessLand(XMLStreamReader parser) throws
    // XMLStreamException
    // {
    // return parser.next();
    // }

    protected int handleSurfaceType(XMLStreamReader parser) throws XMLStreamException {
        return parser.next();
    }

    protected int handlePhysicalLevel(XMLStreamReader parser) throws XMLStreamException {
        return parser.next();
    }

    protected int handleRightOfUse(XMLStreamReader parser) throws XMLStreamException {
        return parser.next();
    }

    protected int handlePotentialHazard(XMLStreamReader parser) throws XMLStreamException {
        return parser.next();
    }

    private int handleName(XMLStreamReader parser) throws XMLStreamException {
        StringBuilder nameString = new StringBuilder();
        if (nameSet) {
            nameString.append(getTag("name"));
            nameString.append(" (");
        }
        nameString.append(parser.getElementText());
        if (nameSet)
            nameString.append(")");
        nameSet = true;
        setTag("name", nameString.toString());
        return parser.getEventType();
    }

    private int handleDescriptiveTerm(XMLStreamReader parser) throws XMLStreamException {
        String roadType = parser.getElementText();
        setTag("type", "route");
        setTag("highway", getOsmMappedTypeName(roadType));
        setTag("name", getTypeBasedName(roadType));
        return parser.getEventType();
    }

    private String getTypeBasedName(String roadType) {
        if (roadType.equals("No Physical Manifestation"))
            return "Route";
        return roadType;
    }

    private String getOsmMappedTypeName(String roadType) {
        String typeName = roadType;
//        switch (roadType) {
//        case "A Road":
//            typeName = "primary";
//            break;
//        case "B Road":
//            typeName = "secondary";
//            break;
//        case "Alley":
//            typeName = "service";
//            setTag("service", "alley");
//            break;
//        case "Private Road":
//            typeName = "private";
//            break;
//        case "Path":
//            typeName = "path";
//            break;
//        default:
//            break;
//        }
        return typeName;
    }

    private int handleDirectedLink(XMLStreamReader parser) throws XMLStreamException {
        String orientation = parser.getAttributeValue(null, "orientation");
        String nodeId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        addDirectedLink(nodeId, orientation);
        return parser.next();
    }

    private int handleNode(XMLStreamReader parser) throws XMLStreamException {
        String nodeId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        addNode(nodeId);
        return parser.next();
    }

    private int handleTag(String key, XMLStreamReader parser) throws XMLStreamException {
        properties.put(key, parser.getElementText());
        return parser.getEventType();
    }

    private int handleNetworkMember(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        parseNetworkMember(elementText);
        return parser.next();
    }

    private int handleCoordinates(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException,
    FactoryException, TransformException {
        String elementText = parser.getElementText();
        parseCoords(elementText);
        return parser.getEventType();
    }

    private int handleMultiDimensionCoords(XMLStreamReader parser) throws XMLStreamException {
        String dimensionality = parser.getAttributeValue(null, "srsDimension");
        logger.info("Dimensions:" + dimensionality);
        String elementText = parser.getElementText();
        parseCoords(Integer.valueOf(dimensionality), elementText);
        return parser.getEventType();
    }

    protected abstract void parseCoords(String coordinates) throws MismatchedDimensionException, FactoryException,
    TransformException;

    protected abstract void parseCoords(int dimensions, String lineDefinition);

    protected abstract void addNode(String nodeId);

    protected abstract void addDirectedLink(String nodeId, String orientation);

    protected abstract void parseNetworkMember(String elementText);

    private boolean exitElement(XMLStreamReader parser) {
        switch (parser.getLocalName()) {
        case "RouteNode":
        case "RouteLink":
        case "Route":
            return true;
        }
        return false;
    }

    protected String tagsToString() {
        if (properties.isEmpty())
            return "<empty>";

        StringBuilder tagTxt = new StringBuilder();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            tagTxt.append(entry.getKey());
            tagTxt.append("=");
            tagTxt.append(entry.getValue());
            tagTxt.append("\n");
        }
        return tagTxt.toString();
    }

    protected Map<String, Object> getTags() {
        return properties;
    }

    public void setTags(Map<String, String> newTags) {
        properties.clear();
        if (newTags != null)
            for (Entry<String, String> e : newTags.entrySet())
                setTag(e.getKey(), e.getValue());
    }

    @Override
    public boolean hasTags() {
        return !properties.isEmpty();
    }

    @Override
    public String getTag(String name) {
        Object object = properties.get(name);
        return (null != object) ? (String) object.toString() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getTag(String key, T defaultValue) {
        T val = (T) properties.get(key);
        if (val == null)
            return defaultValue;
        return val;
    }

    @Override
    public void setTag(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Chaeck that the object has a given tag with a given value.
     */
    @Override
    public boolean hasTag(String key, Object value) {
        return value.equals(properties.get(key));
    }

    /**
     * Check that a given tag has one of the specified values. If no values are
     * given, just checks for presence of the tag
     */
    @Override
    public boolean hasTag(String key, String... values) {
        Object osmValue = properties.get(key);
        if (osmValue == null)
            return false;

        // tag present, no values given: success
        if (values.length == 0)
            return true;

        for (String val : values)
            if (val.equals(osmValue))
                return true;
        return false;
    }

    /**
     * Check that a given tag has one of the specified values.
     */
    @Override
    public final boolean hasTag(String key, Set<String> values) {
        return values.contains(properties.get(key));
    }

    /**
     * Check a number of tags in the given order for the any of the given
     * values. Used to parse hierarchical access restrictions
     */
    @Override
    public boolean hasTag(List<String> keyList, Set<String> values) {
        for (String key : keyList)
            if (values.contains(properties.get(key)))
                return true;
        return false;
    }

    public void removeTag(String name) {
        properties.remove(name);
    }

    public void clearTags() {
        properties.clear();
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public boolean isType(int type) {
        return this.type == type;
    }
}
