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
package com.graphhopper.storage;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.coll.MyTBitSet;
import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.LinearKeyAlgo;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCosProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.NumHelper;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.XFirstSearch;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.array.TIntArrayList;

/**
 * More precise index compared to Location2IDQuadtree. Preparation takes longer and it requires a
 * lot more RAM.
 *
 * 1. use an array organized as quadtree. I.e. you can devide your area into tiles, and per tile you
 * have an array entry.
 *
 * TODO 1 Omit this for now to make querying faster and implementation simpler: 2. now in a
 * preprocessing step you need to find out which subgraphs are necessary to reach all nodes in one
 * tiles.
 *
 * 3. querying on point A=(lat,lon) converting this to the tile number. Then you'll get all
 * necessary subgraphs. Now you'll need to calculate nearest neighbor of the nodes/edges to your
 * point A using euclidean geometry (which should be fine as long as they are not too far away which
 * is the case for nearest neighbor).
 *
 * @author Peter Karich
 */
public class Location2IDPreciseIndex implements Location2IDIndex {

    private static final String LIST_NAME = "loc2idIndex";
    private ListOfArrays index;
    private Graph g;
    private DistanceCalc calc = new DistanceCalc();
    private KeyAlgo algo;
    private double latWidth, lonWidth;
    private int latSizeI, lonSizeI;
    private boolean calcEdgeDistance = true;

    public Location2IDPreciseIndex(Graph g, Directory dir) {
        this.g = g;
        index = new ListOfArrays(dir, LIST_NAME);
    }

    /**
     * Loads the index from disc if exists. Make sure you are using the identical graph which was
     * used while flusing this index.
     *
     * @return if loading from file was successfully.
     */
    public boolean loadExisting() {
        if (index.loadExisting()) {
            latSizeI = index.getHeader(0);
            lonSizeI = index.getHeader(1);
            calcEdgeDistance = index.getHeader(2) == 1;
            int checksum = index.getHeader(3);
            if (checksum != g.getNodes())
                throw new IllegalStateException("index was created from a different graph with "
                        + checksum + ". Current nodes:" + g.getNodes());
            prepareAlgo();
            return true;
        }
        return false;
    }

    /**
     * Applies only if called before prepareIndex
     */
    public Location2IDIndex setCalcEdgeDistance(boolean calcEdgeDist) {
        calcEdgeDistance = calcEdgeDist;
        return this;
    }

    @Override
    public Location2IDIndex setPrecision(boolean approxDist) {
        if (approxDist)
            calc = new DistanceCosProjection();
        else
            calc = new DistanceCalc();
        return this;
    }

    DistanceCalc getCalc() {
        return calc;
    }

    public InMemConstructionIndex prepareInMemoryIndex(int capacity) {
        prepareBounds(capacity);
        prepareAlgo();
        InMemConstructionIndex hi = new InMemConstructionIndex();
        hi.initBuffer(latSizeI, lonSizeI);
        hi.initIndex();
        hi.compact();
        return hi;
    }

    @Override
    public Location2IDIndex prepareIndex(int capacity) {
        InMemConstructionIndex hi = prepareInMemoryIndex(capacity);
        index.createNew(latSizeI * lonSizeI);
        hi.fill(index);
        hi.initEmptySlots(index);
        return this;
    }

    private void prepareBounds(int size) {
        latSizeI = lonSizeI = (int) Math.sqrt(size);

        // Same number of entries for x and y otherwise we would need an adapted spatialkey algo.
        // Accordingly the width of a tile is different for x and y!
        if (latSizeI * lonSizeI < size)
            lonSizeI++;
    }

    protected KeyAlgo createKeyAlgo(int latS, int lonS) {
        return new LinearKeyAlgo(latS, lonS);
    }

    private void prepareAlgo() {
        BBox b = g.getBounds();
        algo = createKeyAlgo(latSizeI, lonSizeI).setInitialBounds(b.minLon, b.maxLon, b.minLat, b.maxLat);
        latWidth = (b.maxLat - b.minLat) / latSizeI;
        lonWidth = (b.maxLon - b.minLon) / lonSizeI;
    }

    public class InMemConstructionIndex {

        private TIntArrayList[] inMemIndex;

