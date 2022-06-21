package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CHMatrixStorageTest {

    @Test
    void setAndGetLevels() {
        RAMDirectory dir = new RAMDirectory();
        CHStorage store = new CHStorage(dir, "ch1", -1, false);
        store.create();
        store.init(30, 5);
        assertEquals(0, store.getLevel(store.toNodePointer(10)));
        store.setLevel(store.toNodePointer(10), 100);
        assertEquals(100, store.getLevel(store.toNodePointer(10)));
        store.setLevel(store.toNodePointer(29), 300);
        assertEquals(300, store.getLevel(store.toNodePointer(29)));
    }

    @Test
    void createAndLoad(@TempDir Path path) {
        {
            GHDirectory dir = new GHDirectory(path.toAbsolutePath().toString(), DAType.RAM_INT_STORE);
            CHStorage chStorage = new CHStorage(dir, "car", -1, false);
            // we have to call create, because we want to create a new storage not load an existing one
            chStorage.create();
            // init is needed as well, because we have to set the nodes capacity and we cannot do this in create() yet.
            // we can also not use init instead of create, because currently GraphHopperStorage needs to 'create' all
            // its data objects. if we want to change this lifecycle we need to change this in GraphHopperStorage first
            chStorage.init(5, 3);
            assertEquals(0, chStorage.shortcutNodeBased(0, 1, PrepareEncoder.getScFwdDir(), 10, 20,10,3, 5));
            assertEquals(1, chStorage.shortcutNodeBased(1, 2, PrepareEncoder.getScFwdDir(), 11, 21,11,4, 6));
            assertEquals(2, chStorage.shortcutNodeBased(2, 3, PrepareEncoder.getScFwdDir(), 12, 22,12,5, 7));
            // exceeding the number of expected shortcuts is ok, the container will just grow
            assertEquals(3, chStorage.shortcutNodeBased(3, 4, PrepareEncoder.getScFwdDir(), 13,23,13, 6, 8));
            assertEquals(5, chStorage.getNodes());
            assertEquals(4, chStorage.getShortcuts());
            chStorage.flush();
            chStorage.close();
        }
        {
            GHDirectory dir = new GHDirectory(path.toAbsolutePath().toString(), DAType.RAM_INT_STORE);
            CHStorage chStorage = new CHStorage(dir, "car", -1, false);
            // this time we load from disk
            chStorage.loadExisting();
            assertEquals(4, chStorage.getShortcuts());
            assertEquals(5, chStorage.getNodes());
            long ptr = chStorage.toShortcutPointer(0);
            assertEquals(0, chStorage.getNodeA(ptr));
            assertEquals(1, chStorage.getNodeB(ptr));
            assertEquals(10, chStorage.getWeight(ptr));
            assertEquals(20, chStorage.getDistance(ptr));
            assertEquals(10, chStorage.getTime(ptr));
            assertEquals(3, chStorage.getSkippedEdge1(ptr));
            assertEquals(5, chStorage.getSkippedEdge2(ptr));
        }
    }

    @Test
    public void testBigWeight() {
        CHStorage g = new CHStorage(new RAMDirectory(), "abc", 1024, false);
        g.shortcutNodeBased(0, 0, 0, 10,20,30, 0, 1);

        g.setWeight(0, Integer.MAX_VALUE / 1000d + 1000);
        assertEquals(Integer.MAX_VALUE / 1000d + 1000, g.getWeight(0));

        g.setWeight(0, ((long) Integer.MAX_VALUE << 1) / 1000d - 0.001);
        assertEquals(((long) Integer.MAX_VALUE << 1) / 1000d - 0.001, g.getWeight(0), 0.001);

        g.setWeight(0, ((long) Integer.MAX_VALUE << 1) / 1000d);
        assertTrue(Double.isInfinite(g.getWeight(0)));
        g.setWeight(0, ((long) Integer.MAX_VALUE << 1) / 1000d + 1);
        assertTrue(Double.isInfinite(g.getWeight(0)));
        g.setWeight(0, ((long) Integer.MAX_VALUE << 1) / 1000d + 100);
        assertTrue(Double.isInfinite(g.getWeight(0)));
    }

    @Test
    public void testBigDistance() {
        CHStorage g = new CHStorage(new RAMDirectory(), "abc", 1024, false);
        g.shortcutNodeBased(0, 0, 0, 10,20,30, 0, 1);

        g.setDistance(0, Integer.MAX_VALUE / 1000d + 1000);
        assertEquals(Integer.MAX_VALUE / 1000d + 1000, g.getDistance(0));

        g.setDistance(0, ((long) Integer.MAX_VALUE << 1) / 1000d - 0.001);
        assertEquals(((long) Integer.MAX_VALUE << 1) / 1000d - 0.001, g.getDistance(0), 0.001);

        g.setDistance(0, ((long) Integer.MAX_VALUE << 1) / 1000d);
        assertTrue(Double.isInfinite(g.getDistance(0)));
        g.setDistance(0, ((long) Integer.MAX_VALUE << 1) / 1000d + 1);
        assertTrue(Double.isInfinite(g.getDistance(0)));
        g.setDistance(0, ((long) Integer.MAX_VALUE << 1) / 1000d + 100);
        assertTrue(Double.isInfinite(g.getDistance(0)));
    }

    @Test
    public void testBigTime() {
        CHStorage g = new CHStorage(new RAMDirectory(), "abc", 1024, false);
        g.shortcutNodeBased(0, 0, 0, 10,20,30, 0, 1);

        g.setTime(0, Integer.MAX_VALUE / 1000L + 1000);
        assertEquals(Integer.MAX_VALUE / 1000L + 1000, g.getTime(0));

        g.setTime(0, ((long) Integer.MAX_VALUE << 1) / 1000L);
        assertEquals(((long) Integer.MAX_VALUE << 1) / 1000L, g.getTime(0));
    }
    
}