package com.graphhopper.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GHDirectoryTest {

    @Test
    public void testConfigure() {
        GHDirectory dir = new GHDirectory("", DAType.RAM);
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("nodes", "FOREIGN_MMAP");
        dir.configure(map);
        assertEquals(DAType.FOREIGN_MMAP, dir.getDefaultType("nodes", true));

        // first rule wins
        map.put("preload.nodes", "10");
        map.put("preload.nodes.*", "100");
        dir.configure(map);
        assertEquals(10, dir.getPreload("nodes"));
    }

    @Test
    public void testPatternMatching() {
        GHDirectory dir = new GHDirectory("", DAType.RAM);
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("nodes_ch.*", "FOREIGN_MMAP");
        dir.configure(map);
        assertEquals(DAType.RAM, dir.getDefaultType("nodes", false));
        assertEquals(DAType.FOREIGN_MMAP, dir.getDefaultType("nodes_ch_car", false));
    }

}