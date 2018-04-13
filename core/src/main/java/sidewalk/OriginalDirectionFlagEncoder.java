package com.conveyal.r5.graphhopper;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;

public class OriginalDirectionFlagEncoder extends AbstractFlagEncoder {


    private long originalDirectionBitmask;

    protected OriginalDirectionFlagEncoder() {
        super(0, 0, 0);
    }

    @Override
    public int defineWayBits(int index, int shift) {
        shift = super.defineWayBits(index, shift);
        this.originalDirectionBitmask = 1L << shift;
        return shift + 1;
    }

    @Override
    public long handleRelationTags(ReaderRelation readerRelation, long l) {
        return l;
    }

    @Override
    public long acceptWay(ReaderWay readerWay) {
        return 0;
    }

    @Override
    public long handleWayTags(ReaderWay readerWay, long l, long l1) {
        return 0;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    public long reverseFlags(long flags) {
        return super.reverseFlags(flags) ^ this.directionBitMask;
    }

    public boolean isOriginalDirection(long flags) {
        return (flags & this.originalDirectionBitmask) != 0L;
    }

    public long setOriginalDirection(long flags, boolean originalDirection) {
        return originalDirection ? flags | originalDirectionBitmask : flags & ~originalDirectionBitmask;
    }

    public String toString() {
        return "original-direction";
    }

}
