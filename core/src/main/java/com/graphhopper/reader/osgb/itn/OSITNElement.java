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
package com.graphhopper.reader.osgb.itn;

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
 * Base class for all OsITN objects
 * <p/>
 *
 * @author Nop
 * @author Peter
 * @author stuartadam
 * @author phopkins
 */
public abstract class OSITNElement implements RoutingElement {
    public static final int NODE = 0;
    public static final int WAY = 1;
    public static final int RELATION = 2;
    public static final int LINK_INFORMATION = 3;

    public static final String TAG_KEY_TYPE = "type";
    public static final String TAG_KEY_ONEWAY_ORIENTATION = "oneway";
    public static final String TAG_KEY_NOENTRY_ORIENTATION = "noentry";
    public static final String TAG_KEY_RESTRICTION = "restriction";
    public static final String TAG_KEY_CLASSIFICATION = "classification";

    public static final String TAG_VALUE_TYPE_ONEWAY = "oneway";
    public static final String TAG_VALUE_TYPE_NOENTRY = "noentry";
    public static final String TAG_VALUE_TYPE_ACCESS_LIMITED = "limited";
    public static final String TAG_VALUE_TYPE_ACCESS_PROHIBITED = "prohibited";
    public static final String TAG_VALUE_TYPE_RESTRICTION = "restriction";

    public static final String TAG_VALUE_CLASSIFICATION_FORD = "ford";
    public static final String TAG_VALUE_CLASSIFICATION_LEVEL_CROSSING = "level crossing";
    public static final String TAG_VALUE_CLASSIFICATION_GATE = "gate";

    private final int type;
    private final long id;
    private final Map<String, Object> properties = new HashMap<String, Object>(5);
    private static final Logger logger = LoggerFactory.getLogger(OSITNElement.class);

    protected OSITNElement(long id, int type) {
        this.id = id;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    protected void readTags(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && (event != XMLStreamConstants.END_ELEMENT || !exitElement(parser))) {
            if (event == XMLStreamConstants.CHARACTERS) {
                event = parser.next();
            } else {

                if (event == XMLStreamConstants.START_ELEMENT) {
                    logger.info("LOCALNAME:" + parser.getLocalName());
                    switch (parser.getLocalName()) {
                    case "coordinates": {
                        event = handleCoordinates(parser);
                        break;
                    }
                    case "posList": {
                        event = handleMultiDimensionCoords(parser);
                        break;
                    }
                    case "pos": {
                        event = handleThreeDimensionalCoords(parser);
                        break;
                    }
                    case "networkMember": {
                        event = handleNetworkMember(parser);
                        break;
                    }
                    case "startNode":
                    case "endNode":
                    case "directedNode": {
                        event = handleDirectedNode(parser);
                        break;
                    }

                    case "referenceToRoadLink": {
                        event = handleReferenceToRoadLink(parser);
                        break;
                    }
                    case "directedLink": {
                        event = handleDirectedLink(parser);
                        break;
                    }
                    case "classification": {
                        event = handleClassification(parser);
                        break;
                    }
                    case "instruction": {
                        // eg. Mandatory Turn, No Turn, One Way, No Entry
                        event = handleInstructionEnvironmentalQualifier(parser);
                        break;
                    }
                    case "type": {
                        // eg. Buses, Coaches, Mopeds, HGV's
                        event = handleTypeVehicleQualifier(parser);
                        break;
                    }
                    case "load": {
                        // eg. Explosives, Dangerous Goods, Abnormal Loads, Wide
                        // Loads
                        event = handleLoadVehicleQualifier(parser);
                        break;
                    }
                    case "use": {
                        // eg. Taxi, School Bus, Patron, Access, Emergency
                        // Vehicle, Public Transport
                        event = handleUseVehicleQualifier(parser);
                        break;
                    }
                    case "descriptiveTerm":
                    case "descriptiveGroup": {
                        event = handleDescriptiveGroup(parser);
                        break;
                    }
                    case "natureOfRoad": {
                        event = handleRoadNature(parser);
                        break;
                    }
                    case "roadNumber":
                    case "name":
                    case "alternativeName":
                    case "roadName": {
                        event = handleTag("name", parser);
                        break;
                    }
                    default: {
                        event = parser.next();
                    }
                    }

                } else {
                    logger.info("EVENT:" + event);
                    event = parser.next();
                }
            }
        }
    }

