package com.graphhopper.reader.gtfs;

import java.util.BitSet;

class BoardEdge extends TimePassesPtEdge {
    BoardEdge(int i, BitSet validOn) {
        super(i);
    }
}
