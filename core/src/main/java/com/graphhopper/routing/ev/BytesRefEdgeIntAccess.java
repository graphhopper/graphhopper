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
        // TODO NOW what was the behaviour for signed values!? because for e.g. int3 we ignore the sign
        if (pointer + 3 == bytesRef.bytes.length)
            return bitUtil.toInt3(bytesRef.bytes, pointer);
        if (pointer + 2 == bytesRef.bytes.length)
            return bitUtil.toShort(bytesRef.bytes, pointer);
        if (pointer + 1 == bytesRef.bytes.length)
            return bytesRef.bytes[pointer];
        return bitUtil.toInt(bytesRef.bytes, pointer);
    }

    @Override
    public void setInt(int edgeId, int index, int value) {
        int pointer = index * 4;
        if (pointer + 3 == bytesRef.bytes.length)
            bitUtil.fromInt3(bytesRef.bytes, value, pointer);
        else if (pointer + 2 == bytesRef.bytes.length)
            bitUtil.fromShort(bytesRef.bytes, (short) value, pointer);
        else if (pointer + 1 == bytesRef.bytes.length)
            bytesRef.bytes[pointer] = (byte) value;
        else
            bitUtil.fromInt(bytesRef.bytes, value, pointer);
    }
}