    private int handleRoadNature(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        String nature = resolveNature(elementText);
        //        String highwayType = getTag("highway");
        if (null != nature && !hasTag("nature")) {
            //            highwayType = highwayType + "-";
            //            highwayType = highwayType + nature;
            setTag("nature", nature);
        }
        else if(elementText.equals("Roundabout")) {
            setTag("junction", "roundabout");
            setTag("direction", "clockwise");
        }
        return parser.getEventType();
    }

    private int handleDescriptiveGroup(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        String roadType = resolveHighway(elementText);
        if (null != roadType && !hasTag("highway")) {
            setTag("type", "route");
            setTag("highway", roadType);
        }
        // This line is for debug and could possibly be removed.
        if (null == roadType) {
            setTag("nothighway", elementText);
        }
        return parser.getEventType();
    }

    private String resolveHighway(String elementText) {
        logger.info("OSITNElement.resolveHighway( " + elementText + ")");
        switch (elementText) {
        case "A Road":
        case "Motorway":
        case "B Road":
        case "Minor Road":
            // Pedestrianised Street is supported for walking so traversing will be controlled by speed in the flag encoders
        case "Pedestrianised Street":
            // Private Road - Publicly Accessible are NOT traversible
            //        case "Private Road - Publicly Accessible":
            // Alleys are not traversible
            // case "Alley":
            // Private Road - Restricted Access are not traversible
            // case "Private Road - Restricted Access":
        case "Local Street":
            return elementText;
        default:
            return null;
        }

    }

    private String resolveNature(String elementText) {
        logger.info("OSITNElement.resolveNature( " + elementText + ")");
        switch (elementText) {
        case "Single Carriageway":
        case "Dual Carriageway":
        case "Slip Road":
            return elementText;
        }
        return null;
    }

    /**
     * Process <code><osgb:instruction>One Way</osgb:instruction></code>
     * instructions within an environmentalQualifier element.
     *
     * It is either "One Way", "No Entry" or a turn restriction type
     *
     * @param parser
     * @return
     * @throws XMLStreamException
     */
    private int handleInstructionEnvironmentalQualifier(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        int event;
        switch (elementText) {
        case "One Way":
            setTag(TAG_KEY_TYPE, TAG_VALUE_TYPE_ONEWAY);
            setTag(TAG_KEY_ONEWAY_ORIENTATION, "-1");
            break;
        case "No Entry":
            // We are processing a No Entry RoadRouteInformation element.
            setTag(TAG_KEY_TYPE, TAG_VALUE_TYPE_NOENTRY);
            // Default the orientation to -1. This could be changed when we
            // process the directedLink element later
            setTag(TAG_KEY_NOENTRY_ORIENTATION, "-1");
            break;
        case "Access Prohibited To":
            // We are processing a Access Prohibited To RoadPartialLinkInformation (maybe a RoadRouteInformation???) element.
            setTag(TAG_KEY_TYPE, TAG_VALUE_TYPE_ACCESS_PROHIBITED);
            break;
        case "Access Limited To":
            // We are processing a Access Limited To RoadPartialLinkInformation (maybe a RoadRouteInformation???) element.
            setTag(TAG_KEY_TYPE, TAG_VALUE_TYPE_ACCESS_LIMITED);
            break;
        default:
            // Handles Mandatory Turn and No Turn
            setTag(TAG_KEY_TYPE, TAG_VALUE_TYPE_RESTRICTION);
            setTag(TAG_KEY_RESTRICTION, elementText);
            break;
        }
        event = parser.getEventType();
        return event;
    }

    private int handleTypeVehicleQualifier(XMLStreamReader parser) throws XMLStreamException {
        String exceptForString = parser.getAttributeValue(null, "exceptFor");
        Boolean exceptFor = null;
        if (exceptForString != null) {
            exceptFor = Boolean.parseBoolean(exceptForString);
        }
        String elementText = parser.getElementText();
        setTag(elementText, exceptFor.toString());
        int event = parser.getEventType();
        return event;
    }

    private int handleLoadVehicleQualifier(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        int event;
        switch (elementText) {
        case "Explosives":
        case "Dangerous Goods":
        case "Abnormal Loads":
        case "Wide Loads":
            break;
        }
        event = parser.getEventType();
        return event;
    }

