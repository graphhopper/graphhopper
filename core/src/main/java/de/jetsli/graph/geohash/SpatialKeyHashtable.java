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
import de.jetsli.graph.geohash.SpatialKeyHashtable.BucketOverflowLoop;
import de.jetsli.graph.reader.OSMReaderTrials;
import de.jetsli.graph.reader.PerfTest;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.*;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.CoordTrigLongEntry;
import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.util.shapes.Circle;
import de.jetsli.graph.util.shapes.Shape;
import gnu.trove.map.hash.TIntIntHashMap;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * This class maps latitude and longitude through there spatial key to values like osm ids, geo IPs,
 * references or similar.
 *
 * It is similar to SpatialKeyHashtableOld but should be more robust and more applicable to real
 * world due to the QuadTree interface. Although SpatialKeyHashtableOld is exactly the same idea -
 * except that no neighbor search was implemented there.
 *
 * Another feature of this implementation is to move the "bucket-index-window" to the front of the
 * spatial key (ie. skipKeyEndBits is maximal). Then it'll behave like a normal quadtree but will
 * have too many collisions/overflows. If you move the window to the end of the key (ie.
 * skipKeyEndBits is minimal) then this class will behave like a good spatial key hashtable
 * (collisions are minimal), but the neighbor searches are most inefficient.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyHashtable implements QuadTree<Long> {

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
            for (int skipLeft = 8; skipLeft < 40; skipLeft += 2) {
                int entriesPerBuck = 3;
//                for (; entriesPerBuck < 20; entriesPerBuck += 8) {
                log("\n");
                SpatialKeyHashtable qt = new SpatialKeyHashtable(skipLeft, entriesPerBuck).init(locs);
                int epb = qt.getEntriesPerBucket();
                String title = "skipLeft:" + skipLeft + " entries/buck:" + epb;
                log(title);
                try {
                    PerfTest.fillQuadTree(qt, g);
                } catch (Exception ex) {
//                    ex.printStackTrace();
                    log(ex.getMessage());
                    continue;
                }

                XYVectorInterface entries = qt.getEntries("Entr " + title);
                panel.addData(entries);
                HistogrammInterface hist = (HistogrammInterface) entries.getY();
                log("entries: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());

                XYVectorInterface overflow = qt.getOverflowEntries("Ovrfl " + title);
                panel.addData(overflow);
                hist = (HistogrammInterface) overflow.getY();
                log("ovrflow: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());

//                XYVectorInterface overflowOff = qt.getOverflowOffset("Off " + title);
//                panel.addData(overflowOff);
//                hist = (HistogrammInterface) overflowOff.getY();
//                log("offsets: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float)hist.getMean() + " rms:" + (float)hist.getRMSError());

                XYVectorInterface dataHist = qt.getSum("Unuse " + title);
                hist = (HistogrammInterface) dataHist.getY();
                log("unused:  [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());
                panel.addData(dataHist);
                panel.repaint();
//                }
            }
        } catch (Exception ex) {
            // do not crash the UI if 'overflow'
            ex.printStackTrace();
        }
    }

    public static void log(Object o) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "# " + o);
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
    private int bytesPerValue;
    private int bytesPerOverflowEntry;
    private int maxEntriesPerBucket;
    // key compression
    private int skipKeyBeginningBits, skipKeyEndBits;
    private int bytesPerKeyRest;
    private int spatialKeyBits;
    private int bucketIndexBits;

    public SpatialKeyHashtable() {
        this(8, 3);
    }

    public SpatialKeyHashtable(int skipKeyBeginningBits) {
        this(skipKeyBeginningBits, 3);
    }

    public SpatialKeyHashtable(int skipKeyBeginningBits, int initialEntriesPerBucket) {
        this.skipKeyBeginningBits = skipKeyBeginningBits;
        this.maxEntriesPerBucket = initialEntriesPerBucket;
    }

    // REQUIREMENTS:
    // * memory efficient spatial storage, even for smaller collections of data
    // * relative simple implementation ("safe bytes not bits"), use lots of methods even if its slower
    // * moving bucket-index-window to configure between hashtable and quadtree 
    //   -> avoid configuration, auto-determine all stuff like necessary window, maxBuckets, ...
    // * implement neighbor search
    // * allow duplicate keys => only a "List get(key, distance)" method
    // * implement removing via distance search => TODO change QuadTree interface
    // * possibility to increase size => efficient copy + close + reinit
    //   see Netty's DynamicChannelBuffer.ensureWritableBytes(int minWritableBytes)
    // * there should be no problem to identify if an entry is empty or not => length for used entries, 
    //   offset byte for overflow entries which are > 0
    // * no explicit overflow area -> use the same buckets and use one byte in an overflow entries 
    //   to indentify the origin of it (count distance to original bucket). 
    //   ring: at the end use beginning as overflow area
    //
    // LATER GOALS:
    // * thread safe
    //   ByteBuffer is not thread safe, though we could a lock object per index or simply using Read+WriteLocks
    // * no integer limit due to the use of ByteBuffer.get(*int*) => use multiple bytebuffers 
    //  -> see FatBuffer.java or ByteBufferLongBigList.java from it.unimi.dsi dsiutils (grepcode)!
    //   would be a lot slower due to i1=longIndex/len;i2=longIndex%len; in our put and get?
    // * extract general purpose big-hashtable. ie. store less bytes for key (long/int)
    //   we would need spatialKeyAlgo.encode(lat,lon,bytes,iterations), getBucketIndex(bytes), add(byte[] bytes, int value)
    @Override
    public SpatialKeyHashtable init(long maxEntries) {
        initKey();
        initBucketSizes((int) maxEntries);
        initBuffers();
        return this;
    }

    public SpatialKeyHashtable setCompressKey(boolean compressKey) {
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
        maxEntriesPerBucket = (int) Math.round(correctDivide(maxEntries, maxBuckets));

        // Bytes which are not encoded as bucket index needs to be stored => 'rest' bytes
        if (compressKey) {
            // introduce hash overflow area
            maxEntriesPerBucket++;

            bytesPerKeyRest = correctDivide(spatialKeyBits - bucketIndexBits, BITS8);
            skipKeyEndBits = 8 * BITS8 - skipKeyBeginningBits - bucketIndexBits;
            if (skipKeyEndBits < 0)
                throw new IllegalStateException("Too many entries (" + maxEntries + ") for this"
                        + " skipBeginning value (" + skipKeyBeginningBits
                        + "). Or increase spatialKeyBits (" + spatialKeyBits + ")");
        } else {
            // introduce hash overflow area
            maxEntriesPerBucket++;
            // When keys are uncompressed we could easily increase maxBucket size instead.
            // For compressed case we would need to adjust bucketIndexBits to use the bigger buckets
            // maxBuckets *= 10;

            skipKeyEndBits = 0;
            // complete key
            bytesPerKeyRest = 8;
        }

        if (bytesPerValue <= 0 || bytesPerValue > 8)
            bytesPerValue = 4;
        bytesPerEntry = bytesPerKeyRest + bytesPerValue;
        bytesPerOverflowEntry = bytesPerEntry + 1;
        // store used entries per bucket in one byte (use one bit to mark bucket as full)
        // => maximum entries per bucket = 128
        int bytesForLength = 1;
        bytesPerBucket = maxEntriesPerBucket * bytesPerEntry + bytesForLength;
    }

    public void setBytesPerValue(int bytesPerValue) {
        this.bytesPerValue = bytesPerValue;
    }

    protected void initBuffers() {
        long capacity = maxBuckets * bytesPerBucket;
        if (capacity >= Integer.MAX_VALUE)
            throw new IllegalStateException("Too many elements. TODO: use multiple buffers to workaround 4GB limitation");

        storage = ByteBuffer.allocateDirect((int) capacity);
    }

    void setBucketIndexBits(int bucketIndexBits) {
        this.bucketIndexBits = bucketIndexBits;
    }

    int getBytesPerBucket() {
        return bytesPerBucket;
    }

    int getBytesPerEntry() {
        return bytesPerEntry;
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
        // which is not sufficient for a good distribution. We would need: x % (2^n-1)= .. this is ok now: x^y
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
    public void add(long key, long value) {
        int bucketPointer = getBucketIndex(key);
        if (compressKey)
            key = getPartOfKeyToStore(key);

        // convert bucketIndex to byte pointer
        bucketPointer *= bytesPerBucket;

        if (isBucketFull(bucketPointer)) {
            bucketPointer = findExistingOverflow(bucketPointer + bytesPerBucket, key);
        } else {
            byte no = getNoOfEntries(bucketPointer);
            int ovflBytes = getNoOfOverflowEntries(bucketPointer, no);

            // will the new entry fit into the current bucket or do we need to overflow?
            if (ovflBytes * bytesPerOverflowEntry + (no + 1) * bytesPerEntry + 1 <= bytesPerBucket) {
                // store current entries in this bucket
                writeNoOfEntries(bucketPointer, no + 1, false);
                // skip old entries and one byte for length info
                bucketPointer += no * bytesPerEntry + 1;
            } else {
                // store overflowed bit but old size
                writeNoOfEntries(bucketPointer, no, true);
                // Use overflow area! Ie. empty space from right to left of one bucket
                bucketPointer = findFreeOverflow(bucketPointer + bytesPerBucket, 0);
            }
        }

        putKey(bucketPointer, key);
        putValue(bucketPointer + bytesPerKeyRest, value);
        size++;
    }

    void writeNoOfEntries(int bucketPointer, int no, boolean fullBucket) {
        if (no > maxEntriesPerBucket)
            throw new IllegalStateException("Entries shouldn't exceed maxEntriesPerBucket! Was "
                    + no + " vs. " + maxEntriesPerBucket);
        no <<= 1;
        if (fullBucket)
            put(bucketPointer, (byte) (no | 0x1));
        else
            put(bucketPointer, (byte) no);
    }

    boolean isBucketFull(int bucketPointer) {
        return (get(bucketPointer) & 0x1) == 1;
    }

    byte getNoOfEntries(int bucketPointer) {
        byte no = get(bucketPointer);
        // skip overflowed bit
        no >>>= 1;
        if (no > maxEntriesPerBucket)
            throw new IllegalStateException("Entries shouldn't exceed maxEntriesPerBucket! Was "
                    + no + " vs. " + maxEntriesPerBucket + " at " + bucketPointer);
        return no;
    }

    /**
     * find last overflow entry with identical key and stopbit (1).
     *
     * @param bytesPointer use the pointer from the overflow area (next to the original bucket)
     */
    private int findExistingOverflow(int bytesPointer, long key) {
        BucketOverflowLoop loop1 = new KeyCheckLoop(key);
        bytesPointer = loop1.throughBuckets(bytesPointer);
        // write offset and remove stopbit
        put(loop1.overflowPointer, (byte) ((loop1.lastOffset >>> 1) << 1));
        return findFreeOverflow(bytesPointer, loop1.newOffset - 1);
    }

    /**
     * find next free overflow entry.
     *
     * @param bytesPointer use the pointer from the overflow area (next to the original bucket)
     */
    private int findFreeOverflow(int bytesPointer, int oldOffset) {
        BucketOverflowLoop loop2 = new BucketOverflowLoop();
        loop2.newOffset = oldOffset;
        loop2.throughBuckets(bytesPointer);

        // write offset and set stopbit
        put(loop2.overflowPointer, (byte) ((loop2.newOffset << 1) | 0x1));
        // skip the overflow-offset byte
        return loop2.overflowPointer + 1;
    }

    /**
     * @return count the number of used overflow bytes
     */
    int getNoOfOverflowEntries(int buckerPointer) {
        int no = getNoOfEntries(buckerPointer);
        return getNoOfOverflowEntries(buckerPointer, no);
    }

    private int getNoOfOverflowEntries(int bucketPointer, int entriesNo) {
        int overflowPointer = bucketPointer + bytesPerBucket - bytesPerOverflowEntry;
        int lastEntryByte = bucketPointer + 1 + entriesNo * bytesPerEntry - 1;

        // loop until last normal entry!
        int count = 0;
        while (lastEntryByte < overflowPointer) {
            byte offsetAndStopBit = get(overflowPointer);
            if (offsetAndStopBit == 0)
                break;

            count++;
            overflowPointer -= bytesPerOverflowEntry;
        }
        return count;
    }

    final long getKey(int pointer) {
        return getHelper(pointer, pointer + bytesPerKeyRest);
    }

    final long getValue(int pointer) {
        return getHelper(pointer, pointer + bytesPerValue);
    }

    private long getHelper(int pointer, int max) {
        long key = 0;
        while (true) {
            // uh, byte converted to long makes all longish bits to 1!?
            key |= get(pointer) & 0xff;
            pointer++;
            if (pointer >= max)
                break;
            key <<= BITS8;
        }
        return key;
    }

    final byte get(int index) {
        return storage.get(index);
    }

    final void putValue(int index, long val) {
        putHelper(index + bytesPerValue - 1, val);
    }

    final void putKey(int index, long key) {
        putHelper(index + bytesPerKeyRest - 1, key);
    }

    private void putHelper(int start, long val) {
        while (true) {
            put(start, (byte) val);
            val >>>= BITS8;
            if (val == 0)
                break;

            start--;
        }
    }

    private void put(int index, byte b) {
        storage.put(index, b);
    }

    @Override
    public void add(double lat, double lon, Long value) {
        if (value == null)
            throw new UnsupportedOperationException("You cannot add null value. Auto convert this to  e.g. 0?");
        add(algo.encode(lat, lon), value);
    }

    @Override
    public int remove(double lat, double lon) {
        algo.encode(lat, lon);
        // size--;
        return 0;
    }

    List<CoordTrig<Long>> getNodes(long key) {
        final List<CoordTrig<Long>> res = new ArrayList<CoordTrig<Long>>();
        int bucketIndex = getBucketIndex(key);
        getNodes(res, bucketIndex * bytesPerBucket, key);
        return res;
    }

    /**
     * returns nodes of specified key
     */
    void getNodes(final List<CoordTrig<Long>> res, final int bucketPointer, final long key) {
        // convert to pointer:        
        byte no = getNoOfEntries(bucketPointer);
        int max = bucketPointer + no * bytesPerEntry + 1;
        for (int pointer = bucketPointer + 1; pointer < max; pointer += bytesPerEntry) {
            _add(res, getKey(pointer), key, pointer);
        }

        if (isBucketFull(bucketPointer)) {
            // iterate through overflow entries (with identical key) of the next buckets until stopbit found
            new BucketOverflowLoop() {

                @Override
                boolean doWork() {
                    if (_add(res, getKey(overflowPointer + 1), key, overflowPointer + 1)) {
                        // stopbit
                        if ((lastOffset & 0x1) == 1)
                            return true;
                    }
                    return false;
                }
            }.throughBuckets(bucketPointer + bytesPerBucket);
        }
    }

    boolean _add(final List<CoordTrig<Long>> res, long storedKey, long key, int pointer) {
        if (storedKey == key || key == Long.MIN_VALUE) {
            CoordTrig<Long> coord = new CoordTrigLongEntry();
            algo.decode(storedKey, coord);

            if (pointer + bytesPerKeyRest + 4 > getMemoryUsageInBytes(0))
                throw new IllegalStateException("pointer " + pointer + " " + getMemoryUsageInBytes(0) + " " + bytesPerKeyRest);

            coord.setValue(getValue(pointer + bytesPerKeyRest));
            res.add(coord);
            return true;
        }
        return false;

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
        long node10 = bucketIndexBit >>> 1;
        BBox nodeRect10 = new BBox(nodeBB.minLon, lon12, lat12, nodeBB.maxLat);
        if (searchRect.intersect(nodeRect10))
            getNeighbours(nodeRect10, searchRect, node10, worker);

        // top-right
        // TODO 
        long node11 = bucketIndexBit >>> 1;
        BBox nodeRect11 = new BBox(lon12, nodeBB.maxLon, lat12, nodeBB.maxLat);
        if (searchRect.intersect(nodeRect11))
            getNeighbours(nodeRect11, searchRect, node11, worker);

        // bottom-left
        // TODO 
        long node00 = bucketIndexBit >>> 1;
        BBox nodeRect00 = new BBox(nodeBB.minLon, lon12, nodeBB.minLat, lat12);
        if (searchRect.intersect(nodeRect00))
            getNeighbours(nodeRect00, searchRect, node00, worker);

        // bottom-right
        // TODO 
        long node01 = bucketIndexBit >>> 1;
        BBox nodeRect01 = new BBox(lon12, nodeBB.maxLon, nodeBB.minLat, lat12);
        if (searchRect.intersect(nodeRect01))
            getNeighbours(nodeRect01, searchRect, node01, worker);
    }

    @Override
    public Collection<CoordTrig<Long>> getNodes(final double lat, final double lon,
            final double distanceInKm) {
        final List<CoordTrig<Long>> result = new ArrayList<CoordTrig<Long>>();
        final Circle c = new Circle(lat, lon, distanceInKm);
        LeafWorker distanceAcceptor = new LeafWorker() {

            @Override public void doWork(long key, long value) {
                CoordTrigLongEntry coord = new CoordTrigLongEntry();
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
    public Collection<CoordTrig<Long>> getNodes(Shape boundingBox) {
        // TODO
        return Collections.EMPTY_LIST;
    }

    interface LeafWorker {

        void doWork(long key, long value);
    }

    @Override
    public Collection<CoordTrig<Long>> getNodesFromValue(final double lat, final double lon,
            final Long value) {
        // TODO no spatialKey necessary?
        // final long spatialKey = algo.encode(lat, lon);
        final List<CoordTrig<Long>> nodes = new ArrayList<CoordTrig<Long>>(1);
        LeafWorker worker = new LeafWorker() {

            @Override public void doWork(long key, long value) {
                // TODO !
                getNodes(nodes, 0, key);
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

    public XYVectorInterface getUnusedBytes(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            int entryBytes = getNoOfEntries(bucketIndex * bytesPerBucket) * bytesPerEntry;
            int ovflBytes = getNoOfOverflowEntries(bucketIndex * bytesPerBucket) * bytesPerOverflowEntry;
            int unusedBytes = bytesPerBucket - 1 - (entryBytes + ovflBytes);
            xy.add(bucketIndex, unusedBytes);
        }
        xy.setTitle(title);
        return xy;
    }

    // for tests: get the offset of the overflow entry before we found an empty place
    int getLastOffset(int bucketIndex) {
        final AtomicInteger integ = new AtomicInteger(-1);
        BucketOverflowLoop loop = new BucketOverflowLoop() {

            @Override
            boolean doWork() {
                integ.set(lastOffset >>> 1);
                return super.doWork();
            }
        };
        int bucketPointer = bucketIndex * bytesPerBucket;
        loop.throughOverflowEntries(bucketPointer);
        return integ.get();
    }

    XYVectorInterface getOverflowOffset(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            xy.add(bucketIndex, getLastOffset(bucketIndex));
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getOverflowEntries(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            xy.add(bucketIndex, getNoOfOverflowEntries(bucketIndex * bytesPerBucket));
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getEntries(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            // getNoOfEntries(bucketIndex * bytesPerBucket)
            final List<CoordTrig<Long>> res = new ArrayList<CoordTrig<Long>>();
            getNodes(res, bucketIndex * bytesPerBucket, Long.MIN_VALUE);
            xy.add(bucketIndex, res.size());
        }
        xy.setTitle(title);
        return xy;
    }

    XYVectorInterface getSum(String title) {
        MainPool pool = MainPool.getDefault();
        XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
        for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
            int overflBytes = getNoOfOverflowEntries(bucketIndex * bytesPerBucket) * bytesPerOverflowEntry;
            int entryBytes = getNoOfEntries(bucketIndex * bytesPerBucket) * bytesPerEntry;
            int unusedBytes = bytesPerBucket - 1 - (entryBytes + overflBytes);
            xy.add(bucketIndex, (double) unusedBytes / bytesPerEntry);
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
            int entryBytes = getNoOfEntries(bucketIndex * bytesPerBucket) * bytesPerEntry;
            int ovflBytes = getNoOfOverflowEntries(bucketIndex * bytesPerBucket) * bytesPerOverflowEntry;
            int unusedBytes = bytesPerBucket - 1 - (entryBytes + ovflBytes);
            stats.increment(unusedBytes / bytesPerEntry);
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
            int ovfl = getNoOfOverflowEntries(bucketIndex);
            countEmptyBytes += bytesPerBucket - 1 - (entries * bytesPerEntry + ovfl * bytesPerOverflowEntry);
        }
        return countEmptyBytes;
    }

    class BucketOverflowLoop {

        int lastOffset;
        int newOffset;
        int overflowPointer;

        /**
         * steps through bucket by bucket and restarts at 0 if "too far"
         */
        int throughBuckets(int bucketPointer) {
            int i = -1;
            MAIN:
            for (; i < maxBuckets; i++) {
                newOffset++;
                // byte area is connected like a ring
                if (bucketPointer >= getMemoryUsageInBytes(0))
                    bucketPointer = 0;

                if (throughOverflowEntries(bucketPointer))
                    break;

                if (newOffset > 200)
                    throw new IllegalStateException("no empty overflow place found - too full or bad hash distribution? "
                            + "TODO rehash if too small. Now at:" + bucketPointer + " offset:" + newOffset + " size:" + size);
                bucketPointer += bytesPerBucket;
            }
            if (i >= maxBuckets)
                throw new IllegalStateException("maxBuckets is too small => TODO rehash!");

            return bucketPointer;
        }

        /**
         * loops through overflow entries of one bucket
         *
         * @return 1 if overflow entry of identical key with stopbit found, and -1 if not found.
         * returns 0 if empty overflow space found.
         */
        boolean throughOverflowEntries(int bucketPointer) {
            byte no = getNoOfEntries(bucketPointer);
            overflowPointer = bucketPointer + bytesPerBucket - bytesPerOverflowEntry;
            int minBytes = bucketPointer + 1 + no * bytesPerEntry - 1;
            while (overflowPointer > minBytes) {
                lastOffset = get(overflowPointer);
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
