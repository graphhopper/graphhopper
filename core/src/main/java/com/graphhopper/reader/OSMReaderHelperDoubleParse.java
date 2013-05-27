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

import static com.graphhopper.util.Helper.nf;
import gnu.trove.list.TLongList;

import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TurnCostsEntry;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.GraphStorageTurnCosts;
import com.graphhopper.storage.GraphTurnCosts;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import com.graphhopper.util.PointList;

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
    /* we cannot hold a map for osmId->edgeId, because different edgeIds can have the same osmId
      instead we hold for each edgeId the corresponding OSM id and compare it later when we need it
      @see OSMRestrictionRelation#getAsEntries(...) */ 
    private DataAccess osmIDsOfEdges;
    
    private int towerId = 0;
    private int pillarId = 0;
    // remember how many times a node was used to identify tower nodes
    private DataAccess pillarLats, pillarLons;
    private final Directory dir;
    private EdgeFilter edgeOutFilter;
    private EdgeFilter edgeInFilter;

    public OSMReaderHelperDoubleParse(GraphStorage storage, long expectedNodes) {
        super(storage, expectedNodes);
        dir = storage.directory();
        pillarLats = dir.findCreate("tmpLatitudes");
        pillarLons = dir.findCreate("tmpLongitudes");
        osmIDsOfEdges = dir.findCreate("tmpOsmidsEdges");
        // we need those to build the node costs tables
        // here we can use any vehicleDecoder, because we just care about
        // forward/backward direction of the edges
        edgeOutFilter = new DefaultEdgeFilter(new CarFlagEncoder(), false, true);
        edgeInFilter = new DefaultEdgeFilter(new CarFlagEncoder(), true, false);

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
    public int addEdge(TLongList osmIds, int flags, long edgeOsmid) {
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
                            successfullyAdded += addEdge(firstNode, tmpNode, pointList, flags, edgeOsmid);
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
                        successfullyAdded += addEdge(firstNode, tmpNode, pointList, flags, edgeOsmid);
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

    @Override
    void storeEdgeOSMId(int edgeId, long osmId) {
        long ptr = (long) edgeId * 2;
        osmIDsOfEdges.setInt(ptr, Helper.longToIntLeft(osmId));
        osmIDsOfEdges.setInt(ptr + 1, Helper.longToIntRight(osmId));
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

    public void processRelations(XMLStreamReader sReader) throws XMLStreamException {
        if (g instanceof GraphTurnCosts) {
            OSMRestrictionRelation restriction = parseRestriction(sReader);
            if (restriction != null && restriction.isValid()) {
                    for (TurnCostsEntry entry : restriction.getAsEntries((GraphTurnCosts) g,
                            edgeOutFilter, edgeInFilter, osmIDsOfEdges)) {
                        ((GraphTurnCosts) g).turnCosts(entry.node(), entry.edgeFrom(),
                                entry.edgeTo(), entry.flags());
                    }    
                
            }
        }
    }

    public OSMRestrictionRelation parseRestriction(XMLStreamReader sReader)
            throws XMLStreamException {
        final OSMRestrictionRelation restriction = new OSMRestrictionRelation();
        
        boolean restrictionFound = false;
        
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT; tmpE = sReader
                .nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("member".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    String role = sReader.getAttributeValue(null, "role");
                    String type = sReader.getAttributeValue(null, "type");
                    if ("way".equals(type) && ref != null) {
                        if ("from".equals(role)) {
                            restriction.fromOsm = Long.valueOf(ref);
                        } else if ("to".equals(role)) {
                            restriction.toOsm = Long.valueOf(ref);
                        }
                    } else if ("node".equals(type) && ref != null && "via".equals(role)) {
                        int tmpNode = nodeOsmIdToIndexMap.get(Long.parseLong(ref));
                        if (tmpNode < TOWER_NODE) {
                            tmpNode = -tmpNode - 3;
                            restriction.via = tmpNode;
                        }
                    }
                } else if ("tag".equals(sReader.getLocalName())) {
                    String tagKey = sReader.getAttributeValue(null, "k");
                    String tagValue = sReader.getAttributeValue(null, "v");
                    if (!Helper.isEmpty(tagKey) && !Helper.isEmpty(tagValue)) {
                        if ("type".equals(tagKey) && "restriction".equals(tagValue)) {
                            restrictionFound = true;
                        }
                        if ("restriction".equals(tagKey)) {
                            restriction.restriction = OSMRestrictionRelation.getRestrictionType(tagValue);
                        }
                    }
                }
                sReader.next();
            }
        }
        return (restrictionFound) ? restriction : null;
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
    void startRelationsProcessing() {
        printInfo("way");
    }

    void printRelationInfo() {
        if(g instanceof GraphStorageTurnCosts){
            LoggerFactory.getLogger(getClass()).info("finished relation processing."
                    + " turn cost entries: "+ ((GraphStorageTurnCosts)g).entries()+", "
                    + Helper.memInfo());    
        }
        
    }

    @Override
    void finishedReading() {
        // todo: is this necessary before removing it?
        nodeOsmIdToIndexMap.optimize();
        printRelationInfo();
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        dir.remove(osmIDsOfEdges);
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
        osmIDsOfEdges.create(Math.max(expectedNodes / 50, 100));
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
