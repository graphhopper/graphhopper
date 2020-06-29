package com.graphhopper.storage;

// Source: Lucene core. Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.
final class DataHandler {
    // TODO do we need two separate offsets? Or should we better separate read+write into separate classes like Lucene does?
    int readOffset = 0;
    int writeOffset = 0;
    byte[] bytes;
    long dataAccessPointer;
    DataAccess dataAccess;

    public DataHandler(byte[] bytes, DataAccess dataAccess, long dataAccessPointer) {
        this.dataAccess = dataAccess;
        this.dataAccessPointer = dataAccessPointer;
        this.bytes = bytes;
        if (bytes.length < 9)
            throw new IllegalArgumentException("Too small bytes array " + bytes.length);
    }

    /**
     * To use the readXY method you have to call this method first
     */
    void readFromDataAccess() {
        dataAccess.getBytes(dataAccessPointer, bytes, bytes.length);
    }

    /**
     * This method is automatically called whenever bytes is "nearly" filled up.
     */
    void writeToDataAccess() {
        if (writeOffset > 0)
            dataAccess.setBytes(dataAccessPointer, bytes, writeOffset);
    }

    public final void writeZInt(int i) {
        writeVInt(zigZagEncode(i));
    }

    final void writeVInt(int i) {
        if (writeOffset + 5 >= bytes.length) {
            writeToDataAccess();
            dataAccessPointer += writeOffset;
            writeOffset = 0;
        }

        while ((i & ~0x7F) != 0) {
            bytes[writeOffset++] = (byte) ((i & 0x7F) | 0x80);
            i >>>= 7;
        }
        bytes[writeOffset++] = (byte) i;
    }

    /**
     * <a href="https://developers.google.com/protocol-buffers/docs/encoding#types">Zig-zag</a>
     * encode the provided long. Assuming the input is a signed long whose
     * absolute value can be stored on <code>n</code> bits, the returned value will
     * be an unsigned long that can be stored on <code>n+1</code> bits.
     */
    static int zigZagEncode(int i) {
        return (i >> 31) ^ (i << 1);
    }

    static int zigZagDecode(int i) {
        return ((i >>> 1) ^ -(i & 1));
    }

    public int readZInt() {
        return zigZagDecode(readVInt());
    }

    public static int readVInt(DataAccess dataAccess, long pointer) {
        byte valueByte = dataAccess.getByte(pointer++);
        if (valueByte >= 0) return valueByte;

        int valueInt = valueByte & 0x7F;
        valueByte = dataAccess.getByte(pointer++);
        valueInt |= (valueByte & 0x7F) << 7;
        if (valueByte >= 0) return valueInt;

        valueByte = dataAccess.getByte(pointer++);
        valueInt |= (valueByte & 0x7F) << 14;
        if (valueByte >= 0) return valueInt;

        valueByte = dataAccess.getByte(pointer++);
        valueInt |= (valueByte & 0x7F) << 21;
        if (valueByte >= 0) return valueInt;

        valueByte = dataAccess.getByte(pointer);
        // Warning: the next ands use 0x0F / 0xF0 - beware copy/paste errors:
        valueInt |= (valueByte & 0x0F) << 28;
        if ((valueByte & 0xF0) == 0) return valueInt;
        throw new IllegalStateException("Invalid vInt detected (too many bits)");
    }

    int readVInt() {
        if (readOffset + 3 >= bytes.length) { // refresh but check only twice per readVInt => 3+2=5 (5 is maximum)
            // the first action is readFromDataAccess so increase it before the next call (unlike write)
            dataAccessPointer += readOffset;
            readFromDataAccess();
            readOffset = 0;
        }
        // unrolled loop because of a JVM bug, see LUCENE-2975
        byte b = bytes[readOffset++];
        if (b >= 0) return b;
        int i = b & 0x7F;
        b = bytes[readOffset++];
        i |= (b & 0x7F) << 7;
        if (b >= 0) return i;
        b = bytes[readOffset++];
        i |= (b & 0x7F) << 14;
        if (b >= 0) return i;

        if (readOffset + 2 >= bytes.length) {
            dataAccessPointer += readOffset;
            readFromDataAccess();
            readOffset = 0;
        }
        b = bytes[readOffset++];
        i |= (b & 0x7F) << 21;
        if (b >= 0) return i;
        b = bytes[readOffset++];
        // Warning: the next ands use 0x0F / 0xF0 - beware copy/paste errors:
        i |= (b & 0x0F) << 28;
        if ((b & 0xF0) == 0) return i;
        throw new IllegalStateException("Invalid vInt detected (too many bits)");
    }
}
