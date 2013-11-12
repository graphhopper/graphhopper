/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.trees;

import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.CoordTrig;
import com.graphhopper.util.shapes.CoordTrigObjEntry;
import com.graphhopper.util.shapes.Shape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple implementation of a spatial index via a spatial key trie - the normal java way (a bit
 * memory intensive with all those object references).
 * <p/>
 * TODO depth is too large!
 * <p/>
 * The latitude and longitude is encoded via our spatial key - see SpatialKeyAlgo for more details.
 * <p/>
 * If the branch node would have only 2 children then it would be a binary tree - we would need to
 * shift only once. If the branch node would have 8 children then it would be an oct tree - shifting
 * 3 times.
 * <p/>
 * Warning: cannot store null values - an exception will be thrown.
 * <p/>
 * Duplicates allowed.
 * <p/>
 * @author Peter Karich
 */
public class QuadTreeSimple<T> implements QuadTree<T>
{
    private static class Acceptor<T> implements LeafWorker<T>
    {
        public final List<CoordTrig<T>> result = new ArrayList<CoordTrig<T>>();
        SpatialKeyAlgo algo;

        public Acceptor( SpatialKeyAlgo algo )
        {
            this.algo = algo;
        }

        @Override
        public void doWork( QTDataNode<T> dataNode, int i )
        {
            CoordTrigObjEntry<T> entry = new CoordTrigObjEntry<T>();
            algo.decode(dataNode.keys[i], entry);
            if (accept(entry))
            {
                entry.setValue(dataNode.values[i]);
                result.add(entry);
            }
        }

        public boolean accept( CoordTrig<T> entry )
        {
            return true;
        }
    }
    private final int mbits;
    private final long globalMaxBit;
    private final SpatialKeyAlgo algo;
    private final int entriesPerLeaf;
    private DistanceCalc calc = new DistanceCalcEarth();
    private int size;
    private QTNode<T> root;

    public QuadTreeSimple()
    {
        this(1, 64);
    }

    public QuadTreeSimple( int entriesPerLeafNode )
    {
        this(entriesPerLeafNode, 64);
    }

    public QuadTreeSimple( int entriesPerLeafNode, int bitsForLatLon )
    {
        mbits = bitsForLatLon;
        entriesPerLeaf = entriesPerLeafNode;
        globalMaxBit = 1L << (bitsForLatLon - 1);
        algo = new SpatialKeyAlgo(bitsForLatLon);
    }

    public QuadTreeSimple setCalcDistance( DistanceCalc dist )
    {
        this.calc = dist;
        return this;
    }

    @Override
    public long getSize()
    {
        return size;
    }

    @Override
    public QuadTreeSimple init( long maxItemsHint )
    {
        return this;
    }

    @Override
    public void add( double lat, double lon, T value )
    {
        if (value == null)
        {
            throw new IllegalArgumentException("This quad tree does not support null values");
        }

        long spatialKey = algo.encode(lat, lon);
        long maxBit = globalMaxBit;
        if (root == null)
        {
            size++;
            QTDataNode<T> d = new QTDataNode<T>(entriesPerLeaf);
            d.add(spatialKey, value);
            root = d;
            return;
        }
        QTBranchNode<T> previousBranch = null;
        int previousNum = -1;
        QTNode<T> current = root;
        while (maxBit != 0)
        {
            if (current.hasData())
            {
                addData(spatialKey, value, current, previousBranch, previousNum, maxBit);
                return;
            }

            previousBranch = (QTBranchNode<T>) current;
            // latitude
            previousNum = (spatialKey & maxBit) == 0 ? 0 : 2;
            maxBit >>>= 1;
            // longitude
            if ((spatialKey & maxBit) == 0)
            {
                current = previousNum == 0 ? previousBranch.node0 : previousBranch.node2;
            } else
            {
                current = previousNum == 0 ? previousBranch.node1 : previousBranch.node3;
                previousNum++;
            }
            maxBit >>>= 1;
            if (current == null)
            {
                current = new QTDataNode<T>(entriesPerLeaf);
                previousBranch.set(previousNum, current);
            }
        }

        throw new UnsupportedOperationException("Cannot put element? Too many entries per area? Try increasing entries per leaf! "
                + lat + "," + lon + " spatial key:" + spatialKey + " value:" + value + " size:" + size);
    }

