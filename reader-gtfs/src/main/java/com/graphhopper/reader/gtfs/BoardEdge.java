package com.graphhopper.reader.gtfs;

import java.util.BitSet;

class BoardEdge extends TimePassesPtEdge {
    final BitSet validOn;

    BoardEdge(int i, BitSet validOn) {
        super(i);
        this.validOn = validOn;
    }
}
