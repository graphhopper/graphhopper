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
package com.graphhopper.storage.index;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.LinearKeyAlgo;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.util.BreadthFirstSearch;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * This class implements map matching and returns a node index from lat,lon coordinate. This
 * implementation is the a very memory efficient representation for areas with lots of node and
 * edges, but lacks precision. No edge distances are measured.
 * <p>
 *
 * @author Peter Karich
 * @see LocationIndexTree which is more precise but more complicated and also slightly slower
 * implementation of LocationIndex.
 * <p>
 */
class Location2IDQuadtree implements LocationIndex {
    private final static int MAGIC_INT = Integer.MAX_VALUE / 12306;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DataAccess index;
    private final Graph graph;
    private final NodeAccess nodeAccess;
    protected DistanceCalc distCalc = Helper.DIST_PLANE;
    private KeyAlgo keyAlgo;
    private double maxRasterWidth2InMeterNormed;
    private int lonSize, latSize;

    public Location2IDQuadtree(Graph g, Directory dir) {
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
        index = dir.find("loc2id_index");
        setResolution(100 * 100);
    }

    @Override
    public LocationIndex setApproximation(boolean approxDist) {
        if (approxDist)
            distCalc = Helper.DIST_PLANE;
        else
            distCalc = Helper.DIST_EARTH;

        return this;
    }

    @Override
    public long getCapacity() {
        return index.getCapacity() / 4;
    }

    /**
     * Loads the index from disc if exists. Make sure you are using the identical graph which was
     * used while flushing this index.
     * <p>
     *
     * @return if loading from file was successfully.
     */
    @Override
    public boolean loadExisting() {
        if (!index.loadExisting())
            return false;

        if (index.getHeader(0) != MAGIC_INT)
            throw new IllegalStateException("incorrect loc2id index version");

        int lat = index.getHeader(1 * 4);
        int lon = index.getHeader(2 * 4);
        int checksum = index.getHeader(3 * 4);
        if (checksum != graph.getNodes())
            throw new IllegalStateException("index was created from a different graph with "
                    + checksum + ". Current nodes:" + graph.getNodes());

        initAlgo(lat, lon);
        return true;
    }

    @Override
    public LocationIndex create(long size) {
        throw new UnsupportedOperationException("Not supported. Use prepareIndex instead.");
    }

    @Override
    public LocationIndex setResolution(int resolution) {
        initLatLonSize(resolution);
        return this;
    }

    /**
     * Fill quadtree which will span a raster over the entire specified graph g. But do this in a
     * pre-defined resolution which is controlled via capacity. This data structure then uses approx.
     * capacity * 4 bytes. So maximum capacity is 2^30 where the quadtree would cover the world
     * boundaries every 1.3km - IMO enough for EU or US networks.
     */
    @Override
    public LocationIndex prepareIndex() {
        initBuffer();
        initAlgo(latSize, lonSize);
        StopWatch sw = new StopWatch().start();
        GHBitSet filledIndices = fillQuadtree(latSize * lonSize);
        int fillQT = filledIndices.getCardinality();
        float res1 = sw.stop().getSeconds();
        sw = new StopWatch().start();
        int counter = fillEmptyIndices(filledIndices);
        float fillEmpty = sw.stop().getSeconds();
        logger.info("filled quadtree index array in " + res1 + "s. size is " + getCapacity()
                + " (" + fillQT + "). filled empty " + counter + " in " + fillEmpty + "s");
        flush();
        return this;
    }

    private void initLatLonSize(int size) {
        latSize = lonSize = (int) Math.sqrt(size);
        if (latSize * lonSize < size)
            lonSize++;
    }

    private void initBuffer() {
        // avoid default big segment size and use one segment only:
        index.setSegmentSize(latSize * lonSize * 4);
        index.create(latSize * lonSize * 4);
    }

