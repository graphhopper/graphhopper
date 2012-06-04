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
package de.jetsli.graph.storage;

import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.reader.CalcDistance;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;
import de.jetsli.graph.util.BooleanRef;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.XFirstSearch;
import de.jetsli.graph.util.shapes.Circle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class is an index mapping lat,lon coordinates to one node id or index of a routing graph.
 *
 * This implementation is the most memory efficient representation for indices which maps to a
 * routing graph. As a minor drawback it involves that you have to post-process the findID() query.
 * See the javadocs.
 *
 * @author Peter Karich
 */
public class ID2LocationQT implements ID2LocationIndex {

    private SpatialKeyAlgo algo;
    private CalcDistance calc = new CalcDistance();
    private IntBuffer quadtree;
    private double maxRasterWidthKm;
    private int size;
    private Graph g;

    public ID2LocationQT(Graph g) {
        this.g = g;
    }

    public int getCapacity() {
        return size;
    }

    /**
     * Fill quadtree which will span a raster over the entire specified graph g. But do this in a
     * pre-defined resolution which is controlled via capacity. This datastructure then uses approx.
     * capacity * 4 bytes. So maximum capacity is 2^30 where the quadtree would cover the world
     * boundaries every 1.3km - IMO enough for EU or US networks.
     *
     * TODO it should be additionally possible to specify the minimum raster width instead of the 
     * memory usage
     */
    @Override
    public ID2LocationIndex prepareIndex(int _size) {
        int bits = initBuffer(_size);
        initAlgo(bits);
        MyOpenBitSet filledIndices = fillQuadtree(size);
        fillEmptyIndices(filledIndices);
        return this;
    }

    /**
     * @return the node id (corresponding to a coordinate) closest to the specified lat,lon.
     */
    @Override
    public int findID(final double lat, final double lon) {
        // The following cases (e.g. dead ends or motorways crossing a normal way) could be problematic:
        // |     |
        // |  P  | 
        // |  |  |< --- raster limit reached
        // \-----/
        /*
         * TODO use additionally the 8 surrounding quadrants: There an error due to the raster
         * width. Because this index does not cover 100% of the graph you'll need to traverse the
         * graph until you find the real matching point or if you reach the raster width limit. And
         * there is a problem when using the raster limit as 'not found' indication and if you have
         * arbitrary requests e.g. from other systems (where points do not match exactly): Although
         * P is the closest point to the request one it could be that the raster limit is too short
         * to reach it via graph traversal:
         */

        long key = algo.encode(lat, lon);
        int id = quadtree.get((int) key);
        float mainLat = g.getLatitude(id);
        float mainLon = g.getLongitude(id);
        final DistEntry closestNode = new DistEntry(id,
                (float) calc.calcDistKm(lat, lon, mainLat, mainLon));
        new XFirstSearch() {

            @Override protected boolean goFurther(int nodeId) {
                double currLat = g.getLatitude(nodeId);
                double currLon = g.getLongitude(nodeId);
                float d = (float) calc.calcDistKm(currLat, currLon, lat, lon);
                if (d < closestNode.distance) {
                    closestNode.distance = d;
                    closestNode.node = nodeId;
                    return true;
                }

                return d < 2 * maxRasterWidthKm;
            }
        }.start(g, id, false);
        return closestNode.node;
    }

    /**
     * @return an implementation which returns the closest point and iterates through all points of
     * the graph
     */
    public ID2LocationIndex createFullIndex() {
        return new ID2LocationIndex() {

            @Override public ID2LocationIndex prepareIndex(int capacity) {
                return this;
            }

            @Override public int findID(double lat, double lon) {
                float locs = g.getLocations();
                int id = -1;
                Circle circle = null;
                for (int i = 0; i < locs; i++) {
                    float tmpLat = g.getLatitude(i);
                    float tmpLon = g.getLongitude(i);
                    if (circle == null) {
                        id = i;
                        circle = new Circle(lat, lon, calc.calcDistKm(tmpLat, tmpLon, lat, lon), calc);
                    } else if (circle.contains(tmpLat, tmpLon)) {
                        circle = new Circle(lat, lon, calc.calcDistKm(tmpLat, tmpLon, lat, lon), calc);
                        id = i;
                    }
                }
                return id;
            }
        };
    }

    private int initBuffer(int _size) {
        size = _size;
        int bits = (int) (Math.log(size) / Math.log(2)) + 1;
        size = (int) Math.pow(2, bits);
        int x = (int) Math.sqrt(size);
        int y = x;
        if (x * x < size)
            y++;
        if (size < x * y)
            size = x * y;

        quadtree = ByteBuffer.allocateDirect(size * 4).asIntBuffer();
        return bits;
    }

