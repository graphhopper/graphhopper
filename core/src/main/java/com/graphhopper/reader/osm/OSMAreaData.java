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
import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.BitUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OSMAreaData {
    // todonow: private
    final List<OSMArea> osmAreas;
    final LongToTwoIntsMap osmAreaNodeIndicesByOSMNodeIds;
    final KVStorage osmAreaTagStorage;

    public OSMAreaData(Directory directory) {
        osmAreas = new ArrayList<>();
        osmAreaNodeIndicesByOSMNodeIds = new LongToTwoIntsMap();
        // todonow: using a separate ram directory, because otherwise it is harder to measure memory (bc the kv storage keeps a reference to the directory)
        osmAreaTagStorage = new KVStorage(new RAMDirectory(), "osm_area_tags_");
    }

    public void addOSMAreaWithoutCoordinates(LongArrayList osmNodeIds, Map<String, Object> tags) {
        long tagPointer = osmAreaTagStorage.add(tags.entrySet().stream().map(m -> new KVStorage.KeyValue(m.getKey(),
                        m.getValue() instanceof String ? KVStorage.cutString((String) m.getValue()) : m.getValue()))
                .collect(Collectors.toList()));
        if (tagPointer > Integer.MAX_VALUE)
            throw new IllegalStateException("Too many tags are stored for OSM areas " + tagPointer);
        osmAreas.add(new OSMArea(osmNodeIds.size(), tagPointer));
        for (LongCursor node : osmNodeIds)
            osmAreaNodeIndicesByOSMNodeIds.put(node.value, osmAreas.size() - 1, node.index);
    }

    public void fillOSMAreaNodeCoordinates(ReaderNode node) {
        long index = osmAreaNodeIndicesByOSMNodeIds.get(node.getId());
        if (index >= 0) {
            int osmAreaIndex = BitUtil.LITTLE.getIntLow(index);
            int nodeIndex = BitUtil.LITTLE.getIntHigh(index);
            OSMArea osmArea = osmAreas.get(osmAreaIndex);
            // Note that we set the coordinates only for one particular node for one particular
            // osm area, even though the same osm node might be used in multiple such areas. We will
            // fix the next time we get to see the osm area ways.
            osmArea.setCoordinate(nodeIndex, node.getLat(), node.getLon());
        }
    }

    public void fixOSMArea(int osmAreaWayIndex, ReaderWay way) {
        // The problem we solve here is that some osm nodes are used by multiple landuse/area ways.
        // At the very least this is the case for the first and last nodes of the closed-ring ways,
        // but there are also many areas that really share common nodes. Since we only store one
        // index into the coordinate array of the area polygons, the coordinates for some polygon
        // nodes won't be set. Therefore we need to make up for this here where we get to see the
        // ways a second time. If this wasn't the case we could read the area ways&nodes in pass1
        // instead of pass0 which would allow us to skip nodes and ways in pass0 (faster import).
        OSMArea actual = osmAreas.get(osmAreaWayIndex);
        for (LongCursor node : way.getNodes()) {
            long index = osmAreaNodeIndicesByOSMNodeIds.get(node.value);
            int osmAreaIndex = BitUtil.LITTLE.getIntLow(index);
            int nodeIndex = BitUtil.LITTLE.getIntHigh(index);
            OSMArea osmArea = osmAreas.get(osmAreaIndex);
            actual.border.setX(node.index, osmArea.border.getX(nodeIndex));
            actual.border.setY(node.index, osmArea.border.getY(nodeIndex));
        }
    }

    public List<OSMArea> getOSMAreas() {
        return osmAreas;
    }

    public static class LongToTwoIntsMap {
        private final LongIntMap internalIdsByKey = new GHLongIntBTree(200);
        private final IntArrayList vals1 = new IntArrayList();
        private final IntArrayList vals2 = new IntArrayList();

        public void put(long key, int val1, int val2) {
            vals1.add(val1);
            vals2.add(val2);
            internalIdsByKey.put(key, vals1.size() - 1);
        }

        public long get(long key) {
            int id = internalIdsByKey.get(key);
            if (id < 0) return -1;
            return BitUtil.LITTLE.combineIntsToLong(vals1.get(id), vals2.get(id));
        }

        public void clear() {
            internalIdsByKey.clear();
            vals1.release();
            vals2.release();
        }
    }
}
