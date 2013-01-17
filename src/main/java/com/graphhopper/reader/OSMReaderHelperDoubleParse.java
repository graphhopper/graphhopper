/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.PointList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongIntHashMap;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This helper requires less memory but parses the osm file twice. After the
 * first parse process it knows which nodes are involved in ways and only
 * memorizes the lat,lon of those node. Only tower nodes are kept directly in
 * the graph, pillar nodes (nodes with degree of exactly 2) are just stored as
 * geometry for an edge for later usage e.g. in a UI.
 *
 * @author Peter Karich
 */
public class OSMReaderHelperDoubleParse extends OSMReaderHelper {

    private static final int EMPTY = -1;
    private static final int PILLAR_NODE = 1;
    private static final int TOWER_NODE = -2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private TLongIntHashMap osmIdToIndexMap;
    // very slow: private SparseLongLongArray osmIdToIndexMap;
    // not applicable as ways introduces the nodes in 'wrong' order: private OSMIDSegmentedMap
    private int towerId = 0;
    private int pillarId = 0;
    private final TLongArrayList tmpLocs = new TLongArrayList(10);
    // remember how many times a node was used to identify tower nodes
    private DataAccess pillarLats, pillarLons;
    private final Directory dir;

    public OSMReaderHelperDoubleParse(GraphStorage storage, int expectedNodes) {
        super(storage, expectedNodes);
        dir = storage.directory();
        pillarLats = dir.findCreate("tmpLatitudes");
        pillarLons = dir.findCreate("tmpLongitudes");
        osmIdToIndexMap = new TLongIntHashMap(expectedNodes, 1.4f, -1L, EMPTY);
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        int nodeType = osmIdToIndexMap.get(osmId);
        if (nodeType == EMPTY)
            return false;

        if (nodeType == TOWER_NODE) {
            addTowerNode(osmId, lat, lon);
        } else if (nodeType == PILLAR_NODE) {
            int tmp = (pillarId + 1) * 4;
            pillarLats.ensureCapacity(tmp);
            pillarLats.setInt(pillarId, Helper.degreeToInt(lat));
            pillarLons.ensureCapacity(tmp);
            pillarLons.setInt(pillarId, Helper.degreeToInt(lon));
            osmIdToIndexMap.put(osmId, pillarId + 3);
            pillarId++;
        }
        return true;
    }

    private int addTowerNode(long osmId, double lat, double lon) {
        g.setNode(towerId, lat, lon);
        int id = -(towerId + 3);
        osmIdToIndexMap.put(osmId, id);
        towerId++;
        return id;
    }

    @Override
    public int expectedNodes() {
        return osmIdToIndexMap.size();
    }

    @Override
    public int addEdge(TLongList nodes, int flags) {
        PointList pointList = new PointList(nodes.size());
        int successfullyAdded = 0;
        int firstIndex = -1;
        int lastIndex = nodes.size() - 1;
        for (int i = 0; i < nodes.size(); i++) {
            long osmId = nodes.get(i);
            int tmpNode = osmIdToIndexMap.get(osmId);
            if (tmpNode == EMPTY)
                continue;
            // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
            if (tmpNode == TOWER_NODE)
                continue;
            if (tmpNode == PILLAR_NODE) {
                // add part of the way which are in bounds, but force the last 
                // node to be a tower node
                if (i == lastIndex && pointList.size() > 1) {
                    tmpNode = osmIdToIndexMap.get(nodes.get(i - 1));
                    // force creating tower node from pillar node
                    tmpNode = handlePillarNode(tmpNode, osmId, pointList, true);
                    tmpNode = -tmpNode - 3;
                    successfullyAdded += addEdge(firstIndex, tmpNode, pointList, flags);
                }
                continue;
            }

            if (tmpNode > EMPTY) {
                // PILLAR node, but convert to towerNode if end-standing
                tmpNode = handlePillarNode(tmpNode, osmId, pointList, i == 0 || i == lastIndex);
            }

            if (tmpNode < EMPTY) {
                // TOWER node
                tmpNode = -tmpNode - 3;
                pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                if (firstIndex >= 0) {
                    successfullyAdded += addEdge(firstIndex, tmpNode, pointList, flags);
                    pointList.clear();
                    pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                }
                firstIndex = tmpNode;
            }
        }
        return successfullyAdded;
    }

    /**
     * @return if power node
     */
    private int handlePillarNode(int tmpNode, long osmId, PointList pointList, boolean towerNode) {
        tmpNode = tmpNode - 3;
        double tmpLatInt = Helper.intToDegree(pillarLats.getInt(tmpNode));
        double tmpLonInt = Helper.intToDegree(pillarLons.getInt(tmpNode));
        if (tmpLatInt < 0 || tmpLonInt < 0)
            throw new AssertionError("Conversation pillarNode to towerNode already happended!? "
                    + "osmId:" + osmId + " pillarIndex:" + tmpNode);

        if (towerNode) {
            // convert pillarNode type to towerNode
            pillarLons.setInt(tmpNode, -1);
            pillarLats.setInt(tmpNode, -1);
            tmpNode = addTowerNode(osmId, tmpLatInt, tmpLonInt);
        } else
            pointList.add(tmpLatInt, tmpLonInt);

        return tmpNode;
    }

    @Override
    void startWayProcessing() {
        LoggerFactory.getLogger(getClass()).info("finished node processing. osmIdMap:" + osmIdToIndexMap.size() * 16f / Helper.MB
                + ", " + Helper.getMemInfo());
    }

    @Override
    void cleanup() {
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        pillarLons = null;
        pillarLats = null;
        osmIdToIndexMap = null;
    }

    private void setHasHighways(long osmId) {
        int tmpIndex = osmIdToIndexMap.get(osmId);
        if (tmpIndex == EMPTY) {
            // unused osmId
            osmIdToIndexMap.put(osmId, PILLAR_NODE);
        } else if (tmpIndex > EMPTY) {
            // mark node as tower node as it occured at least twice times
            osmIdToIndexMap.put(osmId, TOWER_NODE);
        } else {
            // tmpIndex is already negative (already tower node)
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways.
     * This allows a more compact graph data structure.
     */
    @Override
    public void preProcess(InputStream osmXml) {
        pillarLats.createNew(Math.max(expectedNodes / 50, 100));
        pillarLons.createNew(Math.max(expectedNodes / 50, 100));
        if (osmXml == null)
            throw new AssertionError("Stream cannot be empty");

        Map<String, Object> empty = new HashMap<String, Object>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(osmXml, "UTF-8");
            int tmpCounter = 1;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next(), tmpCounter++) {
                if (tmpCounter % 20000000 == 0)
                    logger.info(tmpCounter + " (preprocess) " + Helper.getMemInfo());

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("way".equals(sReader.getLocalName())) {
                            boolean isHighway = parseWay(tmpLocs, empty, sReader);
                            if (isHighway && tmpLocs.size() > 1) {
                                int s = tmpLocs.size();
                                for (int index = 0; index < s; index++) {
                                    setHasHighways(tmpLocs.get(index));
                                }
                            }
                        }
                        break;
                }
            }
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }

    boolean parseWay(TLongArrayList tmpLocs, Map<String, Object> properties, XMLStreamReader sReader)
            throws XMLStreamException {
        return true;
    }
}