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

import de.jetsli.graph.geohash.SpatialHashtable.BucketOverflowLoop;
import de.jetsli.graph.util.CalcDistance;
import de.jetsli.graph.trees.*;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.CoordTrigLongEntry;
import de.jetsli.graph.util.shapes.BBox;
import de.jetsli.graph.util.shapes.Circle;
import de.jetsli.graph.util.shapes.Shape;
import gnu.trove.map.hash.TIntIntHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class maps latitude and longitude through there spatial key to values like osm ids, geo IPs,
 * references or similar.
 *
 * See http://karussell.wordpress.com/category/algorithm/
 *
 * ##### FEATURES #####
 *
 * # memory efficient spatial storage, also for smaller collections of data
 *
 * # relative simple implementation ("safe bytes not bits"), use maintainable amount of methods even
 * if its slower
 *
 * # implements QuadTree interface - with neighborhood search
 *
 * # duplicate key allowed
 *
 * # no separate overflow area necessary -> use the same buckets and use one byte in an overflow
 * entries to indentify the origin of it (count distance to original bucket). ring: at the end use
 * beginning as overflow area
 *
 * # moving the "bucket-index-window" to the front of the spatial key (ie. skipKeyEndBits is
 * maximal). Then it'll behave like a normal quadtree but will have too many collisions/overflows.
 *
 * # moving the window to the end of the key (ie. skipKeyEndBits is minimal) then this class will
 * behave like a good spatial key hashtable (collisions are minimal), but the neighbor searches are
 * most inefficient.
 *
 * ##### TODOs: #####
 *
 * # possibility to increase size => efficient copy + close + rehash see Netty's
 * DynamicChannelBuffer.ensureWritableBytes(int minWritableBytes)
 *
 * # implement removing via distance search => change QuadTree interface
 *
 * ##### LATER GOALS: #####
 *
 * # thread safety ByteBuffer is not thread safe, though we could a lock object per index or simply
 * using Read+WriteLocks
 *
 * # no integer limit due to the use of ByteBuffer.get(*int*) => use multiple bytebuffers -> see
 * FatBuffer.java or ByteBufferLongBigList.java from it.unimi.dsi dsiutils (grepcode)! would be a
 * lot slower due to i1=longIndex/len;i2=longIndex%len; in our put and get?
 *
 * # extract general purpose big-hashtable. ie. store less bytes for key (long/int) we would need
 * spatialKeyAlgo.encode(lat,lon,bytes,iterations), getBucketIndex(bytes), add(byte[] bytes, int
 * value)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialHashtable implements QuadTree<Long> {

    // bits & byte stuff
    private static final int BITS8 = 8;
    protected int size;
    protected int maxBuckets;
    protected CalcDistance calc = new CalcDistance();
    protected SpatialKeyAlgo algo;
    private boolean compressKey = true;
    
    private ByteBuffer storage;
    protected int bytesPerBucket;
    protected int bytesPerEntry;
    protected int bytesPerValue;
    protected int bytesPerOverflowEntry;
    private int maxEntriesPerBucket;
    // key compression
    private int unusedBits = BITS8;
    private int skipKeyBeginningBits, skipKeyEndBits;
    private int bytesPerKeyRest;
    private int spatialKeyBits;
    private int bucketIndexBits;
    private long rightMask;
    private int findOverflowFactor = 4;

    public SpatialHashtable() {
        this(8, 3);
    }

    public SpatialHashtable(int skipKeyBeginningBits) {
        this(skipKeyBeginningBits, 3);
    }

    public SpatialHashtable(int skipKeyBeginningBits, int initialEntriesPerBucket) {
        this.skipKeyBeginningBits = skipKeyBeginningBits;
        this.maxEntriesPerBucket = initialEntriesPerBucket;
    }

    public SpatialHashtable setCompressKey(boolean compressKey) {
        this.compressKey = compressKey;
        return this;
    }

    SpatialHashtable setFindOverflowFactor(int factor) {
        this.findOverflowFactor = factor;
        return this;
    }

    @Override
    public SpatialHashtable init(long maxEntries) {
        initKey();
        initBucketSizes((int) maxEntries);
        initBuffers();
        return this;
    }

    protected void initKey() {
        // TODO calculate necessary spatial key precision (=>unusedBits) from maxEntries
        //
        // one unused byte in spatial key (making encode/decode a bit faster) => but still higher precision than float        
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

    //####################
    // bucket byte layout: 1 byte + maxEntriesPerBucket * bytesPerEntry
    //   | size | entry1 | entry2 | ... | empty space | ... | oe2 | overflow entry1 |
    //
    // size byte layout
    //   entries per bucket (without overflowed entries!) (7 bits) | overflowed bit - marks if the current bucket is already overflowed
    //
    // overflow entry layout:
    //   offset to original bucket (7 bits) | stop bit | overflow entry    
    //
    // spatial key bit layout
    // | skipBeginning (incl. unusedBits) | bucketIndexBits | veryRightSide (bucketIndexBits) | skipEnd |        
    //####################
    //
    protected void initBucketSizes(int maxEntries) {
        maxBuckets = correctDivide(maxEntries, maxEntriesPerBucket);

        // Always use lower bits to guarantee that all indices are smaller than maxBuckets
        bucketIndexBits = (int) (Math.log(maxBuckets) / Math.log(2));

        // now adjust maxBuckets and maxEntriesPerBucket to avoid memory waste and fit a power of 2
        maxBuckets = (int) Math.pow(2, bucketIndexBits);
        maxEntriesPerBucket = correctDivide(maxEntries, maxBuckets);

        // introduce hash overflow area
        if (maxEntriesPerBucket < 5)
            maxEntriesPerBucket++;
        else if (maxEntriesPerBucket < 8)
            maxEntriesPerBucket += 2;
        else
            maxEntriesPerBucket *= 1.25;

        // TODO overflow area: When keys are uncompressed we could easily increase maxBucket size.
        // maxBuckets *= 1.1;
        // For the compressed case we would need to adjust bucketIndexBits to use the bigger buckets

        // if compressed then all data except 'y' which is indirectly encoded as bucket index needs to be stored 
        // => skip y of spatial key and store 'rest'
        if (compressKey) {
            bytesPerKeyRest = correctDivide(spatialKeyBits - bucketIndexBits, BITS8);
            skipKeyEndBits = 8 * BITS8 - skipKeyBeginningBits - bucketIndexBits * 2;
            if (skipKeyEndBits < 0)
                throw new IllegalStateException("Too many entries (" + maxEntries + ") for this"
                        + " skipBeginning value (" + skipKeyBeginningBits
                        + "). Or increase spatialKeyBits (" + spatialKeyBits + ")");
        } else {
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
        if (skipKeyEndBits > 0)
            rightMask = (1L << skipKeyEndBits) - 1;
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

    public int getSkipKeyBeginningBits() {
        return skipKeyBeginningBits;
    }

    public int getBucketIndexBits() {
        return bucketIndexBits;
    }

    public SpatialKeyAlgo getAlgo() {
        return algo;
    }

    public int getEntriesPerBucket() {
        return maxEntriesPerBucket;
    }

    public long getMaxBuckets() {
        return maxBuckets;
    }

    int getBucketIndex(long spatialKey) {
        if (!compressKey)
            return Math.abs((int) (spatialKey % (maxBuckets - 1)));

        // 2^28 * 3 ..  6 = ~800-1600 mio -> not possible to address this in a bytebuffer (int index)
        // bucket index = leftSide ^= veryRightSide

        // System.out.println(BitUtil.toBitString(spatialKey));
        long veryRightSide = spatialKey;
        veryRightSide <<= skipKeyBeginningBits + bucketIndexBits;
        // System.out.println(BitUtil.toBitString(veryRightSide));
        veryRightSide >>>= 8 * BITS8 - bucketIndexBits;
        // System.out.println(BitUtil.toBitString(veryRightSide));

        spatialKey <<= skipKeyBeginningBits;
        // System.out.println(BitUtil.toBitString(spatialKey));
        spatialKey >>>= skipKeyBeginningBits + skipKeyEndBits + bucketIndexBits;
        // System.out.println(BitUtil.toBitString(spatialKey));
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
     * And second part is better distributed and 'first' XOR 'second' is better distributed only if
     * second is longer) And because it is smaller => less space consumed. Also the first part is
     * more equal to other spatialKeys => in future implementations this could be better
     * compressable.
     */
    long getStoredKey(long spatialKey) {
        if (!compressKey)
            return spatialKey;

        // | skipKeyBeginningBits | x (bucketIndexBits) | y (bucketIndexBits) | skipped
        // => REMOVE y
        long skippedRight = spatialKey & rightMask;
//        System.out.println(BitUtil.toBitString(spatialKey));
        spatialKey >>>= bucketIndexBits + skipKeyEndBits;
        spatialKey <<= skipKeyEndBits;
//        System.out.println(BitUtil.toBitString(spatialKey));
//        System.out.println(BitUtil.toBitString(skippedRight));
        return spatialKey | skippedRight;
    }

    long toUncompressedKey(long storedKey, int bucketIndex) {
        if (!compressKey)
            return storedKey;

        // | skipKeyBeginningBits | x (bucketIndexBits) | y (bucketIndexBits) | skipped
        // => INSERT y
        int tmp = BITS8 * 8 - skipKeyEndBits;
        long right = storedKey;
        right <<= tmp;
        right >>>= tmp;
        long x = storedKey << BITS8 * 8 - bucketIndexBits - skipKeyEndBits;
        x >>>= BITS8 * 8 - bucketIndexBits;
        storedKey >>>= skipKeyEndBits;
        storedKey <<= skipKeyEndBits + bucketIndexBits;
//        System.out.println(BitUtil.toBitString(storedKey));
//        System.out.println(BitUtil.toBitString(right));
        storedKey |= right;
//        System.out.println(BitUtil.toBitString(bucketIndex));
        long y = (bucketIndex ^ x) << skipKeyEndBits;
//        System.out.println(BitUtil.toBitString(x));
//        System.out.println(BitUtil.toBitString(y));
        return storedKey | y;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public void add(long key, long value) {
        int bucketIndex = getBucketIndex(key);
        long storedKey = getStoredKey(key);

        // convert bucketIndex to byte pointer
        int pointer = bucketIndex * bytesPerBucket;
        if (isBucketFull(pointer)) {
            pointer = findExistingOverflow(pointer + bytesPerBucket, storedKey);
        } else {
            byte no = getNoOfEntries(pointer);
            int ovflBytes = getNoOfOverflowEntries(pointer, no);

            // will the new entry fit into the current bucket or do we need to overflow?
            if (ovflBytes * bytesPerOverflowEntry + (no + 1) * bytesPerEntry + 1 <= bytesPerBucket) {
                // store current entries in this bucket
                writeNoOfEntries(pointer, no + 1, false);
                // skip old entries and one byte for length info
                pointer += no * bytesPerEntry + 1;
            } else {
                // store overflowed bit but old size
                writeNoOfEntries(pointer, no, true);
                // Use overflow area! Ie. empty space from right to left of one bucket
                pointer = findFreeOverflow(pointer + bytesPerBucket, 0);
            }
        }

        putKey(pointer, storedKey);
        putValue(pointer + bytesPerKeyRest, value);
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

    protected byte getNoOfEntries(int bucketPointer) {
        byte no = get(bucketPointer);
        // skip overflowed bit
        no >>>= 1;
        if (no > maxEntriesPerBucket)
            throw new IllegalStateException("Entries shouldn't exceed maxEntriesPerBucket! Was "
                    + no + " vs. " + maxEntriesPerBucket + " at " + bucketPointer
                    + " problematic bp? " + bucketPointer % bytesPerBucket);
        return no;
    }

    /**
     * find last overflow entry with identical key and stopbit (1).
     *
     * @param bytesPointer use the pointer from the overflow area (next to the original bucket)
     */
    private int findExistingOverflow(int bytesPointer, long storedKey) {
        BucketOverflowLoop loop1 = new KeyCheckLoop(storedKey);
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
    protected int getNoOfOverflowEntries(int buckerPointer) {
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

    final long getKey(int pointer, int bucketIndex) {
        long storedKey = getHelper(pointer, pointer + bytesPerKeyRest);
        return toUncompressedKey(storedKey, bucketIndex);
    }

    final long getValue(int pointer) {
        return getHelper(pointer, pointer + bytesPerValue);
    }

    private long getHelper(int pointer, int max) {
        long key = 0;
        while (true) {
            // byte converted to long makes all longish bits to 1 so remove them via & 0xff!
            key |= get(pointer) & 0xff;
            pointer++;
            if (pointer >= max)
                break;
            key <<= BITS8;
        }
        return key;
    }

    final byte get(int pointer) {
        // unsufficient error message from ByteBuffer!
        if (pointer < 0)
            throw new IllegalStateException("negative pointer! " + pointer);
        return storage.get(pointer);
    }

    final void putValue(int pointer, long val) {
        putHelper(pointer + bytesPerValue - 1, val);
    }

    final void putKey(int pointer, long storedKey) {
        putHelper(pointer + bytesPerKeyRest - 1, storedKey);
    }

    private void putHelper(int pointer, long val) {
        while (true) {
            put(pointer, (byte) val);
            val >>>= BITS8;
            if (val == 0)
                break;

            pointer--;
        }
    }

    private void put(int pointer, byte b) {
        storage.put(pointer, b);
    }

    @Override
    public void add(double lat, double lon, Long value) {
        if (value == null)
            throw new UnsupportedOperationException("You cannot add null values. Auto convert this to e.g. 0?");
        add(algo.encode(lat, lon), value);
    }

    @Override
    public int remove(double lat, double lon) {
        // TODO
        // algo.encode(lat, lon);
        // size--;
        return 0;
    }

    @Override
    public Collection<CoordTrig<Long>> getNodes(final Shape searchArea) {
        final List<CoordTrig<Long>> result = new ArrayList<CoordTrig<Long>>();
        LeafWorker worker = new LeafWorker() {

            @Override public boolean doWork(long key, long value) {
                CoordTrigLongEntry coord = new CoordTrigLongEntry();
                algo.decode(key, coord);
                if (searchArea.contains(coord.lat, coord.lon)) {
                    result.add(coord);
                    coord.setValue(value);
                    return true;
                }
                return false;
            }
        };

        // brute force:
//        for (int bi = 0; bi < maxBuckets; bi++) {
//            getNodes(worker, bi);            
//        }        
        // quadtree:
        getNeighbours(BBox.createEarthMax(), searchArea, 0, 0L, worker, false);
        return result;
    }

    @Override
    public Collection<CoordTrig<Long>> getNodes(double lat, double lon, double distanceInKm) {
        return getNodes(new Circle(lat, lon, distanceInKm, calc));
    }

    @Override
    public Collection<CoordTrig<Long>> getNodesFromValue(final double lat, final double lon,
            final Long v) {
        final List<CoordTrig<Long>> nodes = new ArrayList<CoordTrig<Long>>(1);
        final long requestKey = algo.encode(lat, lon);
        LeafWorker worker = new LeafWorker() {

            @Override public boolean doWork(long key, long value) {
                if (v == null || v == value) {
                    CoordTrigLongEntry e = new CoordTrigLongEntry();
                    algo.decode(key, e);
                    if (requestKey == key) {
                        e.setValue(value);
                        nodes.add(e);
                    }
                    return true;
                }
                return false;
            }
        };
        double err = 1.0 / Math.pow(10, algo.getExactPrecision());
        getNeighbours(BBox.createEarthMax(), new BBox(lon - err, lon + err, lat - err, lat + err),
                0, 0L, worker, false);
        return nodes;
    }

    List<CoordTrig<Long>> getNodes(final long requestedKey) {
        int bucketIndex = getBucketIndex(requestedKey);
        final List<CoordTrig<Long>> res = new ArrayList<CoordTrig<Long>>();
        getNodes(new LeafWorker() {

            @Override public boolean doWork(long key, long value) {
                if (key == requestedKey) {
                    CoordTrig<Long> coord = new CoordTrigLongEntry();
                    algo.decode(key, coord);
                    coord.setValue(value);
                    res.add(coord);
                    return true;
                }
                return false;
            }
        }, bucketIndex, requestedKey);
        return res;
    }

    /**
     * allows the worker to process nodes of the specified bucketIndex
     */
    protected void getNodes(final LeafWorker worker, final int bucketIndex, final Long requestedKey) {
        int bucketPointer = bucketIndex * bytesPerBucket;
        byte no = getNoOfEntries(bucketPointer);
        int max = bucketPointer + no * bytesPerEntry + 1;
        for (int pointer = bucketPointer + 1; pointer < max; pointer += bytesPerEntry) {
            _add(worker, getKey(pointer, bucketIndex), pointer, requestedKey);
        }

        if (isBucketFull(bucketPointer)) {
            // iterate through overflow entries (with identical key) of the next buckets until stopbit found
            new BucketOverflowLoop() {

                @Override
                boolean doWork() {
                    if (_add(worker, getKey(overflowPointer + 1, bucketIndex), overflowPointer + 1, requestedKey)) {
                        // stopbit
                        if ((lastOffset & 0x1) == 1)
                            return true;
                    }
                    return false;
                }
            }.throughBuckets(bucketPointer + bytesPerBucket);
        }
    }

    boolean _add(LeafWorker worker, long key, int pointer, Long requestedKey) {
        if (pointer + bytesPerKeyRest + 4 > getMemoryUsageInBytes(0))
            throw new IllegalStateException("pointer " + pointer + " "
                    + getMemoryUsageInBytes(0) + " " + bytesPerKeyRest);

        // do expensive encoding contains check only if beginning bits of key are identical!
        if (requestedKey != null && (requestedKey >> skipKeyEndBits + bucketIndexBits) != (key >> skipKeyEndBits + bucketIndexBits)) {
//            System.out.println("rkey " + BitUtil.toBitString(requestedKey));
//            System.out.println(" key " + BitUtil.toBitString(key));
            return false;
        }

        return worker.doWork(key, getValue(pointer + bytesPerKeyRest));
    }

    private void getNeighbours(BBox nodeBB, Shape searchArea, int depth, long key, LeafWorker worker, boolean contained) {
        if (contained) {
            // TODO
            // check if searchRect is entirely consumed from nodeBB 
            // => we could simply iterate from smallest to highest bucketIndex
            // worker.setCheckContained(false);
            // do iteration
            // worker.setCheckContained(true);
            // return;
        }

        // instead of nodeBB we could use rectangle: top-left (xxx1010...), top-right (xxx1111...), bottom-left (xxx0000...), bottom-right (xxx0101...) created from key

        if (depth >= bucketIndexBits * 2 + skipKeyBeginningBits - unusedBits) {
            // key includes: | skippedBeginning | x | y | so we need skipEndBits:
            key <<= skipKeyEndBits;
            int bucketIndex = getBucketIndex(key);
            // worker.setCheckContained(contained);

            // avoid processing duplicate bucket indexes (due to "x XOR y")
            // if (!worker.markDone(bucketIndex)) -> not necessary as it is done via beginning of requestKey vs. key

            getNodes(worker, bucketIndex, key);

            // worker.setCheckContained(true);
            return;
        }

        double lat12 = (nodeBB.maxLat + nodeBB.minLat) / 2;
        double lon12 = (nodeBB.minLon + nodeBB.maxLon) / 2;
        depth += 2;
        key <<= 2;
        // see SpatialKeyAlgo that latitude goes from bottom to top and is 1 if on top
        // 10 11
        // 00 01    
        // top-left    
        BBox nodeRect10 = new BBox(nodeBB.minLon, lon12, lat12, nodeBB.maxLat);
        boolean res = searchArea.intersect(nodeRect10);
        if (res)
            getNeighbours(nodeRect10, searchArea, depth, key | 0x2L, worker, false);

        // top-right        
        BBox nodeRect11 = new BBox(lon12, nodeBB.maxLon, lat12, nodeBB.maxLat);
        res = searchArea.intersect(nodeRect11);
        if (res)
            getNeighbours(nodeRect11, searchArea, depth, key | 0x3L, worker, false);

        // bottom-left
        BBox nodeRect00 = new BBox(nodeBB.minLon, lon12, nodeBB.minLat, lat12);
        res = searchArea.intersect(nodeRect00);
        if (res)
            getNeighbours(nodeRect00, searchArea, depth, key, worker, false);

        // bottom-right
        BBox nodeRect01 = new BBox(lon12, nodeBB.maxLon, nodeBB.minLat, lat12);
        res = searchArea.intersect(nodeRect01);
        if (res)
            getNeighbours(nodeRect01, searchArea, depth, key | 0x1L, worker, false);
    }

    protected static abstract class LeafWorker {

        public LeafWorker() {
        }

        protected abstract boolean doWork(long key, long value);
    }

    @Override
    public void clear() {
        size = 0;
        initBuffers();
    }

    // for tests: get the offset of the overflow entry before we found an empty place
    public int getLastOffset(int bucketIndex) {
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
            int entries = getNoOfEntries(bucketIndex * bytesPerBucket);
            int ovfl = getNoOfOverflowEntries(bucketIndex * bytesPerBucket);
            countEmptyBytes += bytesPerBucket - 1 - (entries * bytesPerEntry + ovfl * bytesPerOverflowEntry);
        }
        return countEmptyBytes;
    }

    class BucketOverflowLoop {

        int lastOffset;
        int newOffset;
        int overflowPointer;
        int startBucketIndex;

        /**
         * steps through bucket by bucket and restarts at 0 if "too far"
         */
        int throughBuckets(int bucketPointer) {
            startBucketIndex = bucketPointer / bytesPerBucket;
            long tmp = bucketPointer;
            int i = -1;
            if (findOverflowFactor <= 0)
                findOverflowFactor = 2000;
            double factor = findOverflowFactor;
            MAIN:
            for (; i < maxBuckets; i++) {
                newOffset++;
                // byte area is connected like a ring
                while (tmp >= getMemoryUsageInBytes(0)) {
                    tmp -= getMemoryUsageInBytes(0);
                }

                if (throughOverflowEntries((int) tmp))
                    break;

                if (newOffset > 200000 || tmp < 0)
                    throw new IllegalStateException("no empty overflow place found - too full or bad hash distribution? "
                            + "TODO rehash if too small. Now at:" + tmp + " offset:" + newOffset + " size:" + size
                            + " requested bucketIndex " + startBucketIndex);
                tmp += factor * bytesPerBucket;
                // searches are 10% faster with this:
                // factor++;
            }
            if (i >= maxBuckets)
                throw new IllegalStateException("maxBuckets is too small => TODO rehash!");

            return (int) tmp;
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

        long storedKey;

        public KeyCheckLoop(long storedKey) {
            this.storedKey = storedKey;
        }

        @Override
        boolean doWork() {
            // stopbit
            if ((lastOffset & 0x1) == 1) {
                long tmpKey = getKey(overflowPointer + 1, startBucketIndex);
                if (tmpKey == storedKey)
                    return true;
            }
            return false;
        }
    }
};
