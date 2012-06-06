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

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MMapGraph;
import de.jetsli.graph.util.MyIteratorable;
import gnu.trove.map.hash.TIntIntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMyGraphStorage implements Storage {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MMapGraph g;
    private TIntIntHashMap osmIdToIndexMap;

    public MMyGraphStorage(int size) {
        this(null, size);
    }

    public MMyGraphStorage(String file, int size) {
        g = new MMapGraph(file, size);
        osmIdToIndexMap = new TIntIntHashMap(Math.round(size * 1.4f), 1.5f, -1, -1);
    }

    @Override
    public Storage init(boolean forceCreate) throws Exception {
        g.init(forceCreate);
        return this;
    }

    @Override
    public boolean addNode(int osmId, float lat, float lon) {
        int internalId = g.addLocation(lat, lon);
//        if (internalId > 9936 && internalId < 9946)
//            System.out.println("id:" + internalId + " osm:" + osmId);
        osmIdToIndexMap.put(osmId, internalId);
        return true;
    }
    int counter = 0;

    @Override
    public boolean addEdge(int nodeIdFrom, int nodeIdTo, boolean reverse, CalcDistance callback) {
        int fromIndex = osmIdToIndexMap.get(nodeIdFrom);
        int toIndex = osmIdToIndexMap.get(nodeIdTo);
        if (fromIndex == osmIdToIndexMap.getNoEntryValue() || toIndex == osmIdToIndexMap.getNoEntryValue())
            return false;

        try {
            float laf = g.getLatitude(fromIndex);
            float lof = g.getLongitude(fromIndex);
            float lat = g.getLatitude(toIndex);
            float lot = g.getLongitude(toIndex);
            float dist = (float) callback.calcDistKm(laf, lof, lat, lot);
            if (dist <= 0) {
                counter++;
                if (counter % 10000 == 0)
                    logger.info(counter + " - distances negative or zero. E.g. " + fromIndex + " (" + laf + ", " + lof + ")->"
                            + toIndex + "(" + lat + ", " + lot + ") :" + dist);
                return false;
            }
            g.edge(fromIndex, toIndex, dist, reverse);
            return true;
        } catch (Exception ex) {
            logger.error("Problem with " + fromIndex + "->" + toIndex + " osm:" + nodeIdFrom + "->" + nodeIdTo, ex);
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        g.flush();
        // g.close();
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
        return g.getLocations();
    }
}
