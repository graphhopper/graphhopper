package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

public abstract class FlagEncoderManagerProtocol implements FlagEncoder {
    public abstract boolean isRegistered();

    public abstract void setRegistered(boolean b);

    public abstract int defineNodeBits(int encoderCount, int nextNodeBit);

    public abstract void setNodeBitMask(int i, int nextNodeBit);

    public abstract int defineWayBits(int encoderCount, int nextWayBit);

    public abstract void setWayBitMask(int i, int nextWayBit);

    public abstract int defineRelationBits(int encoderCount, int nextRelBit);

    public abstract void setRelBitMask(int i, int nextRelBit);

    public abstract int defineTurnBits(int encoderCount, int nextTurnBit);

    public abstract long acceptWay(ReaderWay way);

    public abstract long handleRelationTags(ReaderRelation relation, long oldRelationFlags);

    public abstract long getRelBitMask();

    public abstract long handleWayTags(ReaderWay way, long includeWay, long l);

    public abstract char[] getPropertiesString();

    public abstract long flagsDefault(boolean forward, boolean backward);

    public abstract long reverseFlags(long flags);

    public abstract long handleNodeTags(ReaderNode node);

    public abstract void applyWayTags(ReaderWay way, EdgeIteratorState edge);
}
