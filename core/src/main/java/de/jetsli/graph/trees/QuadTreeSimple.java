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
package de.jetsli.graph.trees;

import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.reader.CalcDistance;
import de.jetsli.graph.util.*;
import de.jetsli.graph.util.shapes.Circle;
import de.jetsli.graph.util.shapes.Shape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A simple implementation of a spatial index via a spatial key trie - the normal java way (a bit
 * memory intensive with all those object references).
 *
 * The latitude and longitude is encoded via our spatial key - see SpatialKeyAlgo for more details.
 *
 * If the branch node would have only 2 children then it would be a binary tree - we would need to
 * shift only once. If the branch node would have 8 children then it would be an oct tree - shifting
 * 3 times.
 *
 * Warning: cannot store null values - an exception will be thrown.
 * 
 * Cannot store duplicates - the old value will be returned and overwritten.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class QuadTreeSimple<T> implements QuadTree<T> {

    private static class Acceptor<T> implements LeafWorker<T> {

        public final List<CoordTrig<T>> result = new ArrayList<CoordTrig<T>>();
        SpatialKeyAlgo algo;

        public Acceptor(SpatialKeyAlgo algo) {
            this.algo = algo;
        }

        @Override public boolean doWork(QTDataNode<T> dataNode, int i) {
            CoordTrigObjEntry<T> entry = new CoordTrigObjEntry<T>();
            algo.decode(dataNode.keys[i], entry);
            if (accept(entry)) {
                entry.setValue((T) dataNode.values[i]);
                result.add(entry);
            }

            return false;
        }

        public boolean accept(CoordTrig<T> entry) {
            return true;
        }
    }
    private final int mbits;
    private final long globalMaxBit;
    private final SpatialKeyAlgo algo;
    private final int entriesPerLeaf;
    private final CalcDistance calc = new CalcDistance();
    private int size;
    private QTNode<T> root;

    public QuadTreeSimple() {
        this(1, 64);
    }

    public QuadTreeSimple(int entriesPerLeafNode) {
        this(entriesPerLeafNode, 64);
    }

    public QuadTreeSimple(int entriesPerLeafNode, int bitsForLatLon) {
        mbits = bitsForLatLon;
        entriesPerLeaf = entriesPerLeafNode;
        globalMaxBit = 1L << (bitsForLatLon - 1);
        algo = new SpatialKeyAlgo(bitsForLatLon);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public QuadTreeSimple init(int maxItemsHint) {
        return this;
    }

    @Override
    public T put(double lat, double lon, T value) {
        if (value == null)
            throw new IllegalArgumentException("This quad tree does not support null values");

        long spatialKey = algo.encode(lat, lon);
        long maxBit = globalMaxBit;
        if (root == null) {
            size++;
            QTDataNode<T> d = new QTDataNode<T>(entriesPerLeaf);
            d.put(spatialKey, value);
            root = d;
            return null;
        }
        QTBranchNode<T> previousBranch = null;
        int previousNum = -1;
        QTNode<T> current = root;
        while (maxBit != 0) {
            if (current.hasData()) {
                return putData(spatialKey, value, current, previousBranch, previousNum, maxBit);
            }

            previousBranch = (QTBranchNode<T>) current;
            // latitude
            previousNum = (spatialKey & maxBit) == 0 ? 0 : 2;
            maxBit >>>= 1;
            // longitude
            if ((spatialKey & maxBit) == 0) {
                current = previousNum == 0 ? previousBranch.node0 : previousBranch.node2;
            } else {
                current = previousNum == 0 ? previousBranch.node1 : previousBranch.node3;
                previousNum++;
            }
            maxBit >>>= 1;
            if (current == null) {
                current = new QTDataNode<T>(entriesPerLeaf);
                previousBranch.set(previousNum, current);
            }
        }

        throw new UnsupportedOperationException("Cannot put element? Too many entries per area? Try increasing entries per leaf! "
                + lat + "," + lon + " spatial key:" + spatialKey + " value:" + value + " size:" + size);
    }

    private T putData(long spatialKey, T value, QTNode<T> current, QTNode<T> previousBranch,
            int previousNum, long maxBit) {
        QTDataNode<T> dataNode = (QTDataNode) current;
        T old = dataNode.getValue(spatialKey);
        // overwrite means we don't need to handle overflow
        if (old != null) {
            dataNode.put(spatialKey, value);
            return old;
        }

        size++;
        boolean overflow = dataNode.put(spatialKey, value);
        if (!overflow)
            return null;

        QTBranchNode<T> n = new QTBranchNode<T>();
        if (previousBranch != null)
            previousBranch.set(previousNum, n);
        else
            root = n;
        QTDataNode<T> data00 = new QTDataNode<T>(entriesPerLeaf);
        QTDataNode<T> data01 = new QTDataNode<T>(entriesPerLeaf);
        QTDataNode<T> data10 = new QTDataNode<T>(entriesPerLeaf);
        QTDataNode<T> data11 = new QTDataNode<T>(entriesPerLeaf);
        for (; maxBit != 0; maxBit >>>= 2) {
            // not necessary to clear dataxy as it is overwritten if necessary in the next loop
            overflow = data00.overwriteFrom(0, maxBit, dataNode, spatialKey, value);
            if (overflow) {
                // '0' would be full so try to divide it again
                QTBranchNode<T> tmp = new QTBranchNode<T>();
                n.set(0, tmp);
                n = tmp;
                continue;
            }

            overflow = data01.overwriteFrom(1, maxBit, dataNode, spatialKey, value);
            if (overflow) {
                QTBranchNode<T> tmp = new QTBranchNode<T>();
                n.set(1, tmp);
                n = tmp;
                continue;
            }

            overflow = data10.overwriteFrom(2, maxBit, dataNode, spatialKey, value);
            if (overflow) {
                QTBranchNode<T> tmp = new QTBranchNode<T>();
                n.set(2, tmp);
                n = tmp;
                continue;
            }

            overflow = data11.overwriteFrom(3, maxBit, dataNode, spatialKey, value);
            if (overflow) {
                QTBranchNode<T> tmp = new QTBranchNode<T>();
                n.set(3, tmp);
                n = tmp;
                continue;
            }

            // optimization: if(!data00.isEmpty()) n.set(0, data00); etc

            n.set(0, data00);
            n.set(1, data01);
            n.set(2, data10);
            n.set(3, data11);
            return null;
        }
        throw new IllegalStateException("tree full!? too many entries in datanode 0 "
                + data00 + " or 1 " + data01 + " or 2 " + data10 + " or 3 " + data11);
    }

    @Override
    public T get(final double lat, final double lon) {
        if (root == null)
            return null;

        final long spatialKey = algo.encode(lat, lon);
        final CoordTrig<T> ret = new CoordTrigObjEntry<T>();
        LeafWorker<T> worker = new LeafWorker<T>() {

            @Override public boolean doWork(QTDataNode<T> dataNode, int i) {
                if (dataNode.keys[i] == spatialKey) {
                    ret.setValue((T) dataNode.values[i]);
                    return true;
                }

                return false;
            }
        };
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err), root, worker);
        return ret.getValue();
    }

    @Override
    public boolean remove(double lat, double lon) {
        if (root == null)
            return false;

        final long spatialKey = algo.encode(lat, lon);
        LeafWorker<T> worker = new LeafWorker<T>() {

            @Override
            public boolean doWork(QTDataNode<T> entry, int index) {
                if (entry.remove(spatialKey)) {
                    size--;
                    // stop search
                    return true;
                }

                return false;
            }
        };
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        return getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err), root, worker);
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Collection<CoordTrig<T>> getNeighbours(final double lat, final double lon, final double distanceInKm) {
        final Circle c = new Circle(lat, lon, distanceInKm);
        Acceptor<T> distanceAcceptor = new Acceptor<T>(algo) {

            @Override public boolean accept(CoordTrig<T> entry) {
                return c.contains(entry.lat, entry.lon);
            }
        };
        getNeighbours(BBox.createEarthMax(), c, root,
                distanceAcceptor);
        return distanceAcceptor.result;
    }

    @Override
    public Collection<CoordTrig<T>> getNeighbours(Shape boundingBox) {
        Acceptor<T> worker = new Acceptor<T>(algo);
        getNeighbours(BBox.createEarthMax(), boundingBox, root, worker);
        return worker.result;
    }

    private boolean getNeighbours(BBox nodeBB, Shape searchRect, QTNode current, LeafWorker<T> worker) {
        if (current == null)
            return false;

        if (current.hasData()) {
            QTDataNode<T> dataNode = (QTDataNode<T>) current;
            for (int i = 0; i < dataNode.values.length; i++) {
                if (dataNode.values[i] == null)
                    break;

                if (worker.doWork(dataNode, i))
                    return true;
            }
            return false;
        }

        double lat12 = (nodeBB.maxLat + nodeBB.minLat) / 2;
        double lon12 = (nodeBB.minLon + nodeBB.maxLon) / 2;

        // top-left - see SpatialKeyAlgo that latitude goes from bottom to top and is 1 if on top
        // 10 11
        // 00 01
        QTNode<T> node10 = current.get(2);
        if (node10 != null) {
            BBox nodeRect10 = new BBox(nodeBB.minLon, lon12, lat12, nodeBB.maxLat);
            if (searchRect.intersect(nodeRect10)) {
                if (getNeighbours(nodeRect10, searchRect, node10, worker))
                    return true;
            }
        }

        // top-right
        QTNode<T> node11 = current.get(3);
        if (node11 != null) {
            BBox nodeRect11 = new BBox(lon12, nodeBB.maxLon, lat12, nodeBB.maxLat);
            if (searchRect.intersect(nodeRect11)) {
                if (getNeighbours(nodeRect11, searchRect, node11, worker))
                    return true;
            }
        }

        // bottom-left
        QTNode<T> node00 = current.get(0);
        if (node00 != null) {
            BBox nodeRect00 = new BBox(nodeBB.minLon, lon12, nodeBB.minLat, lat12);
            if (searchRect.intersect(nodeRect00)) {
                if (getNeighbours(nodeRect00, searchRect, node00, worker))
                    return true;
            }
        }

        // bottom-right
        QTNode<T> node01 = current.get(1);
        if (node01 != null) {
            BBox nodeRect01 = new BBox(lon12, nodeBB.maxLon, nodeBB.minLat, lat12);
            if (searchRect.intersect(nodeRect01)) {
                if (getNeighbours(nodeRect01, searchRect, node01, worker))
                    return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        root = null;
        size = 0;
    }

    @Override
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        List<QTNode<T>> newList = new ArrayList<QTNode<T>>();
        List<QTNode<T>> list = new ArrayList<QTNode<T>>();
        list.add(root);
        int counter = 0;
        int level = 0;
        while (true) {
            if (counter >= list.size()) {
                if (newList.isEmpty())
                    break;

                level++;
                sb.append(level).append("\n");
                list.clear();
                List<QTNode<T>> tmp = list;
                list = newList;
                newList = tmp;
                counter = 0;
            }

            toDetailString(list.get(counter), sb, newList);
            counter++;
        }
        sb.append("\n");
        return sb.toString();
    }

    private void toDetailString(QTNode<T> current, StringBuilder sb, List<QTNode<T>> list) {
        if (current == null) {
            sb.append("dn:null\t");
            return;
        }

        if (current.hasData()) {
            sb.append(((QTDataNode) current).toString(algo)).append("\t");
        } else {
            sb.append("B\t");
            list.add(current.get(2));
            list.add(current.get(3));
            list.add(current.get(0));
            list.add(current.get(1));
        }
    }

    @Override
    public long getMemoryUsageInBytes(int factor) {
        QTNode node = root;

        // + some bytes for the deep objects
        long offset = 3 * 4 + 8 + 3 * Helper.sizeOfObjectRef(factor);
        if (root != null)
            return node.getMemoryUsageInBytes(factor) + offset;

        return offset;
    }

    @Override
    public int count() {
        if (root != null)
            return root.count();
        return 0;
    }
}
