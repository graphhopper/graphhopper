/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Helper7;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
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
 * This helper requires less memory but parses the osm file twice. After the first parse process it
 * knows which nodes are involved in ways and only memorizes the lat,lon of those node. Afterwards
 * the deletion process is also not that memory intensive.
 *
 * @author Peter Karich
 */
public class OSMReaderHelperDoubleParse extends OSMReaderHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private TIntIntHashMap osmIdToIndexMap;
    // very slow: private SparseLongLongArray osmIdToIndexMap;
    // not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
    private int internalId = 0;
    private final TLongArrayList tmpLocs = new TLongArrayList(10);
    // remember how many times a node was used to identify tower nodes
    private DataAccess indexToCount;
    private final Directory dir;

    public OSMReaderHelperDoubleParse(GraphStorage storage, int expectedNodes) {
        super(storage, expectedNodes);
        dir = storage.getDirectory();
        indexToCount = dir.findCreate("tmpOSMReaderMap");
        indexToCount.createNew(expectedNodes);
        osmIdToIndexMap = new TIntIntHashMap(expectedNodes, 1.4f, -1, -1);
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        int count = osmIdToIndexMap.get((int) osmId);
        if (count > FILLED)
            return false;

        g.setNode(internalId, lat, lon);
        indexToCount.ensureCapacity(4 * (internalId + 1));
        indexToCount.setInt(internalId, 1 - (count - FILLED));
        osmIdToIndexMap.put((int) osmId, internalId);
        internalId++;
        return true;
    }

    @Override
    public int getExpectedNodes() {
        return osmIdToIndexMap.size();
    }

    @Override
    public boolean addEdge(long nodeIdFrom, long nodeIdTo, int flags, DistanceCalc callback) {
        int fromIndex = (int) osmIdToIndexMap.get((int) nodeIdFrom);
        int toIndex = (int) osmIdToIndexMap.get((int) nodeIdTo);
        if (fromIndex < 0 || toIndex < 0)
            return false;

        try {
            double laf = g.getLatitude(fromIndex);
            double lof = g.getLongitude(fromIndex);
            double lat = g.getLatitude(toIndex);
            double lot = g.getLongitude(toIndex);
            return addEdge(laf, lof, lat, lot, fromIndex, toIndex, flags, callback);
        } catch (Exception ex) {
            throw new RuntimeException("Problem to add edge! with node " + fromIndex + "->" + toIndex + " osm:" + nodeIdFrom + "->" + nodeIdTo, ex);
        }
    }

    @Override
    public void startWayProcessing() {
        LoggerFactory.getLogger(getClass()).info("finished node processing. osmIdMap:" + osmIdToIndexMap.size() * 16f / Helper.MB
                + ", " + Helper.getMemInfo());
    }

    @Override
    public void cleanup() {
        osmIdToIndexMap = null;
        dir.remove(indexToCount);
        indexToCount = null;
    }

    public void setHasHighways(long osmId) {
        int ret = osmIdToIndexMap.put((int) osmId, FILLED);
        if (ret <= FILLED)
            osmIdToIndexMap.put((int) osmId, ret - 1);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    @Override
    public void preProcess(InputStream osmXml) {
        if (osmXml == null)
            throw new IllegalStateException("Stream cannot be empty");

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