    private void addData( long spatialKey, T value, QTNode<T> current, QTNode<T> previousBranch,
            int previousNum, long maxBit )
    {
        size++;

        QTDataNode<T> dataNode = (QTDataNode<T>) current;
        boolean overflow = dataNode.add(spatialKey, value);
        if (!overflow)
        {
            return;
        }

        QTBranchNode<T> n = new QTBranchNode<T>();
        if (previousBranch != null)
        {
            previousBranch.set(previousNum, n);
        } else
        {
            root = n;
        }

        int num;
        MAIN:
        for (; maxBit != 0; maxBit >>>= 2)
        {
            for (num = 0; num < 4; num++)
            {
                QTDataNode<T> dn = new QTDataNode<T>(entriesPerLeaf);
                overflow = dn.overwriteFrom(num, maxBit, dataNode, spatialKey, value);
                if (overflow)
                {
                    if ((maxBit & 0x3) != 0)
                    {
                        // if the dataNode contains duplicates or if too many nodes have a very similar position
                        // it couldn't be splitted, so we need to increase size
                        dn.ensure(dn.keys.length + 1);
                        dn.add(spatialKey, value);
                        n.set(num, dn);
                        continue;
                    } else
                    {
                        // current node would be full so divide it again
                        QTBranchNode<T> tmp = new QTBranchNode<T>();
                        n.set(num, tmp);
                        n = tmp;
                        continue MAIN;
                    }
                } else if (!dn.isEmpty())
                {
                    n.set(num, dn);
                    continue;
                }
            }

            return;
        }

        throw new AssertionError("Cannot happen? datanode:" + dataNode + " new entry:" + spatialKey + "->" + value);
    }

