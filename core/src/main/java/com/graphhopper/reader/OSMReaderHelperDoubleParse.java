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

import com.graphhopper.coll.LongIntMap;
import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.*;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.PointList;
import gnu.trove.list.TLongList;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This helper requires less memory but parses the osm file twice. After the
 * first parse process it knows which nodes are involved in ways and only
 * memorizes the lat,lon of those node. Only tower nodes (crossroads) are kept
 * directly in the graph, pillar nodes (nodes with degree of exactly 2) are just
 * stored as geometry for an edge for later usage e.g. in a UI.
 *
 * @author Peter Karich
 */
public class OSMReaderHelperDoubleParse extends OSMReaderHelper {

    private static final int EMPTY = -1;
    // pillar node is >= 3
    private static final int PILLAR_NODE = 1;
    // tower node is <= -3
    private static final int TOWER_NODE = -2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private LongIntMap nodeOsmIdToIndexMap;
    private int towerId = 0;
    private int pillarId = 0;
    // remember how many times a node was used to identify tower nodes
    private DataAccess pillarLats, pillarLons;
    private final Directory dir;

    public OSMReaderHelperDoubleParse(GraphStorage storage, long expectedNodes) {
        super(storage, expectedNodes);
        dir = storage.directory();
        pillarLats = dir.findCreate("tmpLatitudes");
        pillarLons = dir.findCreate("tmpLongitudes");

        // Using the correct Map<Long, Integer> is hard. We need a memory 
        // efficient and fast solution for big data sets!
        //
        // very slow: new SparseLongLongArray
        // only append and update possible (no unordered storage like with this doubleParse): new OSMIDMap
        // same here: not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
        // memory overhead due to open addressing and full rehash:
//        nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
        // smaller memory overhead for bigger data sets because of avoiding a "rehash"
        nodeOsmIdToIndexMap = new GHLongIntBTree(200);
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        int nodeType = nodeOsmIdToIndexMap.get(osmId);
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
            nodeOsmIdToIndexMap.put( osmId, pillarId + 3 );
            pillarId++;
        }
        return true;
    }

    private int addTowerNode(long osmId, double lat, double lon) {
        g.setNode(towerId, lat, lon);
        int id = -(towerId + 3);
        nodeOsmIdToIndexMap.put( osmId, id );
        towerId++;
        return id;
    }

    @Override
    public long foundNodes() {
        return nodeOsmIdToIndexMap.size();
    }

    @Override
    public int addEdge(TLongList osmIds, int flags) {
        PointList pointList = new PointList(osmIds.size());
        int successfullyAdded = 0;
        int firstNode = -1;
        int lastIndex = osmIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        try {
            for (int i = 0; i < osmIds.size(); i++) {
                long osmId = osmIds.get(i);
                int tmpNode = nodeOsmIdToIndexMap.get(osmId);
                if (tmpNode == EMPTY)
                    continue;
                // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
                if (tmpNode == TOWER_NODE)
                    continue;
                if (tmpNode == PILLAR_NODE) {
                    // In some cases no node information is saved for the specified osmId.
                    // ie. a way references a <node> which does not exist in the current file.
                    // => if the node before was a pillar node then convert into to tower node (as it is also end-standing).
                    if (!pointList.isEmpty() && lastInBoundsPillarNode > -TOWER_NODE) {
                        // transform the pillar node to a tower node
                        tmpNode = lastInBoundsPillarNode;
                        tmpNode = handlePillarNode(tmpNode, osmId, null, true);
                        tmpNode = -tmpNode - 3;
                        if (pointList.size() > 1 && firstNode >= 0) {
                            // TOWER node                        
                            successfullyAdded += addEdge(firstNode, tmpNode, pointList, flags);
                            pointList.clear();
                            pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                        }
                        firstNode = tmpNode;
                        lastInBoundsPillarNode = -1;
                    }
                    continue;
                }

                if (tmpNode <= -TOWER_NODE && tmpNode >= TOWER_NODE)
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmId);

                if (tmpNode > -TOWER_NODE) {
                    boolean convertToTowerNode = i == 0 || i == lastIndex;
                    if (!convertToTowerNode)
                        lastInBoundsPillarNode = tmpNode;

                    // PILLAR node, but convert to towerNode if end-standing
                    tmpNode = handlePillarNode(tmpNode, osmId, pointList, convertToTowerNode);
                }

                if (tmpNode < TOWER_NODE) {
                    // TOWER node
                    tmpNode = -tmpNode - 3;
                    pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                    if (firstNode >= 0) {
                        successfullyAdded += addEdge(firstNode, tmpNode, pointList, flags);
                        pointList.clear();
                        pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                    }
                    firstNode = tmpNode;
                }
            }
        } catch (RuntimeException ex) {            
            logger.error("Couldn't properly add edge with osm ids:" + osmIds, ex);
        }
        return successfullyAdded;
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode(int tmpNode, long osmId, PointList pointList, boolean convertToTowerNode) {
        tmpNode = tmpNode - 3;
        int intlat = pillarLats.getInt(tmpNode);
        int intlon = pillarLons.getInt(tmpNode);
        if (intlat == Integer.MAX_VALUE || intlon == Integer.MAX_VALUE)
            throw new RuntimeException("Conversation pillarNode to towerNode already happended!? "
                    + "osmId:" + osmId + " pillarIndex:" + tmpNode);

        double tmpLat = Helper.intToDegree(intlat);
        double tmpLon = Helper.intToDegree(intlon);

        if (convertToTowerNode) {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarLons.setInt(tmpNode, Integer.MAX_VALUE);
            pillarLats.setInt(tmpNode, Integer.MAX_VALUE);
            tmpNode = addTowerNode(osmId, tmpLat, tmpLon);
        } else
            pointList.add(tmpLat, tmpLon);

        return tmpNode;
    }

    private void printInfo(String str) {
        LoggerFactory.getLogger(getClass()).info("finished " + str + " processing."
                + " nodes: " + g.nodes() + ", osmIdMap.size:" + nodeOsmIdToIndexMap.size()
                + ", osmIdMap:" + nodeOsmIdToIndexMap.memoryUsage() + "MB"
                + ", osmIdMap.toString:" + nodeOsmIdToIndexMap + " "
                + Helper.memInfo());
    }

    @Override
    void startWayProcessing() {
        printInfo("node");
    }

    @Override
    void finishedReading() {
        // todo: is this necessary before removing it?
        nodeOsmIdToIndexMap.optimize();
        printInfo("way");
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        pillarLons = null;
        pillarLats = null;
        nodeOsmIdToIndexMap = null;
    }

    private void setHasHighways(long osmId) {
        int tmpIndex = nodeOsmIdToIndexMap.get(osmId);
        if (tmpIndex == EMPTY) {
            // osmId is used exactly once
            nodeOsmIdToIndexMap.put( osmId, PILLAR_NODE );
        } else if (tmpIndex > EMPTY) {
            // mark node as tower node as it occured at least twice times
            nodeOsmIdToIndexMap.put( osmId, TOWER_NODE );
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
        pillarLats.create(Math.max(expectedNodes / 50, 100));
        pillarLons.create(Math.max(expectedNodes / 50, 100));
        if (osmXml == null)
            throw new AssertionError("Stream cannot be empty");

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader sReader = null;
        try {
            sReader = factory.createXMLStreamReader(osmXml, "UTF-8");
            long tmpCounter = 1;
            boolean keepRunning = true;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT && keepRunning;
                    event = sReader.next(), tmpCounter++) {
                if (tmpCounter % 50000000 == 0)
                    logger.info(nf(tmpCounter) + " (preprocess), osmIdMap:"
                            + nf( nodeOsmIdToIndexMap.size()) + " (" + nodeOsmIdToIndexMap.memoryUsage() + "MB) "
                            + Helper.memInfo());

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("way".equals(sReader.getLocalName())) {
                            boolean valid = filterWay(sReader);
                            if (valid) {
                                int s = wayNodes.size();
                                for (int index = 0; index < s; index++) {
                                    setHasHighways(wayNodes.get(index));
                                }
                            }
                        }
                        // stop parsing when we hit the first relation. Works in all sorted planetfiles.
                        // Would not work in self-constructed, unsorted data
                        else if ("relation".equals(sReader.getLocalName()))
                            keepRunning = false;
                        break;
                }
            }
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally {
            Helper7.close(sReader);
        }
    }
}