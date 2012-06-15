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
import gnu.trove.map.hash.TIntIntHashMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraphStorage implements Storage {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private File tmp;
    private RandomAccessFile tmpRA;
    private TIntIntHashMap osm2idMap;
    private MMapGraph g;
    private int size;
    private final String file;
    private int counter = 0;

    public MMapGraphStorage(String file, int size) {
        this.file = file;
        this.size = size;
    }

    @Override
    public boolean loadExisting() {
        g = new MMapGraph(file, -1);
        return g.loadExisting();
    }

    @Override
    public void createNew() {
        g = new MMapGraph(file, size);
        g.createNew();

        // contains mapping from OSM-ID to negative file position pointers OR positive graph node id!
        osm2idMap = new TIntIntHashMap(size, 1.4f, -1, -1);

        // store lat,lon for later usage in processWay into a separate file to avoid reparsing
        // or storing this in an in-memory structure
        // => it is a good idea to put this file on a different disc than the OSM file or mmap file!
        try {
            tmp = File.createTempFile("graph", "osmimport");
            logger.info("using temp file " + tmp);
            tmpRA = new RandomAccessFile(tmp, "rw");
            // write dummy int so that a filepointer of 0-3 won't happen => reserve >0 for "node ids" and -1 for "not a value"
            tmpRA.writeInt(0);
        } catch (IOException ex) {
            throw new RuntimeException("Cannot create temp file " + tmp, ex);
        }
    }

    @Override
    public boolean addNode(int osmId, double lat, double lon) {
        try {
            int line = (int) tmpRA.getFilePointer();
            tmpRA.writeFloat((float) lat);
            tmpRA.writeFloat((float) lon);
            osm2idMap.put(osmId, -line);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't write lat,lon " + osmId + " " + lat + "," + lon, ex);
        }
    }

    @Override
    public boolean addEdge(int osmIdFrom, int osmIdTo, boolean reverse, CalcDistance callback) {
        double latFrom;
        double lonFrom;
        double latTo;
        double lonTo;
        int idFrom;
        int idTo;
        try {
            idFrom = osm2idMap.get(osmIdFrom);
            if (idFrom == osm2idMap.getNoEntryValue())
                return false;

            if (idFrom < 0) {
                tmpRA.seek(-idFrom);
                latFrom = tmpRA.readFloat();
                lonFrom = tmpRA.readFloat();
                idFrom = g.addLocation(latFrom, lonFrom);
                osm2idMap.put(osmIdFrom, idFrom);
            } else {
                latFrom = g.getLatitude(idFrom);
                lonFrom = g.getLongitude(idFrom);
            }

            idTo = osm2idMap.get(osmIdTo);
            if (idTo == osm2idMap.getNoEntryValue())
                return false;

            if (idTo < 0) {
                tmpRA.seek(-idTo);
                latTo = tmpRA.readFloat();
                lonTo = tmpRA.readFloat();
                idTo = g.addLocation(latTo, lonTo);
                osm2idMap.put(osmIdTo, idTo);
            } else {
                latTo = g.getLatitude(idTo);
                lonTo = g.getLongitude(idTo);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Cannot read point " + osmIdFrom + " " + osmIdTo, ex);
        }

        double dist = callback.calcDistKm(latFrom, lonFrom, latTo, lonTo);
        if (dist <= 0) {
            logger.info(counter + " - distances negative or zero. " + osmIdFrom + " (" + latFrom + ", " + lonFrom + ")->"
                    + osmIdTo + "(" + latTo + ", " + lonTo + ") :" + dist);
            return false;
        }
        g.edge(idFrom, idTo, dist, reverse);
        counter++;
        return true;
    }

    @Override
    public void close() throws Exception {
        if (tmp != null) {
            tmpRA.close();
            tmp.delete();
        }
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
}