    void initAlgo(int lat, int lon) {
        this.latSize = lat;
        this.lonSize = lon;
        BBox b = graph.getBounds();
        keyAlgo = new LinearKeyAlgo(lat, lon).setBounds(b);
        double max = Math.max(distCalc.calcDist(b.minLat, b.minLon, b.minLat, b.maxLon),
                distCalc.calcDist(b.minLat, b.minLon, b.maxLat, b.minLon));
        maxRasterWidth2InMeterNormed = distCalc.calcNormalizedDist(max / Math.sqrt(getCapacity()) * 2);

        // as long as we have "dist < PI*R/2" it is save to compare the normalized distances instead of the real
        // distances. because sin(x) is only monotonic increasing for x <= PI/2 (and positive for x >= 0)
    }

    protected double getMaxRasterWidthMeter() {
        return distCalc.calcDenormalizedDist(maxRasterWidth2InMeterNormed) / 2;
    }

    private GHBitSet fillQuadtree(int size) {
        int locs = graph.getNodes();
        if (locs <= 0) {
            throw new IllegalStateException("check your graph - it is empty!");
        }

        GHBitSet filledIndices = new GHBitSetImpl(size);
        GHPoint coord = new GHPoint();
        for (int nodeId = 0; nodeId < locs; nodeId++) {
            double lat = nodeAccess.getLatitude(nodeId);
            double lon = nodeAccess.getLongitude(nodeId);
            int key = (int) keyAlgo.encode(lat, lon);
            long bytePos = (long) key * 4;
            if (filledIndices.contains(key)) {
                int oldNodeId = index.getInt(bytePos);
                keyAlgo.decode(key, coord);
                // decide which one is closer to 'key'
                double distNew = distCalc.calcNormalizedDist(coord.lat, coord.lon, lat, lon);
                double oldLat = nodeAccess.getLatitude(oldNodeId);
                double oldLon = nodeAccess.getLongitude(oldNodeId);
                double distOld = distCalc.calcNormalizedDist(coord.lat, coord.lon, oldLat, oldLon);
                // new point is closer to quad tree point (key) so overwrite old
                if (distNew < distOld) {
                    index.setInt(bytePos, nodeId);
                }
            } else {
                index.setInt(bytePos, nodeId);
                filledIndices.add(key);
            }
        }
        return filledIndices;
    }

    private int fillEmptyIndices(GHBitSet filledIndices) {
        int len = latSize * lonSize;
        DataAccess indexCopy = new RAMDirectory().find("temp_index_copy");
        indexCopy.setSegmentSize(index.getSegmentSize()).create(index.getCapacity());
        GHBitSet indicesCopy = new GHBitSetImpl(len);
        int initializedCounter = filledIndices.getCardinality();
        // fan out initialized entries to avoid "nose-artifacts"
        // we 1. do not use the same index for check and set and iterate until all indices are set
        // and 2. use a taken-from array to decide which of the colliding should be preferred
        int[] takenFrom = new int[len];
        Arrays.fill(takenFrom, -1);
        for (int i = filledIndices.next(0); i >= 0; i = filledIndices.next(i + 1)) {
            takenFrom[i] = i;
        }
        if (initializedCounter == 0) {
            throw new IllegalStateException("at least one entry has to be != null, which should have happened in initIndex");
        }
        int tmp = initializedCounter;
        while (initializedCounter < len) {
            index.copyTo(indexCopy);
            filledIndices.copyTo(indicesCopy);
            initializedCounter = filledIndices.getCardinality();
            for (int i = 0; i < len; i++) {
                int to = -1, from = -1;
                if (indicesCopy.contains(i)) {
                    // check change "initialized to empty"                    
                    if ((i + 1) % lonSize != 0 && !indicesCopy.contains(i + 1)) {
                        // set right from current
                        from = i;
                        to = i + 1;
                    } else if (i + lonSize < len && !indicesCopy.contains(i + lonSize)) {
                        // set below from current
                        from = i;
                        to = i + lonSize;
                    }
                } else // check change "empty to initialized"
                    if ((i + 1) % lonSize != 0 && indicesCopy.contains(i + 1)) {
                        // set from right
                        from = i + 1;
                        to = i;
                    } else if (i + lonSize < len && indicesCopy.contains(i + lonSize)) {
                        // set from below
                        from = i + lonSize;
                        to = i;
                    }
                if (to >= 0) {
                    if (takenFrom[to] >= 0) {
                        // takenFrom[to] == to -> special case for normedDist == 0
                        if (takenFrom[to] == to
                                || getNormedDist(from, to) >= getNormedDist(takenFrom[to], to)) {
                            continue;
                        }
                    }

                    index.setInt(to * 4, indexCopy.getInt(from * 4));
                    takenFrom[to] = takenFrom[from];
                    filledIndices.add(to);
                    initializedCounter++;
                }
            }
        }

        return initializedCounter - tmp;
    }