    @Override
    public int remove( double lat, double lon )
    {
        if (root == null)
        {
            return 0;
        }

        final long spatialKey = algo.encode(lat, lon);
        final AtomicInteger removedWrapper = new AtomicInteger(0);
        LeafWorker<T> worker = new LeafWorker<T>()
        {
            @Override
            public void doWork( QTDataNode<T> entry, int i )
            {
                int removed = entry.remove(spatialKey);
                if (removed > 0)
                {
                    removedWrapper.addAndGet(removed);
                    size -= removed;
                }
            }
        };
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err), root, worker);
        return removedWrapper.get();
    }

    @Override
    public boolean isEmpty()
    {
        return size == 0;
    }

    @Override
    public Collection<CoordTrig<T>> getNodesFromValue( final double lat, final double lon, final T value )
    {
        if (root == null)
        {
            return Collections.emptyList();
        }

        final long spatialKey = algo.encode(lat, lon);
        final List<CoordTrig<T>> nodes = new ArrayList<CoordTrig<T>>(1);
        LeafWorker<T> worker = new LeafWorker<T>()
        {
            @Override
            public void doWork( QTDataNode<T> dataNode, int i )
            {
                if (value != null && !value.equals(dataNode.values[i]))
                {
                    return;
                }

                if (dataNode.keys[i] == spatialKey)
                {
                    CoordTrig<T> ret = new CoordTrigObjEntry<T>();
                    algo.decode(dataNode.keys[i], ret);
                    ret.setValue(dataNode.values[i]);
                    nodes.add(ret);
                }
            }
        };
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err), root, worker);
        return nodes;
    }

    @Override
    public Collection<CoordTrig<T>> getNodes( double lat, double lon, double distanceInMeter )
    {
        return getNodes(new Circle(lat, lon, distanceInMeter, calc));
    }

    @Override
    public Collection<CoordTrig<T>> getNodes( final Shape boundingBox )
    {
        if (root == null)
        {
            return Collections.emptyList();
        }

        Acceptor<T> worker = new Acceptor<T>(algo)
        {
            @Override
            public boolean accept( CoordTrig<T> entry )
            {
                return boundingBox.contains(entry.lat, entry.lon);
            }
        };
        getNeighbours(BBox.createEarthMax(), boundingBox, root, worker);
        return worker.result;
    }

    @SuppressWarnings("unchecked")
    private void getNeighbours( BBox nodeBB, Shape searchRect, QTNode<T> current, LeafWorker<T> worker )
    {
        if (current.hasData())
        {
            QTDataNode<T> dataNode = (QTDataNode<T>) current;
            for (int i = 0; i < dataNode.values.length; i++)
            {
                if (dataNode.values[i] == null)
                {
                    break;
                }

                worker.doWork(dataNode, i);
            }
            return;
        }

        double lat12 = (nodeBB.maxLat + nodeBB.minLat) / 2;
        double lon12 = (nodeBB.minLon + nodeBB.maxLon) / 2;

        // top-left - see SpatialKeyAlgo that latitude goes from bottom to top and is 1 if on top
        // 10 11
        // 00 01
        QTNode<T> node10 = current.get(2);
        if (node10 != null)
        {
            BBox nodeRect10 = new BBox(nodeBB.minLon, lon12, lat12, nodeBB.maxLat);
            if (searchRect.intersect(nodeRect10))
            {
                getNeighbours(nodeRect10, searchRect, node10, worker);
            }
        }

        // top-right
        QTNode<T> node11 = current.get(3);
        if (node11 != null)
        {
            BBox nodeRect11 = new BBox(lon12, nodeBB.maxLon, lat12, nodeBB.maxLat);
            if (searchRect.intersect(nodeRect11))
            {
                getNeighbours(nodeRect11, searchRect, node11, worker);
            }
        }

        // bottom-left
        QTNode<T> node00 = current.get(0);
        if (node00 != null)
        {
            BBox nodeRect00 = new BBox(nodeBB.minLon, lon12, nodeBB.minLat, lat12);
            if (searchRect.intersect(nodeRect00))
            {
                getNeighbours(nodeRect00, searchRect, node00, worker);
            }
        }

        // bottom-right
        QTNode<T> node01 = current.get(1);
        if (node01 != null)
        {
            BBox nodeRect01 = new BBox(lon12, nodeBB.maxLon, nodeBB.minLat, lat12);
            if (searchRect.intersect(nodeRect01))
            {
                getNeighbours(nodeRect01, searchRect, node01, worker);
            }
        }
    }

    @Override
    public void clear()
    {
        root = null;
        size = 0;
    }

    @Override
    public String toDetailString()
    {
        StringBuilder sb = new StringBuilder();
        List<QTNode<T>> newList = new ArrayList<QTNode<T>>();
        List<QTNode<T>> list = new ArrayList<QTNode<T>>();
        list.add(root);
        int counter = 0;
        int level = 0;
        while (true)
        {
            if (counter >= list.size())
            {
                if (newList.isEmpty())
                {
                    break;
                }

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

    private void toDetailString( QTNode<T> current, StringBuilder sb, List<QTNode<T>> list )
    {
        if (current == null)
        {
            sb.append("dn:null\t");
            return;
        }

        if (current.hasData())
        {
            sb.append(((QTDataNode) current).toString(algo)).append("\t");
        } else
        {
            sb.append("B\t");
            list.add(current.get(2));
            list.add(current.get(3));
            list.add(current.get(0));
            list.add(current.get(1));
        }
    }

    @Override
    public long getMemoryUsageInBytes( int factor )
    {
        QTNode node = root;

        // + some bytes for the deep objects
        long offset = 3 * 4 + 8 + 3 * Helper.getSizeOfObjectRef(factor);
        if (root != null)
        {
            return node.getMemoryUsageInBytes(factor) + offset;
        }

        return offset;
    }

    @Override
    public long getEmptyEntries( boolean onlyBranches )
    {
        if (root != null)
        {
            return root.getEmptyEntries(onlyBranches);
        }

        return 0;
    }

    public int count()
    {
        if (root != null)
        {
            return root.count();
        }
        return 0;
    }
}