        void initBuffer(int latSizeI, int lonSizeI) {
            inMemIndex = new TIntArrayList[latSizeI * lonSizeI];
        }
        StopWatch sw = new StopWatch();

        void initIndex() {
            int nodes = g.getNodes();
            MyBitSet alreadyDone = new MyBitSetImpl(nodes);
            int added = 0;
            StopWatch swWhile = new StopWatch();
            for (int node = 0; node < nodes; node++) {
                alreadyDone.add(node);
                double lat = g.getLatitude(node);
                double lon = g.getLongitude(node);
                added++;
                add((int) algo.encode(lat, lon), node);

                if (calcEdgeDistance) {
                    swWhile.start();
                    EdgeIterator iter = g.getOutgoing(node);
                    while (iter.next()) {
                        int connNode = iter.node();
                        if (alreadyDone.contains(connNode))
                            continue;

                        double connLat = g.getLatitude(connNode);
                        double connLon = g.getLongitude(connNode);
                        // already done in main loop: add((int) algo.encode(connLat, connLon), connNode);

                        double tmpLat = lat;
                        double tmpLon = lon;
                        if (connLat < lat) {
                            double tmp = tmpLat;
                            tmpLat = connLat;
                            connLat = tmp;
                        }

                        if (connLon < lon) {
                            double tmp = tmpLon;
                            tmpLon = connLon;
                            connLon = tmp;
                        }

                        // add edge to all possible entries
                        // TODO use bresenhamLine
                        for (double tryLat = tmpLat; tryLat < connLat + latWidth; tryLat += latWidth) {
                            for (double tryLon = tmpLon; tryLon < connLon + lonWidth; tryLon += lonWidth) {
                                if (NumHelper.equals(tryLon, tmpLon) && NumHelper.equals(tryLat, tmpLat)
                                        || NumHelper.equals(tryLon, connLon) && NumHelper.equals(tryLat, connLat))
                                    continue;
                                added++;
                                add((int) algo.encode(tryLat, tryLon), connNode);
                            }
                        }
                    }
                    swWhile.stop();
                }
//                if (added % 100000 == 0)
//                    logger.info("node:" + node + ", added:" + added + " add:" + sw.getSeconds() + ", while:" + swWhile.getSeconds());
            }
        }

        void add(int key, int node) {
            sw.start();
            if (inMemIndex[key] == null)
                inMemIndex[key] = new TIntArrayList(30);
            if (!inMemIndex[key].contains(node))
                inMemIndex[key].add(node);
            sw.stop();
        }

        void compact() {
            // TODO save a more memory: remove all nodes which can be reached from other nodes within 
            // the tile width <=> only one entry per subgraph. query needs to be adapted to search 
            // until it leaves the current tile

            // save memory
            int sum = 0;
            int max = 0;
            int counter = 0;
            for (int i = 0; i < inMemIndex.length; i++) {
                if (inMemIndex[i] != null) {
                    inMemIndex[i].trimToSize();
                    counter++;
                    sum += inMemIndex[i].size();
                    if (max < inMemIndex[i].size())
                        max = inMemIndex[i].size();
                }
            }

            // bavaria (full init): max:3327, mean:320
            // System.out.println("max:" + max + ", mean:" + (float) sum / counter);
        }

        void initEmptySlots(ListOfArrays la) {
            // Here we don't need the precision of edge distance which will make it too slow.
            // Also just use just the reference of the found entry to save space
            int len = inMemIndex.length;
            TIntArrayList[] indexCopy = new TIntArrayList[len];
            int initializedCounter = 0;
            while (initializedCounter < len) {
                initializedCounter = 0;
                System.arraycopy(inMemIndex, 0, indexCopy, 0, len);

                // fan out initialized entries
                for (int i = 0; i < len; i++) {
                    if (indexCopy[i] != null) {
                        // check change "initialized to empty"
                        if ((i + 1) % lonSizeI != 0 && indexCopy[i + 1] == null) {
                            inMemIndex[i + 1] = indexCopy[i];
                            la.setSameReference(i + 1, i);
                        } else if (i + lonSizeI < len && indexCopy[i + lonSizeI] == null) {
                            inMemIndex[i + lonSizeI] = indexCopy[i];
                            la.setSameReference(i + lonSizeI, i);
                        }
                    } else {
                        // check change "empty to initialized"
                        if ((i + 1) % lonSizeI != 0 && indexCopy[i + 1] != null) {
                            inMemIndex[i] = indexCopy[i + 1];
                            la.setSameReference(i, i + 1);
                        } else if (i + lonSizeI < len && indexCopy[i + lonSizeI] != null) {
                            inMemIndex[i] = indexCopy[i + lonSizeI];
                            la.setSameReference(i, i + lonSizeI);
                        }
                    }

                    if (inMemIndex[i] != null)
                        initializedCounter++;
                }

                if (initializedCounter == 0)
                    throw new IllegalStateException("at least one entry has to be != null, which should have happened in initIndex");
            }
        }

