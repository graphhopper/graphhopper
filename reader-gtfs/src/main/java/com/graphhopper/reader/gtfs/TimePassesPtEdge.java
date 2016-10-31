package com.graphhopper.reader.gtfs;

class TimePassesPtEdge extends AbstractPtEdge {
    final int deltaTime;

    TimePassesPtEdge(int deltaTime) {
        this.deltaTime = deltaTime;
    }
}