    double getNormedDist(int from, int to) {
        int fromX = from % lonSize;
        int fromY = from / lonSize;
        int toX = to % lonSize;
        int toY = to / lonSize;
        int dx = (toX - fromX);
        int dy = (toY - fromY);
        return dx * dx + dy * dy;
    }

    @Override
    public QueryResult findClosest(final double queryLat, final double queryLon,
                                   final EdgeFilter edgeFilter) {
        if (isClosed())
            throw new IllegalStateException("You need to create a new LocationIndex instance as it is already closed");

        if (edgeFilter != EdgeFilter.ALL_EDGES)
            throw new UnsupportedOperationException("edge filters are not yet implemented for " + Location2IDQuadtree.class.getSimpleName());

        // The following cases (e.g. dead ends or motorways crossing a normal way) could be problematic:
        // |     |
        // |  P  | 
        // |  |  |< --- maxRasterWidth reached
        // \-----/
        /*
         * Problem: use additionally the 8 surrounding quadrants: There an error due to the raster
         * width. Because this index does not cover 100% of the graph you'll need to traverse the
         * graph until you find the real matching point or if you reach the raster width limit. And
         * there is a problem when using the raster limit as 'not found' indication and if you have
         * arbitrary requests e.g. from other systems (where points do not match exactly): Although
         * P is the closest point to the request one it could be that the raster limit is too short
         * to reach it via graph traversal:
         */
        long key = keyAlgo.encode(queryLat, queryLon);
        final int id = index.getInt(key * 4);
        double mainLat = nodeAccess.getLatitude(id);
        double mainLon = nodeAccess.getLongitude(id);
        final QueryResult res = new QueryResult(queryLat, queryLon);
        res.setClosestNode(id);
        res.setQueryDistance(distCalc.calcNormalizedDist(queryLat, queryLon, mainLat, mainLon));
        goFurtherHook(id);
        new BreadthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHTBitSet(10);
            }

            @Override
            protected boolean goFurther(int baseNode) {
                if (baseNode == id)
                    return true;

                goFurtherHook(baseNode);
                double currLat = nodeAccess.getLatitude(baseNode);
                double currLon = nodeAccess.getLongitude(baseNode);
                double currNormedDist = distCalc.calcNormalizedDist(queryLat, queryLon, currLat, currLon);
                if (currNormedDist < res.getQueryDistance()) {
                    res.setQueryDistance(currNormedDist);
                    res.setClosestNode(baseNode);
                    return true;
                }

                return currNormedDist < maxRasterWidth2InMeterNormed;
            }
        }.start(graph.createEdgeExplorer(), id);

        // denormalize distance
        res.setQueryDistance(distCalc.calcDenormalizedDist(res.getQueryDistance()));
        return res;
    }

    public void goFurtherHook(int n) {
    }

    @Override
    public void flush() {
        index.setHeader(0, MAGIC_INT);
        index.setHeader(1 * 4, latSize);
        index.setHeader(2 * 4, lonSize);
        index.setHeader(3 * 4, graph.getNodes());
        index.flush();
    }

    @Override
    public void close() {
        index.close();
    }

    @Override
    public boolean isClosed() {
        return index.isClosed();
    }

    @Override
    public void setSegmentSize(int bytes) {
        index.setSegmentSize(bytes);
    }
}
