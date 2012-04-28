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
package de.jetsli.graph.geohash;

import de.genvlin.core.data.*;
import de.genvlin.gui.plot.GPlotPanel;
import de.jetsli.graph.reader.OSMReaderTrials;
import de.jetsli.graph.reader.PerfTest;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.*;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.shapes.Shape;
import gnu.trove.map.hash.TIntIntHashMap;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * This class maps latitude and longitude through there spatial key to integeger values like osm
 * ids, geo IPs, references or similar.
 *
 * It is similar to SpatialKeyHashtable but should be more robust and more applicable to real world
 * due to the QuadTree interface. Although SpatialKeyHashtable is exactly the same idea no neighbor
 * search was implemented there.
 *
 * Another feature of this implementation is to move the "bucket-index-window" to the front of the
 * spatial key (ie. skipKeyEndBits is maximal). Then it'll behave like a normal quadtree but will
 * have too many collisions/overflows. If you move the window to the end of the key (ie.
 * skipKeyEndBits is minimal) then this class will behave like a good spatial key hashtable
 * (collisions are minimal), but the neighbor searches are most inefficient.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyTree implements QuadTree<Integer> {

    public static void main(String[] args) throws Exception {
        Graph g = OSMReaderTrials.defaultRead(args[0], "/tmp/mmap-graph");
        int locs = g.getLocations();
        System.out.println("graph contains " + locs + " nodes");

        final GPlotPanel panel = new GPlotPanel();
        // PluginPool.getDefault().add(new ComponentPlatform(panel));
        SwingUtilities.invokeLater(new Runnable() {

            @Override public void run() {
                int frameHeight = 800;
                int frameWidth = 1200;
                JFrame frame = new JFrame("UI - Fast&Ugly");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(frameWidth, frameHeight);
                frame.add(panel.getComponent());
                frame.setVisible(true);
            }
        });

        // for this OSM a smaller skipLeft is hopeless => the maximul fill would be more than 5000
        for (int i = 14; i < 20; i++) {
            for (int entriesPerBuck = 3; entriesPerBuck < 20; entriesPerBuck += 8) {
                SpatialKeyTree qt = new SpatialKeyTree(i, entriesPerBuck).init(locs);
                int epb = qt.getEntriesPerBucket();
                System.out.println("\n\n" + new Date() + "#### skipLeft:" + i + " entries/buck:" + epb);
                PerfTest.fillQuadTree(qt, g);
                XYVectorInterface data = qt.getXY(500);
                data.setTitle("skipLeft:" + i + " e/b:" + epb);
                panel.addData(data);

                // panel.automaticOneScale(data);

                String str = qt.toDetailString();
                if (!str.isEmpty()) {
                    System.out.print("\nentriesPerBucket:" + epb + "      ");
                    System.out.println(str);
                }
            }
        }
    }
    private static final int BITS8 = 8;
    private ByteBuffer bucketBytes;
    private ByteBuffer overflowBytes;
    private int bytesPerBucket;
    private int bytesPerEntry;
    private int entriesPerBucket;
    private int skipKeyBeginningBits, skipKeyEndBits;
    private int bytesPerRest;
    private int size;
    private int maxBuckets;
    private SpatialKeyAlgo algo;
    private IntBuffer usedEntries;
    private int spatialKeyBits;

    public SpatialKeyTree() {
        this(8, 3);
    }

    public SpatialKeyTree(int skipKeyBeginningBits) {
        this(skipKeyBeginningBits, 3);
    }

    public SpatialKeyTree(int skipKeyBeginningBits, int initialEntriesPerBucket) {
        this.skipKeyBeginningBits = skipKeyBeginningBits;
        this.entriesPerBucket = initialEntriesPerBucket;
    }

    // GOALS:
    // * memory efficient: 8bytes per entry, but this should apply for smaller collections too and:
    // * relative simple implementation ("safe bytes not bits")
    // * thread safe
    // * moving bucket-index-window to configure between hashtable and quadtree
    // * implement neighbor search
    // * allow duplicate keys => only a "List get(key, distance)" method
    // * implement removing via distance search => TODO change QuadTree interface
    // * possibility to increase size => multiple mmap files or efficient copy + close + reinit?
    // * no "is-it-really-empty?" problem => bitset for used entries
    // * no integer limit (~500mio) due to the use of ByteBuffer.get(*int*) => use multiple bytebuffers!
    @Override
    public SpatialKeyTree init(int maxEntries) throws Exception {
        initKey();
        initBucketSizes(maxEntries);
        initBuffers();
        usedEntries = ByteBuffer.allocateDirect(maxBuckets * 4).asIntBuffer();
        return this;
    }

    protected void initKey() {
        // TODO calculate necessary spatial key precision (=>unusedBits) and maxBuckets from maxEntries
        //
        // one unused byte in spatial key => but still higher precision than float
        int unusedBits = BITS8;
        spatialKeyBits = 8 * BITS8 - unusedBits;
        algo = new SpatialKeyAlgo(spatialKeyBits);

        // skip the first byte for the bucket index + the unused byte        
        if (skipKeyBeginningBits < 0)
            skipKeyBeginningBits = BITS8 + unusedBits;
        else
            skipKeyBeginningBits += unusedBits;
    }

    int correctDivide(int val, int div) {
        if (val % div == 0)
            return val / div;
        else
            return val / div + 1;
    }

    protected void initBucketSizes(int maxEntries) {
        // 2^(3 * 8) = 16mio bytes to overflow
        int bytesForOverflowLink = getBytesForOverflowLink();
        int bytesPerValue = getBytesPerValue();
        maxBuckets = correctDivide(maxEntries, entriesPerBucket);

        // Always use lower bits to guarantee that all indices are smaller than maxBuckets
        int bucketIndexBits = (int) (Math.log(maxBuckets) / Math.log(2));

        // now adjust maxBuckets and entriesPerBucket to avoid memory waste and fit a power of 2
        maxBuckets = (int) Math.pow(2, bucketIndexBits);
        entriesPerBucket = correctDivide(maxEntries, maxBuckets);
        // Bytes which are not encoded as bucket index needs to be stored => 'rest' bytes
        bytesPerRest = correctDivide(spatialKeyBits - bucketIndexBits, BITS8);
        bytesPerEntry = bytesPerRest + bytesPerValue;
        bytesPerBucket = entriesPerBucket * bytesPerEntry + bytesForOverflowLink;
        skipKeyEndBits = 8 * BITS8 - skipKeyBeginningBits - bucketIndexBits;
        if (skipKeyEndBits < 0)
            throw new IllegalStateException("Too many entries (" + maxEntries + "). Try to "
                    + "reduce them, avoid a big skipBeginning (" + skipKeyBeginningBits
                    + ") or increase spatialKeyBits (" + spatialKeyBits + ")");
    }

    protected void initBuffers() {
        // 500mio entries maximum due to the use of *int* in byteBuffer.get(int)
        int capacity = maxBuckets * bytesPerBucket;
        if (capacity < 0)
            throw new IllegalStateException("Too many elements. TODO: use multiple buffers to workaround 4GB limitation");

        bucketBytes = ByteBuffer.allocateDirect(capacity);
        overflowBytes = ByteBuffer.allocateDirect(capacity / 20);
    }

    protected int getBytesForOverflowLink() {
        return 3;
    }

    protected int getBytesPerValue() {
        return 4;
    }

    protected int getEntriesPerBucket() {
        return entriesPerBucket;
    }

    public int getMaxBuckets() {
        return maxBuckets;
    }

    int getBucketIndex(long spatialKey) {
        // IMPORTANT: there is no need for bucket index to be a multiple of 8. Though memory savings 
        // will be maximized then, because then *all* bits encoded as bucket index are not stored as key

        // 2^16 * 6       =      ~400 k
        // 2^20 * 3 .. 12 =     ~3-12 mio
        // 2^24 * 3 .. 12 =   ~50-200 mio                
        // 2^28 * 3 ..  6 = ~800-1600 mio -> not possible to address this in a bytebuffer (int index!)
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        spatialKey <<= skipKeyBeginningBits;
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        spatialKey >>>= skipKeyBeginningBits;
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        spatialKey >>>= skipKeyEndBits;
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        if (spatialKey >= maxBuckets)
            throw new IllegalStateException("Index devived from spatial key is to high!? " + spatialKey
                    + " vs. " + maxBuckets + " skipBeginning:" + skipKeyBeginningBits
                    + " skipEnd:" + skipKeyEndBits + " log(index):" + Math.log(spatialKey) / Math.log(2));

        return (int) spatialKey;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void add(double lat, double lon, Integer value) {
        long key = algo.encode(lat, lon);
        int bucketIndex = getBucketIndex(key);
        int res = usedEntries.get(bucketIndex);
        usedEntries.put(bucketIndex, res + 1);
        size++;
    }

    @Override
    public int remove(double lat, double lon) {
        algo.encode(lat, lon);
        return 0;
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodes(final double lat, final double lon, final double distanceInKm) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodes(Shape boundingBox) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodesFromValue(final double lat, final double lon, final Integer value) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public void clear() {
        // nextOverflow = 0;
        size = 0;
        initBuffers();
    }

    public XYVectorInterface getXY(int max) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, DoubleVectorInterface.class);
        for (int i = 0; i < maxBuckets; i++) {
            xy.add(i, usedEntries.get(i));
        }
        return xy;
    }

    public TIntIntHashMap getStats(int max) {
        TIntIntHashMap stats = new TIntIntHashMap(max);
        for (int i = 0; i < max; i++) {
            stats.put(i, 0);
        }
        for (int i = 0; i < maxBuckets; i++) {
            stats.increment(usedEntries.get(i));
        }
        return stats;
    }

    @Override
    public String toDetailString() {
        int max = 100;
        TIntIntHashMap stats = getStats(max);
        int maxFill = -1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            int v = stats.get(i);
            if (i > 0 && v > maxFill)
                maxFill = v;
            sb.append(v).append("\t");
        }
        if (maxFill > 200)
            return "";

        return sb.toString() + " maxFill:" + maxFill;
    }

    @Override
    public long getMemoryUsageInBytes(int factor) {
        return maxBuckets * bytesPerBucket;
    }

    @Override
    public long getEmptyEntries(boolean onlyBranches) {
        int counter = 0;
        for (int i = 0; i < maxBuckets; i++) {
            if (usedEntries.get(i) == 0)
                counter++;
        }
        return counter;
    }
};
