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
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
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
    private DistanceCalc callback = new DistanceCalc();

    public OSMReaderHelper(Graph g, int expectedNodes) {
        this.g = g;
        this.expectedNodes = expectedNodes;
    }

    public void setCallback(DistanceCalc callback) {
        this.callback = callback;
    }

    public int getExpectedNodes() {
        return expectedNodes;
    }

    public void preProcess(InputStream osmXml) {
    }

    public boolean addNode(long osmId, double lat, double lon) {
        return true;
    }

    public abstract int addEdge(TLongList nodes, int flags);

    public int addEdge(TDoubleList latitudes, TDoubleList longitudes,
            TIntList allNodes, int flags) {
        int nodes = allNodes.size();
        if (latitudes.size() != nodes || longitudes.size() != nodes)
            throw new IllegalArgumentException("latitudes.size must be equals to longitudes.size and node list size " + nodes);

        double towerNodeDistance = 0;
        double prevLat = latitudes.get(0);
        double prevLon = longitudes.get(0);
        double lat;
        double lon;
        for (int i = 1; i < nodes; i++) {
            lat = latitudes.get(i);
            lon = longitudes.get(i);
            towerNodeDistance += callback.calcDist(prevLat, prevLon, lat, lon);
            prevLat = lat;
            prevLon = lon;
        }
        if (towerNodeDistance == 0) {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points. later this will be removed/fixed while 
            // removing short edges where one node is of degree 2
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        int fromIndex = allNodes.get(0);
        int toIndex = allNodes.get(nodes - 1);
        EdgeIterator iter = g.edge(fromIndex, toIndex, towerNodeDistance, flags);
        if (nodes > 2) {
            TIntList pillarNodes = allNodes.subList(1, nodes - 1);
            iter.pillarNodes(pillarNodes);
        }
        return nodes;
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
