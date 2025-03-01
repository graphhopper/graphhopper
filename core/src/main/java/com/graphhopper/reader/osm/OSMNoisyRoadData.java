/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.coll.GHLongLongBTree;
import com.graphhopper.coll.LongLongMap;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.BitUtil;

import java.util.ArrayList;
import java.util.List;

public class OSMNoisyRoadData {
    // todonow make private and final
    List<OSMNoisyRoad> osmNoisyRoads;
    LongToTwoIntsMap osmNoisyRoadsNodeIndicesByOSMNodeIds;

    public OSMNoisyRoadData() {
        osmNoisyRoads = new ArrayList<>();
        osmNoisyRoadsNodeIndicesByOSMNodeIds = new LongToTwoIntsMap();
    }

    public void addOSMNoisyRoadWithoutCoordinates(LongArrayList osmNodeIds) {
        osmNoisyRoads.add(new OSMNoisyRoad(osmNodeIds.size()));
        for (LongCursor node : osmNodeIds)
            osmNoisyRoadsNodeIndicesByOSMNodeIds.put(node.value, osmNoisyRoads.size() - 1, node.index);
    }

    public void fillOSMNoisyRoadNodeCoordinates(ReaderNode node) {
        long index = osmNoisyRoadsNodeIndicesByOSMNodeIds.get(node.getId());
        if (index >= 0) {
            int osmNoisyRoadIndex = BitUtil.LITTLE.getIntLow(index);
            int nodeIndex = BitUtil.LITTLE.getIntHigh(index);
            OSMNoisyRoad osmNoisyRoad = osmNoisyRoads.get(osmNoisyRoadIndex);
            // Note that we set the coordinates only for one particular node for one particular
            // osm way, even though the same osm node might be used in multiple such ways. We will
            // fix that the next time we get to see the osm way.
            osmNoisyRoad.setCoordinate(nodeIndex, node.getLat(), node.getLon());
        }
    }

    public void clear(){
        osmNoisyRoadsNodeIndicesByOSMNodeIds.clear();
    }

    public void fixOSMNoisyRoads(int osmNoisyWayIndex, ReaderWay way) {
        // The problem we solve here is that some osm nodes are used by multiple noisy ways.
        // At the very least this is the case for the first and last node of the ways,
        // but there are also many junctions that share a common node. Since we only store one
        // index into the coordinate array of the noisy roads, the coordinates for some noisy roads
        // nodes won't be set. Therefore, we need to make up for this here where we get to see the
        // ways a second time. If this wasn't the case we could read the noisy ways&nodes in pass1
        // instead of pass0 which would allow us to skip nodes and ways in pass0 (faster import).
        OSMNoisyRoad actual = osmNoisyRoads.get(osmNoisyWayIndex);
        for (LongCursor node : way.getNodes()) {
            long index = osmNoisyRoadsNodeIndicesByOSMNodeIds.get(node.value);
            int osmNoisyRoadIndex = BitUtil.LITTLE.getIntLow(index);
            int nodeIndex = BitUtil.LITTLE.getIntHigh(index);
            OSMNoisyRoad osmNoisyRoad = osmNoisyRoads.get(osmNoisyRoadIndex);
            actual.road.setX(node.index, osmNoisyRoad.road.getX(nodeIndex));
            actual.road.setY(node.index, osmNoisyRoad.road.getY(nodeIndex));
        }
    }

    public List<OSMNoisyRoad> getOsmNoisyRoads() {
        return osmNoisyRoads;
    }

    public void clearOsmNoisyRoads()  {
        osmNoisyRoads.clear();
        osmNoisyRoads = null;
    }

    public static class LongToTwoIntsMap {
        private final LongLongMap internalIdsByKey = new GHLongLongBTree(200, 4, -1);
        private final IntArrayList vals1 = new IntArrayList();
        private final IntArrayList vals2 = new IntArrayList();

        public void put(long key, int val1, int val2) {
            vals1.add(val1);
            vals2.add(val2);
            internalIdsByKey.put(key, vals1.size() - 1);
        }

        public long get(long key) {
            long id = internalIdsByKey.get(key);
            if (id < 0) return -1;
            return BitUtil.LITTLE.toLong(vals1.get((int)id), vals2.get((int)id));
        }

        public void clear() {
            internalIdsByKey.clear();
            vals1.release();
            vals2.release();
        }
    }
}
