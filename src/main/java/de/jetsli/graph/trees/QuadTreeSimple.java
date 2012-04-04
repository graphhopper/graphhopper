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

import de.jetsli.graph.util.BBox;
import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.reader.CalcDistance;
import de.jetsli.graph.util.*;
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
 * Warning: cannot store null values - an exception will be thrown
 *
 * @author Peter Karich, info@jetsli.de
 */
public class QuadTreeSimple<T> implements QuadTree<T> {

    public static Acceptor<CoordTrig> acceptAll = new Acceptor<CoordTrig>() {

        @Override public boolean accept(CoordTrig entry) {
            return true;
        }
    };

    public static class AcceptInDistance implements Acceptor<CoordTrig> {

        CalcDistance calc;
        float lat;
        float lon;
        double normalizedDist;

        public AcceptInDistance(CalcDistance calc, float lat, float lon, float distInKm) {
            this.calc = calc;
            this.lat = lat;
            this.lon = lon;
            // add 10cm to reduce rounding mistakes and requires no comparing
            this.normalizedDist = distInKm + 1e-4f;
            // now apply some transformation to use the faster distance calculation
            // normalizedDist = Math.cos(normalizedDist / CalcDistance.R);
        }

        @Override public boolean accept(CoordTrig entry) {
            // TODO use an even faster method!! e.g. simple pythagoras without sqrt(): x^2 + y^2 + z^2
            return calc.calcDistKm(lat, lon, entry.lat, entry.lon) < normalizedDist;
//            return calc.calcDistFaster(lat, lon, lat, lon) < normalizedDist;
        }
    };
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
        // maximum precision which fits into a 'long'
        this(entriesPerLeafNode, 64);
    }

    public QuadTreeSimple(int entriesPerLeafNode, int bitsForLatLon) {
        if (bitsForLatLon > 64)
            throw new IllegalStateException("Precision is too high and does not fit into 8 bytes");

        mbits = bitsForLatLon;
        entriesPerLeaf = entriesPerLeafNode;
        globalMaxBit = 1L << (bitsForLatLon - 1);
        algo = new SpatialKeyAlgo().init(bitsForLatLon);
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
    public T put(float lat, float lon, T value) {
        if (value == null)
            throw new IllegalArgumentException("This quad tree does not support null values");

        long maxBit = globalMaxBit;
        long spatialKey = algo.encode(lat, lon);
        if (root == null) {
            size++;
            QTDataNode<T> d = new QTDataNode<T>(entriesPerLeaf);
            d.put(spatialKey, value);
            root = d;
            return null;
        }
        QTNode<T> previousBranch = null;
        int previousNum = -1;
        QTNode<T> current = root;
        // System.out.println("sp.key:" + BitUtil.toBitString(spatialKey, mbits));
        while (maxBit != 0) {
            // System.out.println("maxbit:" + BitUtil.toBitString(maxBit, mbits));            
            if (current.hasData()) {
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

            previousBranch = current;
            // latitude
            previousNum = (spatialKey & maxBit) == 0 ? 0 : 2;
            maxBit >>>= 1;
            // longitude
            if ((spatialKey & maxBit) != 0)
                previousNum |= 1;
            maxBit >>>= 1;
            current = current.get(previousNum);
            if (current == null) {
                current = new QTDataNode<T>(entriesPerLeaf);
                previousBranch.set(previousNum, current);
            }

        }

        throw new UnsupportedOperationException("Cannot put element?? spatial key:" + spatialKey + " value:" + value);
    }

    @Override
    public T get(float lat, float lon) {
        if (root == null)
            return null;

        long spatialKey = algo.encode(lat, lon);
        QTNode<T> current = root;
        long maxBit = globalMaxBit;
        int num;
        while (maxBit != 0) {
            if (current.hasData())
                return (T) ((QTDataNode) current).getValue(spatialKey);

            // latitude
            num = (spatialKey & maxBit) == 0 ? 0 : 2;
            maxBit >>>= 1;
            // longitude
            if ((spatialKey & maxBit) != 0)
                num |= 1;
            maxBit >>>= 1;
            current = current.get(num);
            if (current == null)
                return null;
        }

        // not found
        return null;
    }

    @Override
    public boolean remove(float lat, float lon) {
        if (root == null)
            return false;

        long spatialKey = algo.encode(lat, lon);
        QTNode<T> previous = null;
        int previousNum = -1;
        QTNode<T> current = root;
        long maxBit = globalMaxBit;
        while (maxBit != 0) {
            if (current.hasData()) {
                QTDataNode<T> dataNode = (QTDataNode<T>) current;
                boolean found = dataNode.remove(spatialKey);
                if (!found)
                    return false;

                size--;
                if (!dataNode.isEmpty())
                    return true;

                // Now underflow! -> TODO remove lengthly tail of branches -> going from root again or temp save branches?

                // Remove data node 'current' and branch node 'previous'.
                if (previous == null) {
                    root = null;
                    return true;
                }

                previous.set(previousNum, null);
                return true;
            }

            previous = current;
            // latitude
            previousNum = (spatialKey & maxBit) == 0 ? 0 : 2;
            maxBit >>>= 1;
            // longitude
            if ((spatialKey & maxBit) != 0)
                previousNum |= 1;
            maxBit >>>= 1;
            current = current.get(previousNum);
        }

        // not found
        return false;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Collection<CoordTrig<T>> getNeighbours(float lat, float lon, float distanceInKm) {
        List<CoordTrig<T>> list = new ArrayList<CoordTrig<T>>();
        AcceptInDistance distanceAcceptor = new AcceptInDistance(calc, lat, lon, distanceInKm);
        getNeighbours(BBox.createEarthMax(), BBox.create(lat, lon, distanceInKm, calc), root,
                distanceAcceptor, list);
        return list;
    }

    @Override
    public Collection<CoordTrig<T>> getNeighbours(BBox boundingBox) {
        List<CoordTrig<T>> list = new ArrayList<CoordTrig<T>>();
        getNeighbours(BBox.createEarthMax(), boundingBox, root, acceptAll, list);
        return list;
    }

    private void getNeighbours(BBox nodeBB, BBox searchRect, QTNode current,
            Acceptor<CoordTrig> acceptor, Collection<CoordTrig<T>> result) {
        if (current == null)
            return;

        if (current.hasData()) {
            QTDataNode<T> dataNode = (QTDataNode<T>) current;
            for (int i = 0; i < dataNode.values.length; i++) {
                if (dataNode.values[i] == null)
                    break;

                CoordTrigObjEntry f = new CoordTrigObjEntry();
                algo.decode(dataNode.keys[i], f);
                f.setValue(dataNode.values[i]);
                if (acceptor.accept(f))
                    result.add(f);
            }
            return;
        }

        // TODO avoid rounding error and more expensive intersect comparisons via transforming 
        //      search bbox into spatialKey?
        float lat12 = (nodeBB.lat1 + nodeBB.lat2) / 2;
        float lon12 = (nodeBB.lon1 + nodeBB.lon2) / 2;

        // top-left - see SpatialKeyAlgo that latitude goes from bottom to top and is 1 if on top
        // 10 11
        // 00 01
        QTNode<T> node10 = current.get(2);
        if (node10 != null) {
            BBox nodeRect10 = new BBox(nodeBB.lat1, nodeBB.lon1, lat12, lon12);
            if (searchRect.intersect(nodeRect10))
                getNeighbours(nodeRect10, searchRect, node10, acceptor, result);
        }

        // top-right
        QTNode<T> node11 = current.get(3);
        if (node11 != null) {
            BBox nodeRect11 = new BBox(nodeBB.lat1, lon12, lat12, nodeBB.lon2);
            if (searchRect.intersect(nodeRect11))
                getNeighbours(nodeRect11, searchRect, node11, acceptor, result);
        }

        // bottom-left
        QTNode<T> node00 = current.get(0);
        if (node00 != null) {
            BBox nodeRect00 = new BBox(lat12, nodeBB.lon1, nodeBB.lat2, lon12);
            if (searchRect.intersect(nodeRect00))
                getNeighbours(nodeRect00, searchRect, node00, acceptor, result);
        }

        // bottom-right
        QTNode<T> node01 = current.get(1);
        if (node01 != null) {
            BBox nodeRect01 = new BBox(lat12, lon12, nodeBB.lat2, nodeBB.lon2);
            if (searchRect.intersect(nodeRect01))
                getNeighbours(nodeRect01, searchRect, node01, acceptor, result);
        }
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
}
