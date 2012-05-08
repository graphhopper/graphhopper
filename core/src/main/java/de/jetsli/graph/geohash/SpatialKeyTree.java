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
import de.jetsli.graph.geohash.SpatialKeyTree.BucketOverflowLoop;
import de.jetsli.graph.reader.OSMReaderTrials;
import de.jetsli.graph.reader.PerfTest;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.*;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.CoordTrigIntEntry;
import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.util.shapes.Circle;
import de.jetsli.graph.util.shapes.Shape;
import gnu.trove.map.hash.TIntIntHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

        try {
            for (int skipLeft = 0; skipLeft < 40; skipLeft += 2) {
                int entriesPerBuck = 3;
                for (; entriesPerBuck < 20; entriesPerBuck += 8) {
                    SpatialKeyTree qt = new SpatialKeyTree(skipLeft, entriesPerBuck).init(locs);
                    int epb = qt.getEntriesPerBucket();
                    String title = "skipLeft:" + skipLeft + " entries/buck:" + epb;
                    System.out.println(title);
                    PerfTest.fillQuadTree(qt, g);

                    XYVectorInterface entries = qt.getEntries("E " + title);
                    XYVectorInterface overflow = qt.getOverflowEntries("O " + title);
                    XYVectorInterface overflowOff = qt.getOverflowOffset("OO " + title);
                    panel.addData(entries);
                    panel.addData(overflow);
                    panel.addData(overflowOff);
//                    XYVectorInterface data = qt.getHist(title);
//                    HistogrammInterface hist = (HistogrammInterface) data.getY();
                    // mean value is irrelevant as it is always the same                    
//                    System.out.println("\n\n" + new Date() + "#### " + title + " max:" + hist.getMax() + " rms:" + hist.getRMSError());
                    panel.repaint();

                    // panel.automaticOneScale(data);
//                String str = qt.toDetailString();
//                if (!str.isEmpty()) {
//                    System.out.print("\nentriesPerBucket:" + epb + "      ");
//                    System.out.println(str);
//                }
                }
            }
        } catch (Exception ex) {
            // do not crash the UI if 'overflow'
            ex.printStackTrace();
        }
    }
    private int size;
    private int maxBuckets;
    private SpatialKeyAlgo algo;
    private boolean compressKey = true;
    // bits & byte stuff
    private static final int BITS8 = 8;
    private ByteBuffer storage;
    private int bytesPerBucket;
    private int bytesPerEntry;
    private int bytesPerOverflowEntry;
    private int maxEntriesPerBucket;
    // key compression
    private int skipKeyBeginningBits, skipKeyEndBits;
    private int bytesPerKeyRest;
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
        this.maxEntriesPerBucket = initialEntriesPerBucket;
    }

    // REQUIREMENTS:
    // * memory efficient spatial storage, even for smaller collections of data
    // * relative simple implementation ("safe bytes not bits")
    // * moving bucket-index-window to configure between hashtable and quadtree 
    //   -> avoid configuration, auto-determine necessary window
    // * implement neighbor search
    // * allow duplicate keys => only a "List get(key, distance)" method
    // * implement removing via distance search => TODO change QuadTree interface
    // * possibility to increase size => efficient copy + close + reinit
    //   see Netty's DynamicChannelBuffer.ensureWritableBytes(int minWritableBytes)
    // * there should be no problem to identify if an entry is empty or not => length for used entries, 
    //   offset byte for overflow entries which are > 0
    // * no explicit overflow area -> use the same buckets and use one byte in an overflow entries 
    //   to indentify the origin of it
    //
    // LATER GOALS:
    // * thread safe
    //   ByteBuffer is not thread safe, though we could a lock object per index or simply using Read+WriteLocks
    // * no integer limit due to the use of ByteBuffer.get(*int*) => use multiple bytebuffers 
    //  -> see FatBuffer.java or ByteBufferLongBigList.java from it.unimi.dsi dsiutils (grepcode)!
    //   would be a lot slower due to i1=longIndex/len;i2=longIndex%len;
    // * extract general purpose big-hashtable. ie. store less bytes for key (long/int)
    //   we would need spatialKeyAlgo.encode(lat,lon,bytes,iterations), getBucketIndex(bytes), add(byte[] bytes, int value)
    @Override
    public SpatialKeyTree init(long maxEntries) throws Exception {
        initKey();
        initBucketSizes((int) maxEntries);
        initBuffers();
        return this;
    }

    public SpatialKeyTree setCompressKey(boolean compressKey) {
        this.compressKey = compressKey;
        return this;
    }

    protected void initKey() {
        // TODO calculate necessary spatial key precision (=>unusedBits) from maxEntries
        //
        // one unused byte in spatial key (making encode/decode a bit faster) => but still higher precision than float
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
        maxBuckets = correctDivide(maxEntries, maxEntriesPerBucket);

        // Always use lower bits to guarantee that all indices are smaller than maxBuckets
        bucketIndexBits = (int) (Math.log(maxBuckets) / Math.log(2));

        // now adjust maxBuckets and maxEntriesPerBucket to avoid memory waste and fit a power of 2
        maxBuckets = (int) Math.pow(2, bucketIndexBits);
        // introduce hash overflow area => factor > 1
        maxEntriesPerBucket = (int) Math.round(correctDivide(maxEntries, maxBuckets) * 1.5);
        // Bytes which are not encoded as bucket index needs to be stored => 'rest' bytes
        if (compressKey) {
            bytesPerKeyRest = correctDivide(spatialKeyBits - bucketIndexBits, BITS8);
            skipKeyEndBits = 8 * BITS8 - skipKeyBeginningBits - bucketIndexBits;
            if (skipKeyEndBits < 0)
                throw new IllegalStateException("Too many entries (" + maxEntries + "). Try to "
                        + "reduce them, avoid a big skipBeginning (" + skipKeyBeginningBits
                        + ") or increase spatialKeyBits (" + spatialKeyBits + ")");
        } else {
            skipKeyEndBits = 0;
            // complete key
            bytesPerKeyRest = 8;
        }

        // if you change this getInt/putInt of value needs to be changed to!
        int bytesPerValue = 4;
        bytesPerEntry = bytesPerKeyRest + bytesPerValue;
        bytesPerOverflowEntry = bytesPerEntry + 1;
        // store used entries per bucket in one byte => maximum entries per bucket = 256
        int bytesForLength = 1;
        bytesPerBucket = maxEntriesPerBucket * bytesPerEntry + bytesForLength;
    }

    protected void initBuffers() {
        long capacity = maxBuckets * bytesPerBucket;
        if (capacity >= Integer.MAX_VALUE)
            throw new IllegalStateException("Too many elements. TODO: use multiple buffers to workaround 4GB limitation");

        storage = ByteBuffer.allocateDirect((int) capacity);
    }

    public SpatialKeyAlgo getAlgo() {
        return algo;
    }

    protected int getEntriesPerBucket() {
        return maxEntriesPerBucket;
    }

    public long getMaxBuckets() {
        return maxBuckets;
    }

    int getBucketIndex(long spatialKey) {
        if (!compressKey)
            return Math.abs((int) (spatialKey % (maxBuckets - 1)));

        // IMPORTANT: there is no need for bucket index to be a multiple of 8. Though memory savings 
        // will be maximized then, because then *all* bits encoded as bucket index are not stored as key

        // 2^16 * 6       =      ~400 k
        // 2^20 * 3 .. 12 =     ~3-12 mio
        // 2^24 * 3 .. 12 =   ~50-200 mio                
        // 2^28 * 3 ..  6 = ~800-1600 mio -> not possible to address this in a bytebuffer (int index!)        

        // | skipBeginning (incl. unusedBits) | bucketIndexBits | veryRightSide | skipEnd |
        // result is bucketIndexBits ^= veryRightSide

        long veryRightSide = spatialKey;
        veryRightSide <<= skipKeyBeginningBits + bucketIndexBits;
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

    /**
     * First part of spatialKey should be smaller than second. (In this implementation they are the
     * same length but to use more than 2^32 buckets => it is necessary to make a different length.
     * And second part is better distributed and first XOR second is better distributed only if
     * second is longer) And because it is smaller => less space consumed. Also the first part is
     * more equal to other spatialKeys => in future implementations better compressable.
     */
    long getPartOfKeyToStore(long spatialKey) {
        spatialKey <<= skipKeyBeginningBits;
        spatialKey >>>= 8 * BITS8 - bucketIndexBits;
        return spatialKey;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    //####################
    // bucket byte layout: 1 byte + maxEntriesPerBucket * bytesPerEntry
    //   | size | entry1 | entry2 | ... | empty space | ... | oe2 | overflow entry1 |
    // overflow entry layout:
    //   offset to original bucket (7 bits) | stop bit | overflow entry
    // size byte layout
    //   entries per bucket (without overflowed entries!) (7 bits) | overflowed bit - marks if the current bucket is already overflowed
    //####################
    //
    public void add(long key, int value) {
        int bucketPointer = getBucketIndex(key);
        if (compressKey) {
            // TODO compress: ie. skip parts of the key which are already stored via bucketIndex
            key = getPartOfKeyToStore(key);
        }
        // convert bucketIndex to byte pointer
        bucketPointer *= bytesPerBucket;

        if (isOverflowed(bucketPointer)) {
            bucketPointer = findExistingOverflow(bucketPointer, key);
        } else {
            int ovflPointer = bucketPointer + bytesPerBucket - bytesPerOverflowEntry;
            int ovflBytes = countOverflowBytes(ovflPointer);
            byte no = getNoOfEntries(bucketPointer);
            // will the new entry fit into the current bucket or do we need to overflow?
            if (ovflBytes + (no + 1) * bytesPerEntry < bytesPerBucket) {
                // store current entries in this bucket
                writeNoOfEntries(bucketPointer, no + 1, false);
                // skip old entries and one byte for length info
                bucketPointer += no * bytesPerEntry + 1;
            } else {
                // store overflowed bit but old size
                writeNoOfEntries(bucketPointer, no, true);
                // Use overflow area! Ie. empty space from right to left of one bucket
                bucketPointer = findFreeOverflow(bucketPointer, 0);
            }
        }

        putKey(bucketPointer, key);
        storage.putInt(bucketPointer + bytesPerKeyRest, value);
        size++;
        // TODO create stats of: entries per bucket & overflow entries per bucket
    }

    void writeNoOfEntries(int bucketPointer, int no, boolean overflow) {
        if (no > maxEntriesPerBucket)
            throw new IllegalStateException("Entries shouldn't exceed maxEntriesPerBucket! Was "
                    + no + " vs. " + maxEntriesPerBucket);
        no <<= 1;
        if (overflow)
            storage.put(bucketPointer, (byte) (no | 0x1));
        else
            storage.put(bucketPointer, (byte) no);
    }

    boolean isOverflowed(int bucketPointer) {
        return (storage.get(bucketPointer) & 0x1) == 1;
    }

    byte getNoOfEntries(int bucketPointer) {
        byte no = storage.get(bucketPointer);
        // skip overflowed bit
        no >>>= 1;
        if (no > maxEntriesPerBucket)
            throw new IllegalStateException("Entries shouldn't exceed maxEntriesPerBucket! Was "
                    + no + " vs. " + maxEntriesPerBucket);
        return no;
    }

    /**
     * find last overflow entry with identical key and stopbit (1)
     */
    private int findExistingOverflow(int bucketPointer, long key) {
        BucketOverflowLoop loop1 = new KeyCheckLoop(key);
        bucketPointer = loop1.throughBuckets(bucketPointer);
        // write offset and remove stopbit
        storage.put(loop1.overflowPointer, (byte) ((loop1.lastOffset >>> 1) << 1));
        return findFreeOverflow(bucketPointer, loop1.newOffset - 1);
    }

    /**
     * find next free overflow entry
     */
    private int findFreeOverflow(int bucketPointer, int oldOffset) {
        BucketOverflowLoop loop2 = new BucketOverflowLoop();
        loop2.newOffset = oldOffset;
        loop2.throughBuckets(bucketPointer);
        // write offset and set stopbit
        storage.put(loop2.overflowPointer, (byte) ((loop2.newOffset << 1) | 0x1));
        // skip the overflow-offset byte
        return loop2.overflowPointer + 1;
    }

    /**
     * @param overflowPointer points not to the beginning of the bucket but to the first possible
     * overflow entry (which are filled from right to left)
     * @return count the number of used overflow bytes
     */
    int countOverflowBytes(int overflowPointer) {
        byte offsetAndStopBit = storage.get(overflowPointer);
        // check if at least one overflow entry exists
        if (offsetAndStopBit == 0)
            return 0;

        int count = 1;
        while ((offsetAndStopBit & 1) == 0) {
            offsetAndStopBit = storage.get(overflowPointer);
            count++;
            overflowPointer -= bytesPerOverflowEntry;
            if (overflowPointer < 0)
                throw new IllegalStateException(count + " " + offsetAndStopBit + " " + overflowPointer);
        }
        return count;
    }

    final long getKey(int index) {
        long key = 0;
        int max = index + bytesPerKeyRest;
        while (true) {
            // uh, byte to long makes all longish bits to 1!?
            key |= storage.get(index) & 0xff;
            index++;
            if (index >= max)
                break;
            key <<= BITS8;
        }
        return key;
    }

    final void putKey(int index, long val) {
        int start = index + bytesPerKeyRest - 1;
        while (true) {
            storage.put(start, (byte) val);
            val >>>= BITS8;
            if (val == 0)
                break;

            start--;
        }
    }

    @Override
    public void add(double lat, double lon, Integer value) {
        if (value == null)
            throw new UnsupportedOperationException("You cannot add null value. Auto convert this to  e.g. 0?");
        add(algo.encode(lat, lon), value);
    }

    @Override
    public int remove(double lat, double lon) {
        algo.encode(lat, lon);
        // TODO
        return 0;
    }

    List<CoordTrig<Integer>> getNodes(long key) {
        final List<CoordTrig<Integer>> res = new ArrayList<CoordTrig<Integer>>();
        getNodes(res, key);
        return res;
    }

    /**
     * returns nodes of specified key
     */
    void getNodes(final List<CoordTrig<Integer>> res, final long key) {
        int bucketIndex = getBucketIndex(key);
        // convert to pointer:
        int bucketPointer = bucketIndex * bytesPerBucket;
        byte no = getNoOfEntries(bucketPointer);
        int max = bucketPointer + no * bytesPerEntry + 1;
        for (int index = bucketPointer + 1; index < max; index += bytesPerEntry) {
            long storedKey = getKey(index);
            if (storedKey == key) {
                CoordTrig<Integer> coord = new CoordTrigIntEntry();
                algo.decode(storedKey, coord);
                coord.setValue(storage.getInt(index + bytesPerKeyRest));
                res.add(coord);
            }
        }

        if (isOverflowed(bucketPointer)) {
            // iterate through overflow entries (with identical key) of the next buckets until stopbit found
            new BucketOverflowLoop() {

                @Override
                boolean doWork() {
                    long storedKey = getKey(overflowPointer + 1);
                    if (storedKey == key) {
                        CoordTrig<Integer> coord = new CoordTrigIntEntry();
                        algo.decode(storedKey, coord);
                        coord.setValue(storage.getInt(overflowPointer + 1 + bytesPerKeyRest));
                        res.add(coord);
                        // stopbit
                        if ((lastOffset & 0x1) == 1)
                            return true;
                    }
                    return false;
                }
            }.throughBuckets(bucketPointer);
        }
    }

    private void getNeighbours(BBox nodeBB, Shape searchRect, long bucketIndexBit, LeafWorker worker) {
        if (bucketIndexBit < spatialKeyBits) {
            // TODO where to get current key
            worker.doWork(123, 321);
            return;
        }

        double lat12 = (nodeBB.maxLat + nodeBB.minLat) / 2;
        double lon12 = (nodeBB.minLon + nodeBB.maxLon) / 2;

        // top-left - see SpatialKeyAlgo that latitude goes from bottom to top and is 1 if on top
        // 10 11
        // 00 01
        // TODO node10?
        long node10 = bucketIndexBit >> 1;
        BBox nodeRect10 = new BBox(nodeBB.minLon, lon12, lat12, nodeBB.maxLat);
        if (searchRect.intersect(nodeRect10))
            getNeighbours(nodeRect10, searchRect, node10, worker);

        // top-right
        // TODO 
        long node11 = bucketIndexBit >> 1;
        BBox nodeRect11 = new BBox(lon12, nodeBB.maxLon, lat12, nodeBB.maxLat);
        if (searchRect.intersect(nodeRect11))
            getNeighbours(nodeRect11, searchRect, node11, worker);

        // bottom-left
        // TODO 
        long node00 = bucketIndexBit >> 1;
        BBox nodeRect00 = new BBox(nodeBB.minLon, lon12, nodeBB.minLat, lat12);
        if (searchRect.intersect(nodeRect00))
            getNeighbours(nodeRect00, searchRect, node00, worker);

        // bottom-right
        // TODO 
        long node01 = bucketIndexBit >> 1;
        BBox nodeRect01 = new BBox(lon12, nodeBB.maxLon, nodeBB.minLat, lat12);
        if (searchRect.intersect(nodeRect01))
            getNeighbours(nodeRect01, searchRect, node01, worker);
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodes(final double lat, final double lon,
            final double distanceInKm) {
        final List<CoordTrig<Integer>> result = new ArrayList<CoordTrig<Integer>>();
        final Circle c = new Circle(lat, lon, distanceInKm);
        LeafWorker distanceAcceptor = new LeafWorker() {

            @Override public void doWork(long key, int value) {
                CoordTrigIntEntry coord = new CoordTrigIntEntry();
                algo.decode(key, coord);
                if (c.contains(coord.lat, coord.lon))
                    result.add(coord);
                coord.setValue(value);
            }
        };

        // TODO maxBIT
        getNeighbours(BBox.createEarthMax(), c, 123, distanceAcceptor);
        return result;
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodes(Shape boundingBox) {
        // TODO
        return Collections.EMPTY_LIST;
    }

    interface LeafWorker {

        void doWork(long key, int value);
    }

    @Override
    public Collection<CoordTrig<Integer>> getNodesFromValue(final double lat, final double lon,
            final Integer value) {
        // TODO no spatialKey necessary?
        // final long spatialKey = algo.encode(lat, lon);
        final List<CoordTrig<Integer>> nodes = new ArrayList<CoordTrig<Integer>>(1);
        LeafWorker worker = new LeafWorker() {

            @Override public void doWork(long key, int value) {
                // TODO unused value?
                getNodes(nodes, key);
            }
        };
        // TODO maxBIT
        long maxBit = 1 << spatialKeyBits;
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err),
                maxBit, worker);
        return nodes;
    }

    @Override
    public void clear() {
        size = 0;
        initBuffers();
    }

    public XYVectorInterface getHist(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            int entries = getNoOfEntries(bucketIndex);
            int ovfl = countOverflowBytes(bucketIndex);
            int unusedBytes = bytesPerBucket - 1 - (entries * bytesPerEntry + ovfl * bytesPerOverflowEntry);
            xy.add(bucketIndex, unusedBytes);
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getOverflowOffset(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            BucketOverflowLoop loop = new BucketOverflowLoop();
            loop.throughBuckets(bucketIndex);
            xy.add(bucketIndex, loop.newOffset);
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getOverflowEntries(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            xy.add(bucketIndex, countOverflowBytes(bucketIndex));
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getEntries(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            xy.add(bucketIndex, getNoOfEntries(bucketIndex));
        }
        xy.setTitle(title);
        return xy;
    }

    public TIntIntHashMap getStats(int max) {
        TIntIntHashMap stats = new TIntIntHashMap(max);
        for (int i = 0; i < max; i++) {
            stats.put(i, 0);
        }
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            int entries = getNoOfEntries(bucketIndex);
            int ovfl = countOverflowBytes(bucketIndex);
            int unusedBytes = bytesPerBucket - 1 - (entries * bytesPerEntry + ovfl * bytesPerOverflowEntry);
            stats.increment(unusedBytes);
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
        if (onlyBranches)
            return 0;

        int countEmptyBytes = 0;
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            int entries = getNoOfEntries(bucketIndex);
            int ovfl = countOverflowBytes(bucketIndex);
            countEmptyBytes += bytesPerBucket - 1 - (entries * bytesPerEntry + ovfl * bytesPerOverflowEntry);
        }
        return countEmptyBytes;
    }

    class BucketOverflowLoop {

        int lastOffset;
        int newOffset;
        int overflowPointer;

        /**
         * steps through bucket by bucket
         */
        int throughBuckets(int bucketPointer) {
            MAIN:
            while (true) {
                newOffset++;
                bucketPointer += bytesPerBucket;
                if (bucketPointer >= getMemoryUsageInBytes(0))
                    throw new IllegalStateException("bp:" + bucketPointer + " offset:" + newOffset);

                byte no = getNoOfEntries(bucketPointer);
                int maxBytes = bucketPointer + no * bytesPerEntry + 1;
                overflowPointer = bucketPointer + bytesPerBucket - bytesPerOverflowEntry;
                if (throughOverflowEntries(maxBytes))
                    break;

                if (newOffset > 200)
                    throw new IllegalStateException("bp:" + bucketPointer + " offset:" + newOffset);
            }
            return bucketPointer;
        }

        /**
         * loops through overflow entries of one bucket
         *
         * @return 1 if overflow entry of identical key with stopbit found, and -1 if not found.
         * returns 0 if empty overflow space found.
         */
        boolean throughOverflowEntries(int maxBytes) {
            while (overflowPointer > maxBytes) {
                lastOffset = storage.get(overflowPointer);
                if (lastOffset == 0)
                    return true;

                if (doWork())
                    return true;
                overflowPointer -= bytesPerOverflowEntry;
            }
            return false;
        }

        boolean doWork() {
            return false;
        }
    }

    class KeyCheckLoop extends BucketOverflowLoop {

        long key;

        public KeyCheckLoop(long key) {
            this.key = key;
        }

        @Override
        boolean doWork() {
            // stopbit
            if ((lastOffset & 0x1) == 1) {
                long tmpKey = getKey(overflowPointer + 1);
                if (tmpKey == key)
                    return true;
            }
            return false;
        }
    }
};
