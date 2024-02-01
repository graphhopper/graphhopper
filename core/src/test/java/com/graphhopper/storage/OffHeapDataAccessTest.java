package com.graphhopper.storage;

public class OffHeapDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name, int segmentSize) {
        return new OffHeapDataAccess(name, directory, segmentSize);
    }

}