    private void initAlgo(int bits) {
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE, minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        int locs = g.getLocations();
        for (int nodeId = 0; nodeId < locs; nodeId++) {
            double lat = g.getLatitude(nodeId);
            double lon = g.getLongitude(nodeId);
            if (lat > maxLat)
                maxLat = lat;
            else if (lat < minLat)
                minLat = lat;

            if (lon > maxLon)
                maxLon = lon;
            else if (lon < minLon)
                minLon = lon;
        }
        algo = new SpatialKeyAlgo(bits).setInitialBounds(minLon, maxLon, minLat, maxLat);
        maxRasterWidthKm = Math.max(calc.calcDistKm(minLat, minLon, minLat, maxLon),
                calc.calcDistKm(minLat, minLon, maxLat, minLon));
    }

    private MyOpenBitSet fillQuadtree(int size) {
        int locs = g.getLocations();
        MyOpenBitSet filledIndices = new MyOpenBitSet(size);
        CoordTrig coord = new CoordTrig();
        for (int nodeId = 0; nodeId < locs; nodeId++) {
            float lat = g.getLatitude(nodeId);
            float lon = g.getLongitude(nodeId);
            int key = (int) algo.encode(lat, lon);
            if (filledIndices.contains(key)) {
                int oldNodeId = quadtree.get(key);
                algo.decode(key, coord);
                // decide which one is closer to 'key'
                double distNew = calc.calcDistKm(coord.lat, coord.lon, lat, lon);
                float oldLat = g.getLatitude(oldNodeId);
                float oldLon = g.getLongitude(oldNodeId);
                double distOld = calc.calcDistKm(coord.lat, coord.lon, oldLat, oldLon);
                // new point is closer to quad tree point (key) so overwrite old
                if (distNew < distOld)
                    quadtree.put(key, nodeId);
            } else {
                quadtree.put(key, nodeId);
                filledIndices.add(key);
            }
        }
        return filledIndices;
    }

    private void fillEmptyIndices(MyOpenBitSet filledIndices) {
        // 3. fill empty indices with points close to them to return correct id's for find()!        
        CoordTrig coord = new CoordTrig();
        int locs = g.getLocations();
        int maxSearch = 10;
        List<DistEntry> list = new ArrayList<DistEntry>();
        for (int nodeId = 0; nodeId < locs; nodeId++) {
            final CoordTrig mainCoord = new CoordTrig();
            algo.decode(nodeId, mainCoord);
            int mainKey = nodeId >>> 32 - algo.getBits();
            if (filledIndices.contains(mainKey))
                continue;

            list.clear();

            // check the quadtree
            for (int testIdx = 0; list.size() < maxSearch; testIdx++) {
                // search forward and backwards
                for (int i = -1; i < 2; i += 2) {
                    int tmpIndex = nodeId + i * testIdx;
                    if (tmpIndex < 0 || tmpIndex >= size)
                        continue;
                    if (filledIndices.contains(tmpIndex)) {
                        int key = quadtree.get(tmpIndex);
                        algo.decode(key, coord);
                        float dist = (float) calc.calcDistKm(mainCoord.lat, mainCoord.lon, coord.lat, coord.lon);
                        list.add(new DistEntry(tmpIndex, dist));
                    }
                }
            }

            if (list.isEmpty())
                throw new IllegalStateException("no close nodes found in quadtree for "
                        + nodeId + " " + mainCoord);
            Collections.sort(list, new Comparator<DistEntry>() {

                @Override public int compare(DistEntry o1, DistEntry o2) {
                    return Double.compare(o1.distance, o2.distance);
                }
            });

            // choose the best we have so far
            final DistEntry closestNode = list.get(0);
            // now explore the graph
            for (final DistEntry de : list) {
                final BooleanRef onlyOneDepth = new BooleanRef();
                new XFirstSearch() {

                    @Override protected boolean goFurther(int nodeId) {
                        double currLat = g.getLatitude(nodeId);
                        double currLon = g.getLongitude(nodeId);
                        float d = (float) calc.calcDistKm(currLat, currLon, mainCoord.lat, mainCoord.lon);
                        if (d < closestNode.distance) {
                            closestNode.distance = d;
                            closestNode.node = nodeId;
                        }

                        if (onlyOneDepth.value) {
                            onlyOneDepth.value = false;
                            return true;
                        }
                        return false;
                    }
                }.start(g, de.node, false);
            }

            quadtree.put(nodeId, closestNode.node);
        }
    }
}
