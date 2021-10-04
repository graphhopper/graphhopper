package com.graphhopper.storage;

import java.util.ArrayList;

// ORS-GH MOD - additional class
// ORS TODO: provide reason for this change
public class ExtendedStorageSequence  implements Storable<ExtendedStorageSequence> {

    private Storable[] extensions;
    private int numExtensions;

    public ExtendedStorageSequence(ArrayList<Storable<Object>> seq) {
        numExtensions = seq.size();
        extensions = seq.toArray(new Storable[numExtensions]);
    }

    public Storable<Object>[] getExtensions() {
        return extensions;
    }

    public ExtendedStorageSequence create(long initSize) {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].create(initSize);
        }

        return this;
    }

    public boolean loadExisting() {
        boolean result = true;
        for (int i = 0; i < numExtensions; i++) {
            if (!extensions[i].loadExisting()) {
                result = false;
                break;
            }
        }

        return result;
    }

    public void flush() {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].flush();
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].close();
        }
    }

    public long getCapacity() {
        long capacity = 0;

        for (int i = 0; i < extensions.length; i++) {
            capacity += extensions[i].getCapacity();
        }

        return capacity;
    }

    @Override
    public String toString() {
        return "ExtSequence";
    }

    @Override
    public boolean isClosed() {
        // TODO Auto-generated method stub
        return false;
    }
}

