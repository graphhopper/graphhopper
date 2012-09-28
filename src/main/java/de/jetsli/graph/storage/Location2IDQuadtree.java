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
package de.jetsli.graph.storage;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyBitSetImpl;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.util.*;
import de.jetsli.graph.util.shapes.BBox;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an index mapping lat,lon coordinates to one node id or index of a routing graph.
 *
 * This implementation is the most memory efficient representation for indices which maps to a
 * routing graph. As a minor drawback it involves that you have to post-process the findID() query.
 * See the javadocs.
 *
 * @author Peter Karich
 */
public class Location2IDQuadtree implements Location2IDIndex {

    private final static int MAGIC_INT = Integer.MAX_VALUE / 12305;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private SpatialKeyAlgo algo;
    protected DistanceCalc dist = new DistanceCosProjection();
    private DataAccess index;
    private double maxNormRasterWidthKm;
    private int size;
    private Graph g;

    public Location2IDQuadtree(Graph g, Directory dir) {
        this.g = g;
        this.index = dir.createDataAccess("loc2idIndex");
    }

    @Override
    public Location2IDIndex setPrecision(boolean approxDist) {
        if (approxDist)
            dist = new DistanceCosProjection();
        else
            dist = new DistanceCalc();
        return this;
    }

    public int getCapacity() {
        return size;
    }

