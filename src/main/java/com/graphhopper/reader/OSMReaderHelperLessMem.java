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

import com.graphhopper.storage.GraphStorage;
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
public class OSMReaderHelperLessMem extends OSMReaderHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected TIntIntHashMap osmIdToIndexMap;
    private int internalId = 0;
    private TLongArrayList tmpLocs = new TLongArrayList(10);

    public OSMReaderHelperLessMem(GraphStorage storage, int expectedNodes) {
        super(storage, expectedNodes);
        osmIdToIndexMap = new TIntIntHashMap(expectedNodes, 1.4f, -1, -1);
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        if (!hasHighways(osmId))
            return false;

        g.setNode(internalId, lat, lon);
        osmIdToIndexMap.put((int) osmId, internalId);
        internalId++;
        return true;
    }

    @Override
    public boolean addEdge(long nodeIdFrom, long nodeIdTo, int flags, DistanceCalc callback) {
        int fromIndex = osmIdToIndexMap.get((int) nodeIdFrom);
        if (fromIndex == FILLED) {
            logger.warn("fromIndex is unresolved:" + nodeIdFrom + " to was:" + nodeIdTo);
            return false;
        }
        int toIndex = osmIdToIndexMap.get((int) nodeIdTo);
        if (toIndex == FILLED) {
            logger.warn("toIndex is unresolved:" + nodeIdTo + " from was:" + nodeIdFrom);
            return false;
        }

        if (fromIndex == osmIdToIndexMap.getNoEntryValue() || toIndex == osmIdToIndexMap.getNoEntryValue())
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
        LoggerFactory.getLogger(getClass()).info("finished preprocessing. osmIdMap:" + osmIdToIndexMap.capacity() * 9f / Helper.MB 
                + ", " + Helper.getMemInfo());
    }

    @Override
    public void freeNodeMap() {
        osmIdToIndexMap = null;
    }

    public void setHasHighways(long osmId, boolean isHighway) {
        if (isHighway)
            osmIdToIndexMap.put((int) osmId, FILLED);
        else
            osmIdToIndexMap.remove((int) osmId);
    }

    public boolean hasHighways(long osmId) {
        return osmIdToIndexMap.get((int) osmId) == FILLED;
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
            int tmpCounter = 0;
            for (int event = sReader.next(); event != XMLStreamConstants.END_DOCUMENT;
                    event = sReader.next()) {
                if (++tmpCounter % 10000000 == 0)
                    logger.info(tmpCounter + Helper.getMemInfo());

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("way".equals(sReader.getLocalName())) {
                            boolean isHighway = parseWay(tmpLocs, empty, sReader);
                            if (isHighway && tmpLocs.size() > 1) {
                                int s = tmpLocs.size();
                                for (int index = 0; index < s; index++) {
                                    setHasHighways(tmpLocs.get(index), true);
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