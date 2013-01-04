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

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public abstract class OSMReaderHelper {

    protected static final int FILLED = -2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected int counter = 0;
    protected int zeroCounter = 0;
    protected final Graph g;
    protected final int expectedNodes;

    public OSMReaderHelper(Graph g, int expectedNodes) {
        this.g = g;
        this.expectedNodes = expectedNodes;
    }

    public int getExpectedNodes() {
        return expectedNodes;
    }

    public boolean addNode(long osmId, double lat, double lon) {
        return true;
    }

    public abstract boolean addEdge(long nodeIdFrom, long nodeIdTo, int flags, DistanceCalc callback);

    public void preProcess(InputStream osmXml) {
    }

    public boolean addEdge(double laf, double lof, double lat, double lot,
            int fromIndex, int toIndex, int flags, DistanceCalc callback) {
        double dist = callback.calcDist(laf, lof, lat, lot);
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

        EdgeIterator iter = GraphUtility.until(g.getOutgoing(fromIndex), toIndex);
        if (!iter.isEmpty()) {
            if (flags == iter.flags() && dist > iter.distance()) {
                // silently skip if exactly the same way and the new one would be longer
//                    return true;
            }
//                else logger.warn("longer edge already exists " + fromIndex + "->" + toIndex + "!? "
//                            + "existing: " + iter.distance() + "|" + BitUtil.toBitString(iter.flags(), 8)
//                            + " new:" + dist + "|" + BitUtil.toBitString(flags, 8));
        }

        g.edge(fromIndex, toIndex, dist, flags);
        return true;
    }

    public String getInfo() {
        return "Found " + zeroCounter + " zero and " + counter + " negative distances.";
    }

    public String getStorageInfo(GraphStorage storage) {
        return storage.getClass().getSimpleName() + "|" + storage.getDirectory().getClass().getSimpleName()
                + "|" + storage.getVersion();
    }

    public void cleanup() {
    }

    public void startWayProcessing() {
    }
}
