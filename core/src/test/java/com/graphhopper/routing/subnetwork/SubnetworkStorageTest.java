package com.graphhopper.routing.subnetwork;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubnetworkStorageTest {

    @Test
    public void testSimple() {
        SubnetworkStorage storage = new SubnetworkStorage(new RAMDirectory(), "fastest");
        storage.create(2000);
        storage.setSubnetwork(1, 88);
        assertEquals(88, storage.getSubnetwork(1));
        assertEquals(0, storage.getSubnetwork(0));

        storage.close();
    }
}