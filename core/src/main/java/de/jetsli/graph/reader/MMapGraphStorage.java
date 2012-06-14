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
package de.jetsli.graph.reader;

import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MMapGraph;
import de.jetsli.graph.util.StopWatch;
import gnu.trove.map.hash.TIntIntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraphStorage implements Storage {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MMapGraph g;
    private TIntIntHashMap osmIdToIndexMap;
    private final String file;

    public MMapGraphStorage(String file, int size) {
        this.file = file;
        osmIdToIndexMap = new TIntIntHashMap(size, 1.4f, -1, -1);
    }

    @Override
    public boolean loadExisting() {
        g = new MMapGraph(file, -1);
        return g.loadExisting();
    }

    @Override
    public void createNew() {
        g = new MMapGraph(file, osmIdToIndexMap.size());
        g.createNew();
    }

    @Override
    public boolean addNode(int osmId, double lat, double lon) {
        int internalId = g.addLocation(lat, lon);
        osmIdToIndexMap.put(osmId, internalId);
        return true;
    }
    int counter = 0;
    StopWatch sw1 = new StopWatch();
    StopWatch sw2 = new StopWatch();

    @Override
    public boolean addEdge(int nodeIdFrom, int nodeIdTo, boolean reverse, CalcDistance callback) {
        int fromIndex = osmIdToIndexMap.get(nodeIdFrom);
        if (fromIndex == -10) {
            logger.warn("fromIndex is unresolved:" + nodeIdFrom + " to was:" + nodeIdTo);
            return false;
        }
        int toIndex = osmIdToIndexMap.get(nodeIdTo);
        if (toIndex == -10) {
            logger.warn("toIndex is unresolved:" + nodeIdTo + " from was:" + nodeIdFrom);
            return false;
        }

        if (fromIndex == osmIdToIndexMap.getNoEntryValue() || toIndex == osmIdToIndexMap.getNoEntryValue())
            return false;

        try {
            sw2.start();
            double laf = g.getLatitude(fromIndex);
            double lof = g.getLongitude(fromIndex);
            double lat = g.getLatitude(toIndex);
            double lot = g.getLongitude(toIndex);
            sw2.stop();

            sw1.start();
            double dist = callback.calcDistKm(laf, lof, lat, lot);
            sw1.stop();
            if (dist <= 0) {
                logger.info(counter + " - distances negative or zero. " + fromIndex + " (" + laf + ", " + lof + ")->"
                        + toIndex + "(" + lat + ", " + lot + ") :" + dist);
                return false;
            }

            g.edge(fromIndex, toIndex, dist, reverse);
            counter++;
            if (counter % 10000 == 0)
                logger.info(counter + " g.edge:" + sw1.toString() + " sw2:" + sw2.toString());
            return true;
        } catch (Exception ex) {
            logger.error("Problem with " + fromIndex + "->" + toIndex + " osm:" + nodeIdFrom + "->" + nodeIdTo, ex);
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        g.flush();
    }

    Graph getGraph() {
        return g;
    }

    @Override public void stats() {
        g.stats();
    }

    @Override
    public void flush() {
        g.flush();
    }

    @Override
    public int getNodes() {
        return osmIdToIndexMap.size();
    }

    @Override
    public void setHasHighways(int osmId, boolean isHighway) {
        if (isHighway)
            osmIdToIndexMap.put(osmId, -10);
        else
            osmIdToIndexMap.remove(osmId);
    }

    @Override
    public boolean hasHighways(int osmId) {
        return osmIdToIndexMap.get(osmId) == -10;
    }
}
