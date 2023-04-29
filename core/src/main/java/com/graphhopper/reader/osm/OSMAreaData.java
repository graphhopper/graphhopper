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

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.storage.Directory;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.openjdk.jol.info.GraphLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OSMAreaData {
    private final List<OSMAreaInternal> osmAreas;
    private final LongIntMap nodeIndexByOSMNodeId;
    private final PillarInfo coordinates;

    public OSMAreaData(Directory directory) {
        osmAreas = new ArrayList<>();
        nodeIndexByOSMNodeId = new GHLongIntBTree(200);
        coordinates = new PillarInfo(false, directory, "_osm_area");
    }

    public void addArea(Map<String, Object> tags, LongArrayList nodes) {
        osmAreas.add(new OSMAreaInternal(tags, nodes));
        for (LongCursor node : nodes)
            if (nodeIndexByOSMNodeId.get(node.value) < 0)
                nodeIndexByOSMNodeId.put(node.value, Math.toIntExact(nodeIndexByOSMNodeId.getSize()));
    }

    public void setCoordinate(long osmNodeId, double lat, double lon) {
        int nodeIndex = nodeIndexByOSMNodeId.get(osmNodeId);
        if (nodeIndex >= 0)
            coordinates.setNode(nodeIndex, lat, lon);
    }

    public List<OSMArea> buildOSMAreas() {
        // todonow: remove later
        System.out.println(GraphLayout.parseInstance(this).toFootprint());
        System.out.println(GraphLayout.parseInstance(osmAreas).toFootprint());
        System.out.println(GraphLayout.parseInstance(nodeIndexByOSMNodeId).toFootprint());
        System.out.println(GraphLayout.parseInstance(coordinates).toFootprint());
        AtomicInteger invalidGeometries = new AtomicInteger();
        GeometryFactory geometryFactory = new GeometryFactory();
        List<OSMArea> result = osmAreas.stream().map(a -> {
                    double[] coords = new double[a.nodes.size() * 2];
                    PackedCoordinateSequence.Double coordSequence = new PackedCoordinateSequence.Double(coords, 2, 0);
                    for (LongCursor node : a.nodes) {
                        int nodeIndex = nodeIndexByOSMNodeId.get(node.value);
                        coordSequence.setX(node.index, coordinates.getLon(nodeIndex));
                        coordSequence.setY(node.index, coordinates.getLat(nodeIndex));
                    }
                    try {
                        Polygon polygon = geometryFactory.createPolygon(coordSequence);
                        return new OSMArea(a.tags, polygon);
                    } catch (IllegalArgumentException e) {
                        // todonow: apparently, some areas do not form a closed ring or something, looks like these are tagging errors in OSM!
                        invalidGeometries.incrementAndGet();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        System.out.println("Warning: There were " + invalidGeometries.get() + " invalid geometries among the " + osmAreas.size() + " osm areas");
        return result;
    }

    public static class OSMAreaInternal {
        Map<String, Object> tags;
        LongArrayList nodes;

        public OSMAreaInternal(Map<String, Object> tags, LongArrayList nodes) {
            this.tags = tags;
            this.nodes = nodes;
        }
    }
}
