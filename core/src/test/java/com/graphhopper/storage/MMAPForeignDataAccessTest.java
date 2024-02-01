package com.graphhopper.storage;

public class MMAPForeignDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name, int segmentSize) {
        return new MMAPForeignDataAccess(name, directory, true, segmentSize);
    }
}