    /**
     * Loads the index from disc if exists. Make sure you are using the identical graph which was
     * used while flusing this index.
     *
     * @return if loading from file was successfully.
     */
    public boolean loadExisting() {
        if (index.loadExisting()) {
            if (index.getHeader(0) != MAGIC_INT)
                throw new IllegalStateException("incorrect loc2id index version");
            int bits = index.getHeader(1);
            int checksum = index.getHeader(2);
            if (checksum != g.getNodes())
                throw new IllegalStateException("index was created from a different graph with "
                        + checksum + ". Current nodes:" + g.getNodes());
            size = (int) (index.capacity() / 4);
            initAlgo(bits);
            return true;
        }
        return false;
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
    public Location2IDIndex prepareIndex(int _size) {
        int bits = initBuffer(_size);
        initAlgo(bits);
        StopWatch sw = new StopWatch().start();
        MyBitSet filledIndices = fillQuadtree(size);
        int fillQT = filledIndices.getCardinality();
        float res1 = sw.stop().getSeconds();
        sw = new StopWatch().start();
        int counter = fillEmptyIndices(filledIndices);
        logger.info("filled quadtree index array in " + res1 + "s. size is " + size
                + " (" + fillQT + "). filled empty " + counter + " in " + sw.stop().getSeconds() + "s");
        return this;
    }

    private int initBuffer(int _size) {
        size = _size;
        int bits = (int) (Math.log(size) / Math.log(2)) + 1;
        size = (int) Math.pow(2, bits);
        int x = (int) Math.sqrt(size);
        if (x * x < size) {
            x++;
            size = x * x;
        }

        // avoid default big segment size and use one segment only:
        index.setSegmentSize(size * 4);
        index.createNew(size * 4);
        return bits;
    }

    private void initAlgo(int bits) {
        BBox b = g.getBounds();
        logger.info("bounds:" + b + ", bits:" + bits + ", calc:" + dist.toString());
        algo = new SpatialKeyAlgo(bits).setInitialBounds(b.minLon, b.maxLon, b.minLat, b.maxLat);
        maxNormRasterWidthKm = dist.normalizeDist(Math.max(dist.calcDistKm(b.minLat, b.minLon, b.minLat, b.maxLon),
                dist.calcDistKm(b.minLat, b.minLon, b.maxLat, b.minLon)) / Math.sqrt(size));

        // as long as we have "dist < PI*R/2" it is save to compare the normalized distances instead of the real
        // distances. because sin(x) is only monotonic increasing for x <= PI/2 (and positive for x >= 0)
    }

    protected double getMaxRasterWidthKm() {
        return dist.denormalizeDist(maxNormRasterWidthKm);
    }

    private MyBitSet fillQuadtree(int size) {
        int locs = g.getNodes();
        if (locs <= 0)
            throw new IllegalStateException("check your graph - it is empty!");

        MyBitSet filledIndices = new MyBitSetImpl(size);
        CoordTrig coord = new CoordTrig();
        for (int nodeId = 0; nodeId < locs; nodeId++) {
            double lat = g.getLatitude(nodeId);
            double lon = g.getLongitude(nodeId);
            int key = (int) algo.encode(lat, lon);
            if (filledIndices.contains(key)) {
                int oldNodeId = index.getInt(key);
                algo.decode(key, coord);
                // decide which one is closer to 'key'
                double distNew = dist.calcNormalizedDist(coord.lat, coord.lon, lat, lon);
                double oldLat = g.getLatitude(oldNodeId);
                double oldLon = g.getLongitude(oldNodeId);
                double distOld = dist.calcNormalizedDist(coord.lat, coord.lon, oldLat, oldLon);
                // new point is closer to quad tree point (key) so overwrite old
                if (distNew < distOld)
                    index.setInt(key, nodeId);
            } else {
                index.setInt(key, nodeId);
                filledIndices.add(key);
            }
        }
        return filledIndices;
    }

    private int fillEmptyIndices(MyBitSet filledIndices) {
        // 3. fill empty indices with points close to them to return correct id's for find()!
        final int maxSearch = 10;
        int counter = 0;
        List<Edge> list = new ArrayList<Edge>();
        for (int mainKey = 0; mainKey < size; mainKey++) {
//            if (mainKey % 100000 == 0)
//                logger.info("mainKey:" + mainKey);
            final CoordTrig mainCoord = new CoordTrig();
            algo.decode(mainKey, mainCoord);
            if (filledIndices.contains(mainKey))
                continue;

            counter++;
            list.clear();
            // check the quadtree
            for (int keyOffset = 1; keyOffset < maxSearch || list.isEmpty(); keyOffset++) {
                // search forward and backwards
                for (int i = -1; i < 2; i += 2) {
                    int tmpKey = mainKey + i * keyOffset;
                    if (tmpKey < 0 || tmpKey >= size) {
                        if (keyOffset >= size)
                            throw new IllegalStateException("couldn't find any node!? " + tmpKey + " " + keyOffset + " " + size);

                        continue;
                    }

                    boolean ret = filledIndices.contains(tmpKey);
                    if (ret) {
                        int tmpId = index.getInt(tmpKey);
                        double lat = g.getLatitude(tmpId);
                        double lon = g.getLongitude(tmpId);
                        double dist = this.dist.calcNormalizedDist(mainCoord.lat, mainCoord.lon, lat, lon);
                        list.add(new Edge(tmpId, dist));
                    }
                }
            }

//            if (list.isEmpty())
//                throw new IllegalStateException("no node found in quadtree which is close to id "
//                        + mainKey + " " + mainCoord + " size:" + size);
//            Collections.sort(list, new Comparator<DistEntry>() {
//
//                @Override public int compare(DistEntry o1, DistEntry o2) {
//                    return Double.compare(o1.distance, o2.distance);
//                }
//            });

            // choose the best we have so far - no need to sort - just select
            Edge tmp = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                if (tmp.weight > list.get(i).weight)
                    tmp = list.get(i);
            }
            final Edge closestNode = tmp;

            // use only one bitset for the all iterations so we do not check the same nodes again
            final MyBitSet bitset = new MyTBitSet(maxSearch * 4);
            // now explore the graph
            for (final Edge de : list) {
                final BooleanRef onlyOneDepth = new BooleanRef();
                new XFirstSearch() {
                    @Override protected MyBitSet createBitSet(int size) {
                        return bitset;
                    }

                    @Override protected boolean goFurther(int nodeId) {
                        if (nodeId == de.node)
                            return true;

                        double currLat = g.getLatitude(nodeId);
                        double currLon = g.getLongitude(nodeId);
                        double d = dist.calcNormalizedDist(currLat, currLon, mainCoord.lat, mainCoord.lon);
                        if (d < closestNode.weight) {
                            closestNode.weight = d;
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

            if (mainKey < 0 || mainKey >= size)
                throw new IllegalStateException("Problem with mainKey:" + mainKey + " " + mainCoord + " size:" + size);

            index.setInt(mainKey, closestNode.node);
            // do not forget this to speed up inner loop
            filledIndices.add(mainKey);
        }

        return counter;
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
        final int id = index.getInt((int) key);
        double mainLat = g.getLatitude(id);
        double mainLon = g.getLongitude(id);
        final Edge closestNode = new Edge(id, dist.calcNormalizedDist(lat, lon, mainLat, mainLon));
        goFurtherHook(id);
        new XFirstSearch() {
            @Override protected MyBitSet createBitSet(int size) {
                return new MyTBitSet(10);
            }

            @Override protected boolean goFurther(int nodeId) {
                if (nodeId == id)
                    return true;

                goFurtherHook(nodeId);
                double currLat = g.getLatitude(nodeId);
                double currLon = g.getLongitude(nodeId);
                double d = dist.calcNormalizedDist(currLat, currLon, lat, lon);
                if (d < closestNode.weight) {
                    closestNode.weight = d;
                    closestNode.node = nodeId;
                    return true;
                }

                return d < maxNormRasterWidthKm * 2;
            }
        }.start(g, id, false);

//        logger.info("key:" + key + " lat:" + lat + ",lon:" + lon);
        return closestNode.node;
    }

    public void goFurtherHook(int n) {
    }

    public void flush() {
        index.setHeader(0, MAGIC_INT);
        index.setHeader(1, algo.getBits());
        index.setHeader(2, g.getNodes());
        index.flush();
    }

    @Override
    public float calcMemInMB() {
        return (float) index.capacity() / (1 << 20);
    }
}
