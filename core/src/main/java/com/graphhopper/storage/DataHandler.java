package com.graphhopper.storage;

import java.util.Arrays;

// Partially taken from Lucene core. Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.
final class DataHandler {
    // TODO do we need two separate offsets? Or should we better separate read+write into separate classes like Lucene does?
    int readOffset;
    int writeOffset;
    byte[] writeBytes;
    long readPointer;
    DataAccess dataAccess;

    public DataHandler(DataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public void resetWriteOffset() {
        this.writeOffset = 0;
        if (this.writeBytes == null)
            this.writeBytes = new byte[64];
    }

    public void resetReadOffset() {
        this.readOffset = 0;
    }

    public void setReadPointer(long readPointer) {
        this.readPointer = readPointer;
    }

    public long getReadPointer() {
        return readPointer;
    }

    void writeToDataAccess(long writePointer) {
        dataAccess.ensureCapacity(writePointer + writeOffset);
        dataAccess.setBytes(writePointer, writeBytes, writeOffset);
    }

    /**
     * This method is automatically called to check if bytes array size needs to be increased
     */
    void ensureCapacity() {
        if (writeOffset >= writeBytes.length)
            writeBytes = Arrays.copyOf(writeBytes, Math.round(writeBytes.length * 1.5f));
    }

    public final void writeZInt(int i) {
        writeVInt(zigZagEncode(i));
    }

    final void writeVInt(int i) {
        while ((i & ~0x7F) != 0) {
            ensureCapacity();
            writeBytes[writeOffset++] = (byte) ((i & 0x7F) | 0x80);
            i >>>= 7;
        }

        ensureCapacity();
        writeBytes[writeOffset++] = (byte) i;
    }

    /**
     * <a href="https://developers.google.com/protocol-buffers/docs/encoding#types">Zig-zag</a>
     * encode the provided long. Assuming the input is a signed long whose
     * absolute value can be stored on <code>n</code> bits, the returned value will
     * be an unsigned long that can be stored on <code>n+1</code> bits.
     */
    static int zigZagEncode(int i) {
        // store sign as first bit instead of last.
        return (i >> 31) ^ (i << 1);
    }

    static int zigZagDecode(int i) {
        return ((i >>> 1) ^ -(i & 1));
    }

    public int readZInt() {
        return zigZagDecode(readVInt());
    }

    int readVInt() {
        // unrolled loop because of a JVM bug, see LUCENE-2975
        byte b = dataAccess.getByte(readPointer + readOffset++);
        if (b >= 0) return b;
        int i = b & 0x7F;
        b = dataAccess.getByte(readPointer + readOffset++);
        i |= (b & 0x7F) << 7;
        if (b >= 0) return i;
        b = dataAccess.getByte(readPointer + readOffset++);
        i |= (b & 0x7F) << 14;
        if (b >= 0) return i;
        b = dataAccess.getByte(readPointer + readOffset++);
        i |= (b & 0x7F) << 21;
        if (b >= 0) return i;
        b = dataAccess.getByte(readPointer + readOffset++);
        // Warning: the next ands use 0x0F / 0xF0 - beware copy/paste errors:
        i |= (b & 0x0F) << 28;
        if ((b & 0xF0) == 0) return i;
        throw new IllegalStateException("Invalid vInt detected (too many bits)");
    }
}
