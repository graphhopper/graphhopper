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
import de.jetsli.graph.reader.MiniPerfTest;
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
 * due to the QuadTree interface. Although SpatialKeyHashtable is exactly the same idea - except
 * that no neighbor search was implemented there.
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
        final Graph g = OSMReaderTrials.defaultRead(args[0], "/tmp/mmap-graph");
        final int locs = g.getLocations();
        System.out.println("graph contains " + locs + " nodes");

        // make sure getBucketIndex is okayish fast
//        new MiniPerfTest("test") {
//
//            @Override
//            public long doCalc(int run) {
//                try {
//                    SpatialKeyTree qt = new SpatialKeyTree(10, 4).init(locs);
//                    PerfTest.fillQuadTree(qt, g);
//                    return qt.size();
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                    return -1;
//                }
//            }
//        }.setMax(50).start();

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
        MainPool pool = MainPool.getDefault();
        XYVectorInterface rms = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        rms.setTitle("RMS");
        XYVectorInterface max = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        max.setTitle("MAX");
        panel.addData(rms);
        panel.addData(max);

        try {
            for (int skipLeft = 0; skipLeft < 40; skipLeft += 2) {
                int entriesPerBuck = 3;
//            for (; entriesPerBuck < 20; entriesPerBuck += 8) {
                SpatialKeyTree qt = new SpatialKeyTree(skipLeft, entriesPerBuck).init(locs);
                int epb = qt.getEntriesPerBucket();
                String title = "skipLeft:" + skipLeft + " entries/buck:" + epb;
                PerfTest.fillQuadTree(qt, g);
                XYVectorInterface data = qt.getHist(title);
                HistogrammInterface hist = (HistogrammInterface) data.getY();
                // mean value is irrelevant as it is always the same
                max.add(skipLeft, hist.getMax());
                rms.add(skipLeft, hist.getRMSError());
                System.out.println("\n\n" + new Date() + "#### " + title + " max:" + hist.getMax() + " rms:" + hist.getRMSError());
                panel.repaint();

                // panel.automaticOneScale(data);
//                String str = qt.toDetailString();
//                if (!str.isEmpty()) {
//                    System.out.print("\nentriesPerBucket:" + epb + "      ");
//                    System.out.println(str);
//                }
//            }
            }
        } catch (Exception ex) {
            // do not crash the UI if 'overflow'
            ex.printStackTrace();
        }
    }
    private static final int BITS8 = 8;
    private ByteBuffer bucketBytes;
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
    private int bucketIndexBits;

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
    // * no explicit overflow area -> use the same buckets and store in one byte the number of overflowing entries
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
        // one unused byte in spatial key (making things a bit faster) => but still higher precision than float
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
        bucketIndexBits = (int) (Math.log(maxBuckets) / Math.log(2));

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
    }

    public SpatialKeyAlgo getAlgo() {
        return algo;
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
        
        // | unusedBits | skipBeginning | bucketIndexBits | veryRightSide | skipEnd |
        // result is bucketIndexBits ^= veryRightSide
        
        long veryRightSide = spatialKey;
        veryRightSide <<= bucketIndexBits + skipKeyBeginningBits;
        veryRightSide >>>= 8 * BITS8 - bucketIndexBits;

        spatialKey <<= skipKeyBeginningBits;
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        spatialKey >>>= skipKeyBeginningBits;
        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        spatialKey >>>= skipKeyEndBits;
        spatialKey ^= veryRightSide;

        // maxBuckets is a power of two so x-1 is very likely 'some kind' of prime number :)
        // spatialKey %= maxBuckets - 1;

        // bit operations are ~20% faster but there is only this equivalence: x % 2^n == x & (2^n - 1) 
        // which is not sufficient for a good distribution. We would need: x % (2^n-1)= ..

        // System.out.println(BitUtil.toBitString(spatialKey, 64));
        if (spatialKey >= maxBuckets || spatialKey < 0)
            throw new IllegalStateException("Index devived from spatial key is to high or negative!? " + spatialKey
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

    public XYVectorInterface getHist(String title) {
        MainPool pool = MainPool.getDefault();

        // 1. simplist possibility
        // XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, DoubleVectorInterface.class);
        // 2. possibility with histogramm
        // HistogrammInterface hist = pool.createVector(HistogrammInterface.class); VectorInterface x = pool.createVector(DoubleVectorInterface.class);
        // 3.
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int i = 0; i < maxBuckets; i++) {
            xy.add(i, usedEntries.get(i));
        }
        xy.setTitle(title);
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
        int whichI = -1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            int v = stats.get(i);
            if (v > 0) {
                maxFill = v;
                whichI = i;
            }
            sb.append(v).append("\t");
        }
        if (maxFill > 200)
            return "";

        return sb.toString() + " maxFill:" + maxFill + "[" + whichI + "]";
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