        void fill(ListOfArrays la) {
            for (int i = 0; i < inMemIndex.length; i++) {
                if (inMemIndex[i] != null)
                    la.set(i, inMemIndex[i]);
            }
        }

        public int getLength() {
            return inMemIndex.length;
        }

        public TIntArrayList getNodes(int tileNumber) {
            return inMemIndex[tileNumber];
        }
    }

    @Override
    public int findID(final double lat, final double lon) {
        long key = algo.encode(lat, lon);
        IntIterator iter = index.getIterator((int) key);
        if (!iter.next())
            throw new IllegalStateException("shouldn't happen as all keys should have at least one associated id");

        int node = iter.value();
        double mainLat = g.getLatitude(node);
        double mainLon = g.getLongitude(node);
        final WeightedNode closestNode = new WeightedNode(node, calc.calcNormalizedDist(lat, lon, mainLat, mainLon));
        final MyBitSet bs = new MyTBitSet();
        while (true) {
            bs.clear();
            // traverse graph starting at node            
            new XFirstSearch() {
                @Override protected MyBitSet createBitSet(int size) {
                    return bs;
                }
                double currLat;
                double currLon;
                int currNode;
                double currDist;
                boolean goFurther = true;

                @Override
                protected boolean goFurther(int nodeId) {
                    currLat = g.getLatitude(nodeId);
                    currLon = g.getLongitude(nodeId);
                    currNode = nodeId;

                    currDist = calc.calcNormalizedDist(currLat, currLon, lat, lon);
                    if (currDist < closestNode.weight) {
                        closestNode.weight = currDist;
                        closestNode.node = currNode;
                    }

                    return goFurther;
                }

                @Override
                protected boolean checkConnected(int connectNode) {
                    goFurther = false;
                    double connLat = g.getLatitude(connectNode);
                    double connLon = g.getLongitude(connectNode);

                    // while traversing check distance of lat,lon to currNode and to the whole currEdge
                    double connectDist = calc.calcNormalizedDist(connLat, connLon, lat, lon);
                    double d = connectDist;
                    int tmpNode = connectNode;
                    if (calcEdgeDistance && calc.validEdgeDistance(lat, lon, currLat, currLon, connLat, connLon)) {
                        d = calc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, connLat, connLon);
                        if (currDist < connectDist)
                            tmpNode = currNode;
                    }

                    if (d < closestNode.weight) {
                        closestNode.weight = d;
                        closestNode.node = tmpNode;
                    }
                    return true;
                }
            }.start(g, node, false);
            if (!iter.next())
                break;

            node = iter.value();
        }
        // logger.info("nodes:" + len + " key:" + key + " lat:" + lat + ",lon:" + lon);
        return closestNode.node;
    }

    // http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm
    // or even better: http://en.wikipedia.org/wiki/Xiaolin_Wu%27s_line_algorithm
    void bresenhamLine(double x0, double y0, double x1, double y1) {
        double dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        double dy = Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        double err = (dx > dy ? dx : -dy) / 2;

        while (true) {
            // setPixel(x0, y0);
            if (x0 == x1 && y0 == y1)
                break;
            double e2 = err;
            if (e2 > -dx) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dy) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void flush() {
        index.setHeader(0, latSizeI);
        index.setHeader(1, lonSizeI);
        index.setHeader(2, calcEdgeDistance ? 1 : 0);
        index.setHeader(3, g.getNodes());
        index.flush();
    }

    @Override
    public float calcMemInMB() {
        return (float) index.capacity() / (1 << 20);
    }
}
