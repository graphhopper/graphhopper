/*
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
package com.graphhopper.storage;

import java.util.Arrays;

/**
 * Taken from Lucene DataOutput. VLong's are longs which have variable length depending on the size.
 * When used together with 'delta compression' it is likely that you'll use only 1 byte per value.
 */
public class VLongStorage {
    private byte[] bytes;
    private int pointer = 0;

    public VLongStorage() {
        this(10);
    }

    public VLongStorage(int cap) {
        this(new byte[cap]);
    }

    public VLongStorage(byte[] bytes) {
        this.bytes = bytes;
    }

    public void seek(long pos) {
        // TODO long vs. int
        pointer = (int) pos;
    }

    public long getPosition() {
        return pointer;
    }

    public long getLength() {
        return bytes.length;
    }

    byte readByte() {
        byte b = bytes[pointer];
        pointer++;
        return b;
    }

    void writeByte(byte b) {
        if (pointer >= bytes.length) {
            int cap = Math.max(10, (int) (pointer * 1.5f));
            bytes = Arrays.copyOf(bytes, cap);
        }
        bytes[pointer] = b;
        pointer++;
    }

    /**
     * Writes an long in a variable-length format. Writes between one and nine bytes. Smaller values
     * take fewer bytes. Negative numbers are not supported.
     * <p>
     * The format is described further in Lucene its DataOutput#writeVInt(int)
     * <p>
     * See DataInput readVLong of Lucene
     */
    public final void writeVLong(long i) {
        assert i >= 0L;
        while ((i & ~0x7FL) != 0L) {
            writeByte((byte) ((i & 0x7FL) | 0x80L));
            i >>>= 7;
        }
        writeByte((byte) i);
    }

    /**
     * Reads a long stored in variable-length format. Reads between one and nine bytes. Smaller
     * values take fewer bytes. Negative numbers are not supported.
     * <p>
     * The format is described further in DataOutput writeVInt(int) from Lucene.
     */
    public long readVLong() {
        /* This is the original code of this method,
         * but a Hotspot bug (see LUCENE-2975) corrupts the for-loop if
         * readByte() is inlined. So the loop was unwinded!
         byte b = readByte();
         long i = b & 0x7F;
         for (int shift = 7; (b & 0x80) != 0; shift += 7) {
         b = readByte();
         i |= (b & 0x7FL) << shift;
         }
         return i;
         */
        byte b = readByte();
        if (b >= 0) {
            return b;
        }
        long i = b & 0x7FL;
        b = readByte();
        i |= (b & 0x7FL) << 7;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 14;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 21;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 28;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 35;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 42;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 49;
        if (b >= 0) {
            return i;
        }
        b = readByte();
        i |= (b & 0x7FL) << 56;
        if (b >= 0) {
            return i;
        }
        throw new RuntimeException("Invalid vLong detected (negative values disallowed)");
    }

    public void trimToSize() {
        if (bytes.length > pointer) {
            byte[] tmp = new byte[pointer];
            System.arraycopy(bytes, 0, tmp, 0, pointer);
            bytes = tmp;
        }
    }

    public byte[] getBytes() {
        return bytes;
    }
}
