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

import gnu.trove.map.TDoubleLongMap;
import gnu.trove.map.TDoubleObjectMap;
import gnu.trove.map.TLongObjectMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.Way;

/**
 * Represents an OSM Way
 * <p/>
 * 
 * @author Nop
 */
public class OsDpnWay extends OsDpnElement implements Way {
    private static final long WAY_NODE_PREFIX_MOD = 100000000000000000L;
    protected final List<String> nodes = new ArrayList<String>(5);
    private String endNode;
    protected String startCoord;
    protected String endCoord;
    private String[] wayCoords;
    private static final Logger logger = LoggerFactory
            .getLogger(OsDpnWay.class);

    /**
     * Constructor for XML Parser
     * 
     * @throws TransformException
     * @throws FactoryException
     * @throws MismatchedDimensionException
     */
    public static OsDpnWay create(String idStr, XMLStreamReader parser)
            throws XMLStreamException, MismatchedDimensionException,
            FactoryException, TransformException {
        System.out.println("OsDpnWay.create()");
        OsDpnWay way = new OsDpnWay(idStr);
        parser.nextTag();
        way.readTags(parser);
        way.setTag("highway", "motorway");
        logger.info(way.toString());
        return way;
    }

    public OsDpnWay(String id) {
        super(id, WAY);
    }

    public List<String> getNodes() {
        return nodes;
    }

    @Override
    protected int handlePhysicalLevel(XMLStreamReader parser) throws XMLStreamException
    {
        String text = parser.getElementText();
        if("Below Surface Level Tunnel".equals(text)) {
            setTag("tunnel", "yes");
        } else if("Above Surface Level On Structure".equals(text)) {
            setTag("bridge", "yes");
        }
        return super.handlePhysicalLevel(parser);
    }

    @Override
    protected void parseCoords(String lineDefinition) {
        String[] lineSegments = lineDefinition.split(" ");
        wayCoords = Arrays
                .copyOfRange(lineSegments, 1, lineSegments.length - 1);
        logger.info(toString() + " "
                + ((wayCoords.length == 0) ? "0" : wayCoords[0]));
    }

    @Override
    protected void parseCoords(int dimensions, String lineDefinition) {
        String[] lineSegments = lineDefinition.split(" ");
        wayCoords = new String[lineSegments.length / dimensions];
        StringBuilder curString = null;
        for (int i = 0; i < lineSegments.length; i++) {
            String string = lineSegments[i];
            switch (i % dimensions) {
                case 0: {
                    int coordNumber = i / dimensions;
                    if (coordNumber > 0) {
                        wayCoords[coordNumber - 1] = curString.toString();
                    }
                    curString = new StringBuilder();
                    curString.append(string);
                    break;
                }

                case 1:
                case 2: {
                    curString.append(',');
                    curString.append(string);
                }
            }
        }
        wayCoords[wayCoords.length - 1] = curString.toString();
        addWayNodes();
        nodes.add(endNode);
        logger.info(toString() + " "
                + ((wayCoords.length == 0) ? "0" : wayCoords[0]));
    }

    @Override
    protected void parseNetworkMember(String elementText) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void addNode(String nodeId) {
        String idStr = nodeId.substring(1);
        if (0 == nodes.size()) {
            nodes.add(idStr);
        } else {
            endNode = idStr;
        }
    }

    protected void addWayNodes() {
        for (int i = 1; i <= wayCoords.length; i++) {
            long idPrefix = i;
            String extraId = idPrefix + getId();
            nodes.add(extraId);
        }
    }

    @Override
    protected void addDirectedLink(String nodeId, String orientation) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new OsDpnNode for each wayCoord. This also Looks for direction
     * flags in edgeIdToXToYToNodeFlagsMap for the wayId, x, y combination. If
     * it exists then set the node tag TAG_KEY_NOENTRY_ORIENTATION to true and
     * the TAG_KEY_ONEWAY_ORIENTATION node tag to -1 for one direction and true
     * for the other.
     * 
     * @param edgeIdToXToYToNodeFlagsMap
     * @return
     * @throws TransformException
     * @throws FactoryException
     * @throws MismatchedDimensionException
     */
    public List<OsDpnNode> evaluateWayNodes(
            TLongObjectMap<TDoubleObjectMap<TDoubleLongMap>> edgeIdToXToYToNodeFlagsMap)
            throws MismatchedDimensionException, FactoryException,
            TransformException {
        List<OsDpnNode> wayNodes = new ArrayList<OsDpnNode>();

        for (int i = 0; i < wayCoords.length; i++) {
            String wayCoord = wayCoords[i];

            long idPrefix = (i + 1) * WAY_NODE_PREFIX_MOD;
            String id = idPrefix + getId();
            OsDpnNode wayNode = new OsDpnNode(id);
            wayNode.parseCoords(wayCoord);

            logger.info("Node " + getId() + " coords: " + wayCoord + " tags: ");
            for (String tagKey : wayNode.getTags().keySet()) {
                logger.info("\t " + tagKey + " : " + wayNode.getTag(tagKey));
            }

            wayNodes.add(wayNode);
        }
        return wayNodes;
    }

    /**
     * Memory management method. Once a way is processed the stored string
     * coordinates are no longer required so set them to null so they can be
     * garbage collected
     */
    public void clearStoredCoords() {
        wayCoords = null;
        startCoord = null;
        endCoord = null;
    }

    public String[] getWayCoords() {
        return wayCoords;
    }

    public String getStartCoord() {
        return startCoord;
    }

    public String getEndCoord() {
        return endCoord;
    }

    protected void parseCoordinateString(String elementText,
            String elementSeparator) {
        throw new UnsupportedOperationException();

    }

}
