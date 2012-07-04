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

import de.jetsli.graph.util.BooleanRef;
import java.nio.ByteBuffer;

/**
 * This class maps latitude and longitude through there spatial key to integeger values like osm
 * ids, geo IPs or similar. It uses the last 3 bytes of the spatial key for the bucket index so that
 * we only need to store the rest. E.g. if we use 4 bytes for the spatial key we store only one
 * further byte instead of the whole 4 bytes which would be necessary for all other hash
 * implementations. (To handle hash collision we have to store the key as well)
 *
 * One bucket contains 6 entries of the form: higher order geo hash (1 - 4 bytes) and value (4
 * bytes) and a 1-3 byte index to the overflow area.
 *
 * One bucket is referenced via lower order bytes (1-4) of spatial key. Lower order bytes are more
 * uniformly distributed
 *
 * TODO: neighbour/distance queries are possible with this implementation! Filter away none matching
 * entries
 *
 * Warning 2: still highly alpha software and not thread safe! use thread local because
 * mmapped/bytebuffer is also not thread safe. And eliminate temporary variables
 *
 * Idea 1: The first bit of an entry marks if it is compressed. If it is 0 then simply read the raw
 * bytes. If not then the next 3 bits (customizable) represent how many bits the binary geohashes
 * have in common.
 *
 * Idea 2: All hash collision resolution schemes are nice. Especially this one
 * http://en.wikipedia.org/wiki/Hopscotch_hashing But: we cannot simply use the next bucket as the
 * index is different and so the stored part of the geohash will be wrong in combination with the
 * index
 *
 * TODO 1: remove
 *
 * TODO 2: auto increase and shrink -> change number of bits used for the index automagically. a
 * simple method: use one entry per bucket and multiple bytebuffers (think as if the second
 * dimension is now done via bytebuffer selection). => problem: rare entries will force to create an
 * entire new bytebuffer (~32MB, 3 + 4 bytes every entry) => fixed entryPerBucket + overflow area
 * would use less memory
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyHashtableOld {

    /*
     * bits per byte
     */
    private static final int BITS8 = 8;
    /*
     * If the value is equal to this then it is empty. TODO To change DEFAULT we would have to copy
     * this value into the value position for every entry of all buckets
     */
    private static final int EMPTY_VAL = 0;
    private ByteBuffer bucketBytes;
    private SpatialKeyAlgo encodeAlgo;
    private int entries;
    private int maxEntries;
    /*
     * some of the spatial key bytes are already encoded in the bucket index
     */
    private int bytesForBucketIndex;
    /*
     * complete length of spatial key
     */
    private int bitsForSpatialKey;
    private int bytesPerEntry;
    private int entriesPerBucket;
    private int bytesPerBucket;
    private int bytesForSpatialKeyRest;
    private int bytesForOverflowBucket;
    private long indexByteMask;
    private ByteBuffer overflowBytes;
    private int nextOverflow;
    private int bytesForOverflowLink;
    private int bytesForOverflowEntry;
    // private byte[] tmpBytes;

    public SpatialKeyHashtableOld() {
    }

    public SpatialKeyHashtableOld init() {
        // TODO bucket index bytes => 3
        //      geo hash rest      => 3      
        return init(2, 3);
    }

    SpatialKeyHashtableOld init(int bForBucketIndex, int bytesForSpatialKeyRest) {
        this.bytesForBucketIndex = bForBucketIndex;
        this.bytesForSpatialKeyRest = bytesForSpatialKeyRest;
        this.bitsForSpatialKey = (bytesForBucketIndex + bytesForSpatialKeyRest) * BITS8;
        encodeAlgo = new SpatialKeyAlgo(bitsForSpatialKey);
        int bytesPerValue = 4;
        bytesPerEntry = bytesForSpatialKeyRest + bytesPerValue;
        entriesPerBucket = 6;

        maxEntries = 1 << (bytesForSpatialKeyRest * BITS8 - 1);
        double factor = 1.1;
        if (maxEntries < 0)
            maxEntries = (int) ((double) Integer.MAX_VALUE / bytesPerEntry / factor);

        // store a link as last 3 bytes of every bucket for an overflow bucket
        bytesForOverflowLink = bytesForBucketIndex;
        bytesPerBucket = entriesPerBucket * bytesPerEntry + bytesForOverflowLink;
        int capacity = (int) (maxEntries * bytesPerEntry * factor);
        if (capacity < 0)
            capacity = Integer.MAX_VALUE;
        // TODO change maxEntries?
        //throw new IllegalStateException("To much memory allocated");        

        bucketBytes = ByteBuffer.allocateDirect(capacity);
        overflowBytes = ByteBuffer.allocateDirect(capacity / 10);

        // insert zeros to remove rest
        indexByteMask = 0xFFFFFFFFFFFFFFFFL >>> (64 - bytesForBucketIndex * BITS8);

        // make sure they are different
        nextOverflow = EMPTY_VAL + 1;
        bytesForOverflowEntry = bytesForSpatialKeyRest + bytesPerValue;
        bytesForOverflowBucket = bytesForOverflowEntry + bytesForOverflowLink;
        return this;
    }

    public int put(float lat, float lon, int value) {
        return put(encodeAlgo.encode(lat, lon), value);
    }

    public int put(long spatialKey, int value) {
        // remove 'left' part of the bytes to use spatial key as bucket index
        int bIndex = (int) (spatialKey & indexByteMask) * bytesPerBucket;
        int maxIndex = bIndex + bytesPerBucket - bytesForOverflowLink;

        // remove 'right' part of the bytes to store the binary geohash compressed 
        // because 'left' part of the index is still known via the bucket index we don't loose info!
        spatialKey >>>= bytesForBucketIndex;

        for (; bIndex < maxIndex; bIndex += bytesPerEntry) {
            long tmp = spatialKey;
            boolean isIdentical = true;
            boolean isFilled = false;
            for (int i = bytesPerEntry - 1; i >= 0; i--) {
                byte b = bucketBytes.get(bIndex + i);
                if (i < bytesForSpatialKeyRest) {
                    if (b != (byte) tmp)
                        isIdentical = false;

                    tmp >>>= BITS8;
                }
                if (b != EMPTY_VAL)
                    isFilled = true;
            }

            if (isIdentical || !isFilled)
                return writeEntry(isIdentical, bIndex, bucketBytes, spatialKey, value);
        }
        return writeToOverflow(bIndex, spatialKey, value);
    }

    public int get(float lat, float lon) {
        return get(encodeAlgo.encode(lat, lon));
    }

    public int get(long spatialKey) {
        int bIndex = (int) (spatialKey & indexByteMask) * bytesPerBucket;
        int maxIndex = bIndex + bytesPerBucket - bytesForOverflowLink;
        spatialKey >>>= bytesForBucketIndex;
        MAIN:
        for (; bIndex < maxIndex; bIndex += bytesPerEntry) {
            long tmp = spatialKey;
            boolean isFilled = false;
            for (int i = bytesPerEntry - 1; i >= 0; i--) {
                byte b = bucketBytes.get(bIndex + i);
                if (i < bytesForSpatialKeyRest) {
                    if (b != (byte) tmp)
                        continue MAIN;
                    tmp >>>= BITS8;
                }

                if (b != EMPTY_VAL)
                    isFilled = true;
            }

            // not found
            if (!isFilled)
                break;

            return bucketBytes.getInt(bIndex + bytesForSpatialKeyRest);
        }
        int overflowLink = getIndex(spatialKey, bIndex, null, null);
        if (overflowLink != 0)
            return overflowBytes.getInt(overflowLink + bytesForSpatialKeyRest);

        return EMPTY_VAL;
    }

    public void setHashingAlgo(SpatialKeyAlgo hashingAlgo) {
        this.encodeAlgo = hashingAlgo;
    }

    public int size() {
        return entries;
    }

    public SpatialKeyAlgo getHashingAlgo() {
        return encodeAlgo;
    }

    public int writeEntry(boolean isIdentical, int index, ByteBuffer bytes, long spatialKey, int value) {
        int oldVal = EMPTY_VAL;
        // capture old value when overwrite detected
        if (isIdentical)
            oldVal = bytes.getInt(index + bytesForSpatialKeyRest);
        else
            entries++;

        for (int i = bytesForSpatialKeyRest - 1; i >= 0; i--) {
            bytes.put(index + i, (byte) spatialKey);
            spatialKey >>>= BITS8;
        }
        bytes.putInt(index + bytesForSpatialKeyRest, value);
        return oldVal;
    }

    int getIndex(long spatialKey, int byteBucketIndex, BooleanRef isIdenticalRef, BooleanRef isOverflowRef) {
        int overflowLink = 0;
        for (int i = bytesForOverflowLink - 1; i >= 0; i--) {
            overflowLink <<= BITS8;
            overflowLink |= bucketBytes.get(byteBucketIndex + i) & 0xff;
        }

        boolean isIdentical = false;
        if (overflowLink != 0) {
            int tmpIndex;
            if (isOverflowRef != null)
                isOverflowRef.value = true;

            while (true) {
                isIdentical = true;
                int i = bytesForOverflowBucket - 1;
                tmpIndex = overflowLink + i;
                long tmpHash = spatialKey;
                int tmpOverflowLink = 0;
                for (; i >= 0; i--, tmpIndex--) {
                    byte b = overflowBytes.get(tmpIndex);
                    if (i < bytesForSpatialKeyRest) {
                        if (b != (byte) tmpHash)
                            isIdentical = false;
                        tmpHash >>>= BITS8;
                    }
                    if (i >= bytesForOverflowEntry) {
                        tmpOverflowLink <<= BITS8;
                        tmpOverflowLink |= b & 0xff;
                    }
                }

                if (isIdentical || tmpOverflowLink == 0)
                    break;

                overflowLink = tmpOverflowLink;
            }
        }
        if (isIdenticalRef != null)
            isIdenticalRef.value = isIdentical;
        return overflowLink;
    }

    int writeToOverflow(int byteBucketIndex, long spatialKey, int value) {
        BooleanRef isOverflowRef = new BooleanRef();
        BooleanRef isIdenticalRef = new BooleanRef();
        int ref = getIndex(spatialKey, byteBucketIndex, isIdenticalRef, isOverflowRef);
        if (!isIdenticalRef.value) {
            int index;
            ByteBuffer bb;
            if (isOverflowRef.value) {
                bb = overflowBytes;
                index = ref + bytesForOverflowBucket - 1;
            } else {
                index = byteBucketIndex;
                bb = bucketBytes;
            }

            int tmp = ref = getNextFreeOverflow();
            int min = index - bytesForOverflowLink;
            for (; index > min; index--) {
                bb.put(index, (byte) tmp);
                tmp >>= BITS8;
            }
        }

        return writeEntry(isIdenticalRef.value, ref, overflowBytes, spatialKey, value);
    }

    // the simple overflow stores some link bytes at the end of every bucket
    //
    // the advanced overflow (not implemented) works as follows:
    // if the last bit of a normal bucket is false a simple entry is stored,
    // otherwise the bytes contain an integer refering to an overflow bucket.
    // In case of an overflow we need to move the entry from the end of the normal bucket to 
    // the new overflow bucket and additionally we need to copy the new entry into the overflow bucket
    // => we need at least 2 entries in any case
    // 1. bytes for 1 entry
    // 2. bytes for next overflow
    int getNextFreeOverflow() {
        int tmp = nextOverflow;
        nextOverflow += bytesForOverflowBucket;
        return tmp;
    }
}
