/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.CalcDistance;
import gnu.trove.map.hash.TIntIntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class DefaultStorage implements Storage {

    protected static final int FILLED = -2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected Graph g;
    protected TIntIntHashMap osmIdToIndexMap;
    private int internalId = 0;

    public DefaultStorage(int expectedNodes) {
        osmIdToIndexMap = new TIntIntHashMap(expectedNodes, 1.4f, -1, -1);
    }

    @Override
    public boolean loadExisting() {
        return false;
    }

    @Override
    public void createNew() {
    }

    @Override
    public boolean addNode(int osmId, double lat, double lon) {
        g.setNode(internalId, lat, lon);
        osmIdToIndexMap.put(osmId, internalId);
        internalId++;
        return true;
    }
    int counter = 0;
    int zeroCounter = 0;

    @Override
    public boolean addEdge(int nodeIdFrom, int nodeIdTo, int flags, CalcDistance callback) {
        int fromIndex = osmIdToIndexMap.get(nodeIdFrom);
        if (fromIndex == FILLED) {
            logger.warn("fromIndex is unresolved:" + nodeIdFrom + " to was:" + nodeIdTo);
            return false;
        }
        int toIndex = osmIdToIndexMap.get(nodeIdTo);
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
            double dist = callback.calcDistKm(laf, lof, lat, lot);
            if (dist == 0) {
                // As investigation shows often two paths should have crossed via one identical point 
                // but end up in two very close points. later this will be removed/fixed while 
                // removing short edges where one node is of degree 2
                zeroCounter++;
                dist = 0.0001;
            } else if (dist < 0) {
                logger.info(counter + " - distances negative. " + fromIndex + " (" + laf + ", " + lof + ")->"
                        + toIndex + "(" + lat + ", " + lot + ") :" + dist);
                return false;
            }

            g.edge(fromIndex, toIndex, dist, flags);
            return true;
        } catch (Exception ex) {
            throw new RuntimeException("Problem to add edge! with node " + fromIndex + "->" + toIndex + " osm:" + nodeIdFrom + "->" + nodeIdTo, ex);
        }
    }

    @Override
    public void close() {
        flush();
    }

    @Override public Graph getGraph() {
        return g;
    }

    @Override public void stats() {
    }

    @Override
    public void flush() {
        logger.info("Found " + zeroCounter + " zero and " + counter + " negative distances.");
        osmIdToIndexMap = null;
    }

    @Override
    public int getNodes() {
        return osmIdToIndexMap.size();
    }

    @Override
    public void setHasHighways(int osmId, boolean isHighway) {
        if (isHighway)
            osmIdToIndexMap.put(osmId, FILLED);
        else
            osmIdToIndexMap.remove(osmId);
    }

    @Override
    public boolean hasHighways(int osmId) {
        return osmIdToIndexMap.get(osmId) == FILLED;
    }
}