    private int handleUseVehicleQualifier(XMLStreamReader parser) throws XMLStreamException {
        String exceptForString = parser.getAttributeValue(null, "exceptFor");
        Boolean exceptFor = null;
        if (exceptForString != null) {
            exceptFor = Boolean.parseBoolean(exceptForString);
        }
        String elementText = parser.getElementText();
        setTag(elementText, exceptFor.toString());
        int event = parser.getEventType(); // type and use are effectively the same thing
        return event;
    }

    /**
     * Handle classifications - Level Crossing, Ford, Gate
     * @param parser
     * @return
     * @throws XMLStreamException
     */
    private int handleClassification(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        switch (elementText) {
        case "Ford":
            setTag(TAG_KEY_CLASSIFICATION, TAG_VALUE_CLASSIFICATION_FORD);
            break;
        case "Level Crossing":
            setTag(TAG_KEY_CLASSIFICATION, TAG_VALUE_CLASSIFICATION_LEVEL_CROSSING);
            break;
        case "Gate":
            setTag(TAG_KEY_CLASSIFICATION, TAG_VALUE_CLASSIFICATION_GATE);
            break;
        }
        int event = parser.getEventType();
        return event;
    }

    /**
     * process parsing of directedLink data. If this is a "oneway" OR "noentry"
     * we will change the -1 to true if the orientation on the link it "+"
     *
     * @param parser
     * @return
     * @throws XMLStreamException
     */
    private int handleDirectedLink(XMLStreamReader parser) throws XMLStreamException {
        String orientation = parser.getAttributeValue(null, "orientation");
        String nodeId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        if (hasTag(TAG_KEY_ONEWAY_ORIENTATION, "-1") && orientation.equals("+")) {
            setTag(TAG_KEY_ONEWAY_ORIENTATION, "true");
        }
        if (hasTag(TAG_KEY_NOENTRY_ORIENTATION, "-1") && orientation.equals("+")) {
            setTag(TAG_KEY_NOENTRY_ORIENTATION, "true");
        }
        addDirectedLink(nodeId, orientation);
        return parser.next();
    }

    private int handleReferenceToRoadLink(XMLStreamReader parser) throws XMLStreamException {
        String nodeId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        addDirectedLink(nodeId, "-");// Orientation is ignored
        return parser.next();
    }

    private int handleDirectedNode(XMLStreamReader parser) throws XMLStreamException {
        String orientation = parser.getAttributeValue(null, "orientation");
        String grade = parser.getAttributeValue(null, "gradeSeparation");

        String nodeId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        addDirectedNode(nodeId, grade, orientation);
        return parser.next();
    }

    private int handleTag(String key, XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getElementText();
        logger.info("KEY:" + key + " - VALUE:" + elementText);
        properties.put(key, elementText);
        return parser.getEventType();
    }

    private int handleNetworkMember(XMLStreamReader parser) throws XMLStreamException {
        String elementText = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
        parseNetworkMember(elementText);
        return parser.next();
    }

    private int handleCoordinates(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
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

    private int handleThreeDimensionalCoords(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        String elementText = parser.getElementText();
        parseCoordinateString(elementText, " ");
        return parser.getEventType();
    }

    protected abstract void parseCoordinateString(String elementText, String elementSeparator) throws MismatchedDimensionException, FactoryException, TransformException;

    protected abstract void parseCoords(String coordinates) throws MismatchedDimensionException, FactoryException, TransformException;

    protected abstract void parseCoords(int dimensions, String lineDefinition);

    protected abstract void addDirectedNode(String nodeId, String orientation, String orientation2);

    protected abstract void addDirectedLink(String nodeId, String orientation);

    protected abstract void parseNetworkMember(String elementText);

    private boolean exitElement(XMLStreamReader parser) {
        switch (parser.getLocalName()) {
        case "RoadNode":
        case "RoadLink":
        case "RoadRouteInformation":
        case "RoadLinkInformation":
        case "Road":
        case "RouteLink":
        case "RouteNode":
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

    public void setTags(Map<String, Object> newTags) {
        properties.clear();
        if (newTags != null)
            for (Entry<String, Object> e : newTags.entrySet()) {
                setTag(e.getKey(), e.getValue());
            }
    }

    @Override
    public boolean hasTags() {
        return !properties.isEmpty();
    }

    @Override
    public String getTag(String name) {
        return (String) properties.get(name);
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

        for (String val : values) {
            if (val.equals(osmValue))
                return true;
        }
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
        for (String key : keyList) {
            if (values.contains(properties.get(key)))
                return true;
        }
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
