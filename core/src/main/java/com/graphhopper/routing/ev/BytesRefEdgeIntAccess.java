package com.graphhopper.routing.ev;

import com.graphhopper.storage.BytesRef;
import com.graphhopper.util.BitUtil;

public class BytesRefEdgeIntAccess implements EdgeIntAccess {
    private static final BitUtil bitUtil = BitUtil.LITTLE;
    private final BytesRef bytesRef;

    public BytesRefEdgeIntAccess(BytesRef bytesRef) {
        this.bytesRef = bytesRef;
    }

    @Override
    public int getInt(int edgeId, int index) {
        int pointer = index * 4;
        if (pointer + 3 == bytesRef.bytes.length)
            return bitUtil.toUInt3(bytesRef.bytes, pointer);
        if (pointer + 2 == bytesRef.bytes.length)
            return bitUtil.toShort(bytesRef.bytes, pointer);
        if (pointer + 1 == bytesRef.bytes.length)
            return bytesRef.bytes[pointer];
        return bitUtil.toInt(bytesRef.bytes, pointer);
    }

    @Override
    public void setInt(int edgeId, int index, int value) {
        int pointer = index * 4;
        if (pointer + 3 == bytesRef.bytes.length) {
            if ((value & 0xFF000000) != 0)
                throw new IllegalArgumentException("value at index " + index + "must not have the highest byte set but was " + value);
            bitUtil.fromUInt3(bytesRef.bytes, value, pointer);
        } else if (pointer + 2 == bytesRef.bytes.length) {
            if ((value & 0xFFFF0000) != 0)
                throw new IllegalArgumentException("value at index " + index + " must not have the 2 highest bytes set but was " + value);
            bitUtil.fromShort(bytesRef.bytes, (short) value, pointer);
        } else if (pointer + 1 == bytesRef.bytes.length) {
            if ((value & 0xFFFFFF00) != 0)
                throw new IllegalArgumentException("value at index " + index + "must not have the 3 highest bytes set but was " + value);
            bytesRef.bytes[pointer] = (byte) value;
        } else
            bitUtil.fromInt(bytesRef.bytes, value, pointer);
    }
}
