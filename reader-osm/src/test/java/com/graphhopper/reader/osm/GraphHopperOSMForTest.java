package com.graphhopper.reader.osm;

/**
 * For testing, i.e. preserve raw network topology and use in-memory.
 */
public class GraphHopperOSMForTest extends GraphHopperOSM {
    public GraphHopperOSMForTest() {
        setMinNetworkSize(0, 0);
        setStoreOnFlush(false);
    }
}
