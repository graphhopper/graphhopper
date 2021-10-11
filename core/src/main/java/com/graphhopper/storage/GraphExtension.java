package com.graphhopper.storage;

// TODO ORS: this is a transitional class which should be eliminated in the long run
// ORS-GH MOD - additional class
public interface GraphExtension extends Storable<GraphExtension> {
    default long getCapacity() { return 0; }
}
// ORS-GH MOD