package com.graphhopper.storage;

// TODO ORS: this is a transitional class which should be eliminated in the long run
// ORS-GH MOD - additional class
public interface GraphExtension extends Storable<GraphExtension> {
    GraphExtension create(long initSize);

    void init(Graph graph, Directory dir);

    boolean loadExisting();

    void flush();

    long getCapacity();
}
// ORS-GH MOD