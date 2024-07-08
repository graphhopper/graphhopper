package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.BitSet;

/**
 * This class is useful to efficiently store a read-only mapping from a sparse set of integers to a smaller, compact
 * integer range. The keys can be taken from a large integer range, while the mapped values must start from zero and
 * must be consecutive. For two keys k1 < k2 it must hold that v1 < v2.
 * <p>
 * Let's say we have 100 slots that are all empty except 5 which are mapped to the numbers 0-4:
 * 12->0, 17->1, 26->2, 69->3, 83->4
 * So we are mapping a large, sparse range [0, 99] to a small and compact range [0,4]. How can we represent this in
 * memory?
 * The easiest thing we could do would be using an int[] array with 100 elements that are all set to some 'empty' value
 * like -1 except the non-empty ones that are set to their corresponding value. However, this is not memory efficient
 * when only a few slots are occupied, because most (here 95%) of the memory will be wasted just to store empty elements.
 * In theory we can reduce the memory usage from 100*4byte=3200bit to 100bit without loss of information by using
 * a (hypothetical) 'bit array' with 100 elements. Instead of storing the actual values we simply set the occupied
 * elements to 1 while the empty ones are 0. This way we can still restore the actual value for a given key by counting
 * the number of previous elements. This works because the mapped values are just 0,1,2,...
 * For example to obtain the value for key 26 we would count the number of set bits up to (and excluding) index 26,
 * which indeed gives the correct value two. Doing this counting for every lookup would be too slow, but we can store
 * partial counts to speed this up.
 * This class does exactly this with the only further requirement that the 'bit array' is an array of longs, where each
 * long represents 64 bits. Using longs allows fast bit counting. We divide the long array into 'chunks' of a
 * configurable size and for each chunk we store the bit count of all previous chunks.
 */
public class SparseIntIntMap {
    private static final int BITS_PER_LONG = 64;
    private final LongProvider readLong;
    private final int longsPerChunk;
    private final IntProvider readPartialSum;

    /**
     * Creates a (read-only) access to the int-int mapping. This code is independent from the way the underlying data
     * is stored. For quick-and easy in-memory usage there is {@link #fromBitSet(BitSet, int)}.
     *
     * @param readLong       the key range must be represented by a list of longs where each set bit means the key is used
     *                       and each unset bit means the key is not used. this callback returns the long at a given index
     * @param longsPerChunk  the number of longs per chunk. This value must match the value that was used when we called
     *                       {@link #writeIndex}.
     * @param readPartialSum to efficiently read the values we need the total occupied key count up to a given key.
     *                       this data is written in {@link #writeIndex} and this callback returns the partial sum
     *                       for a given index.
     **/
    public SparseIntIntMap(LongProvider readLong, int longsPerChunk, IntProvider readPartialSum) {
        this.readLong = readLong;
        this.longsPerChunk = longsPerChunk;
        this.readPartialSum = readPartialSum;
    }

    /**
     * Returns the mapped integer value for the given integer key or -1 in case the key is not used.
     */
    public int get(int key) {
        long keyBit = readLong.get(key / BITS_PER_LONG) & (1L << key % BITS_PER_LONG);
        // if the given id's bit is not set there is no mapping
        if (keyBit == 0)
            return -1;
        int chunk = key / (longsPerChunk * BITS_PER_LONG);
        // here we save time because we do not have to count all the previous bits, but can quickly access the summed
        // count for the highest fully occupied chunk
        int result = readPartialSum.get(chunk);
        // for the last chunk we need to do the summation explicitly
        for (int i = chunk * longsPerChunk; i < key / BITS_PER_LONG; ++i)
            result += Long.bitCount(readLong.get(i));
        result += Long.bitCount(readLong.get(key / BITS_PER_LONG) & ((1L << key % BITS_PER_LONG) - 1));
        return result;
    }

    /**
     * Sets the key bit for the given long and given key. The way we store keys here must be aligned with the way
     * we read them later in {@link #get(int)}
     */
    public static long setKeyBit(long l, int key) {
        l |= 1L << (key % 64);
        return l;
    }

    /**
     * @param readLong        callback used to read longs from the underlying storage
     * @param numLongs        number of longs in the underlying storage
     * @param longsPerChunk   the number of longs per chunk. A smaller value means more memory is needed and less summation
     *                        needs to be performed so queries might be faster. A larger value means less memory is needed,
     *                        but queries might be slower.
     * @param writePartialSum callback used to store a partial sum at a given index. It does not matter where the data
     *                        is written, but to create a {@link SparseIntIntMap} instance we will need another callback that
     *                        finds the value we write here again.
     */
    public static void writeIndex(LongProvider readLong, int numLongs, int longsPerChunk, IntSetter writePartialSum) {
        int completeChunks = numLongs / longsPerChunk;
        int sum = 0;
        for (int i = 0; i < completeChunks; i++) {
            writePartialSum.set(i, sum);
            for (int j = 0; j < longsPerChunk; j++)
                sum += Long.bitCount(readLong.get(i * longsPerChunk + j));
        }
        if (numLongs % longsPerChunk > 0)
            writePartialSum.set(completeChunks, sum);
    }

    /**
     * Creates an in-memory mapping from a `BitSet`. The smallest set bit will be interpreted as value 0, the second
     * smallest as value 1 and so on.
     *
     * @param longsPerChunk see {@link SparseIntIntMap}, use 8 in case you don't know what this parameter does.
     */
    public static SparseIntIntMap fromBitSet(BitSet bits, int longsPerChunk) {
        return fromLongArray(bits.bits, bits.bits.length, longsPerChunk);
    }

    /**
     * Creates an in-memory mapping from an array of longs.
     */
    public static SparseIntIntMap fromLongArray(long[] longs, int endExcl, int longsPerChunk) {
        int[] index = new int[endExcl / longsPerChunk + 1];
        writeIndex(i -> longs[i], endExcl, longsPerChunk, (i, sum) -> index[i] = sum);
        return new SparseIntIntMap(i -> longs[i], longsPerChunk, i -> index[i]);
    }

    public interface LongProvider {
        long get(int index);
    }

    public interface LongSetter {
        void set(int index, long value);
    }

    public interface IntProvider {
        int get(int index);
    }

    public interface IntSetter {
        void set(int index, int value);
    }
}
