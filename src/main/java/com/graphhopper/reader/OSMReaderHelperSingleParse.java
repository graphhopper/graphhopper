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

import com.graphhopper.coll.CompressedArray;
import com.graphhopper.coll.OSMIDSegmentedMap;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Helper;
import gnu.trove.list.TLongList;
import org.slf4j.LoggerFactory;

/**
 * This helper does not require the osm to be parsed twice but is potentially
 * more memory intensive (and also slower!?) as it stores all occuring nodes and
 * its lat,lon. Only useful for smaller (&lt; 150km) graphs.
 *
 * @deprecated until we fix the memory and speed problem
 * @author Peter Karich
 */
public class OSMReaderHelperSingleParse extends OSMReaderHelper {

    private OSMIDSegmentedMap osmIdToIndexMap;
    private CompressedArray arr;

    public OSMReaderHelperSingleParse(Graph g, int expectedNodes) {
        super(g, expectedNodes);
        osmIdToIndexMap = new OSMIDSegmentedMap(expectedNodes, 100);
        arr = new CompressedArray();
    }

    @Override
    public boolean addNode(long osmId, double lat, double lon) {
        osmIdToIndexMap.write(osmId);
        arr.write(lat, lon);
        return true;
    }

    @Override
    public void startWayProcessing() {
        arr.flush();
        LoggerFactory.getLogger(getClass()).info("osmIdMap:" + osmIdToIndexMap.calcMemInMB()
                + ", compressedArray:" + arr.calcMemInMB() + ", " + Helper.getMemInfo());
    }

    @Override
    public int addEdge(TLongList nodes, int flags) {
        throw new UnsupportedOperationException("todo");
//        int fromIndex = (int) osmIdToIndexMap.get(nodeIdFrom);
//        int toIndex = (int) osmIdToIndexMap.get(nodeIdTo);
//        if (fromIndex == osmIdToIndexMap.getNoEntryValue() || toIndex == osmIdToIndexMap.getNoEntryValue())
//            return false;
//
//        try {
//            CoordTrig from = arr.get(fromIndex);
//            CoordTrig to = arr.get(toIndex);
//            if (from == null || to == null)
//                return false;
//
//            g.setNode(fromIndex, from.lat, from.lon);
//            g.setNode(toIndex, to.lat, to.lon);
//            return addEdge(from.lat, from.lon, to.lat, to.lon, fromIndex, toIndex, flags, callback);
//        } catch (Exception ex) {
//            throw new RuntimeException("Problem to add edge! with node ids " + fromIndex + "->" + toIndex
//                    + " vs. osm ids:" + nodeIdFrom + "->" + nodeIdTo, ex);
//        }
    }

    @Override
    public void cleanup() {
        osmIdToIndexMap = null;
    }
}
