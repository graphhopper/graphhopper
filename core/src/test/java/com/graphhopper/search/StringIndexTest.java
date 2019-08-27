package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StringIndexTest {

    private StringIndex create() {
        return new StringIndex(new RAMDirectory()).create(1000);
    }

    Map<String, String> createMap(String... strings) {
        if (strings.length % 2 != 0)
            throw new IllegalArgumentException("Cannot create map from strings " + Arrays.toString(strings));
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < strings.length; i += 2) {
            map.put(strings[i], strings[i + 1]);
        }
        return map;
    }

    @Test
    public void cleanup() {
        Map<String, String> map = createMap("a", "same name",
                "b", "same name",
                "c", "other name");
        Map<String, String> res = StringIndex.cleanup(map);
        assertEquals(2, res.size());

        res.put("d", "other name");
        res = StringIndex.cleanup(map);
        assertEquals(2, res.size());
    }

    @Test
    public void putSame() {
        StringIndex index = create();
        long aPointer = index.put(createMap("a", "same name",
                "b", "same name"));

        assertEquals("same name", index.get(aPointer));
        assertEquals("same name", index.get(aPointer, "a"));
        assertEquals("same name", index.get(aPointer, "b"));
        // TODO fallback vs. fail fast ?
        assertEquals("same name", index.get(aPointer, "c"));
    }

    @Test
    public void putSame2() {
        StringIndex index = create();
        long aPointer = index.put(createMap("a", "same name",
                "b", "same name"));

        assertEquals("a name", index.get(aPointer));
    }

    @Test
    public void putAB() {
        StringIndex index = create();
        long aPointer = index.put(createMap("a", "a name",
                "b", "b name"));

        assertEquals("a name", index.get(aPointer));
        assertEquals("a name", index.get(aPointer, "a"));
        assertEquals("b name", index.get(aPointer, "b"));
    }
}