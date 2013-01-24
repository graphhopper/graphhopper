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

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import gnu.trove.list.TLongList;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public abstract class OSMReaderHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());    
    protected long zeroCounter = 0;
    protected final Graph g;
    protected final long expectedNodes;
    private DistanceCalc callback = new DistanceCalc();

    public OSMReaderHelper(Graph g, long expectedNodes) {
        this.g = g;
        this.expectedNodes = expectedNodes;
    }

    public void callback(DistanceCalc callback) {
        this.callback = callback;
    }

    public long expectedNodes() {
        return expectedNodes;
    }

    public void preProcess(InputStream osmXml) {
    }

    public abstract boolean addNode(long osmId, double lat, double lon);

    public abstract int addEdge(TLongList nodes, int flags);

    int addEdge(int fromIndex, int toIndex, PointList pointList, int flags) {
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge "
                    + fromIndex + "->" + toIndex + ", points:" + pointList);

        double towerNodeDistance = 0;
        double prevLat = pointList.latitude(0);
        double prevLon = pointList.longitude(0);
        double lat;
        double lon;
        PointList pillarNodes = new PointList(pointList.size() - 2);
        int nodes = pointList.size();
        for (int i = 1; i < nodes; i++) {
            lat = pointList.latitude(i);
            lon = pointList.longitude(i);
            towerNodeDistance += callback.calcDist(prevLat, prevLon, lat, lon);
            prevLat = lat;
            prevLon = lon;
            if (nodes > 2 && i < nodes - 1)
                pillarNodes.add(lat, lon);
        }
        if (towerNodeDistance == 0) {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points. later this will be removed/fixed while 
            // removing short edges where one node is of degree 2
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        EdgeIterator iter = g.edge(fromIndex, toIndex, towerNodeDistance, flags);
        if (nodes > 2)
            iter.wayGeometry(pillarNodes);
        return nodes;
    }

    String getInfo() {
        return "Found " + zeroCounter + " zero distances.";
    }

    String getStorageInfo(GraphStorage storage) {
        return storage.getClass().getSimpleName() + "|" + storage.directory().getClass().getSimpleName()
                + "|" + storage.version();
    }

    void cleanup() {
    }

    void startWayProcessing() {
    }
}
