package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        // fallback vs. fail fast ?
        try {
            index.get(aPointer, "c");
            fail("get should fail fast");
        } catch (Exception ex) {
            assertTrue(true);
        }
    }

    @Test
    public void putSame2() {
        StringIndex index = create();
        long aPointer = index.put(createMap("a", "a name",
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

    @Test
    public void putMany() {
        StringIndex index = create();
        long aPointer = 0, tmpPointer = 0;

        for (int i = 0; i < 10000; i++) {
            aPointer = index.put(createMap("a", "a name " + i, "b", "b name " + i, "c", "c name " + i));

            if(i == 567)
                tmpPointer = aPointer;
        }

        assertEquals("b name 9999", index.get(aPointer, "b"));
        assertEquals("c name 9999", index.get(aPointer, "c"));

        assertEquals("a name 567", index.get(tmpPointer, "a"));
        assertEquals("b name 567", index.get(tmpPointer, "b"));
        assertEquals("c name 567", index.get(tmpPointer, "c"));
    }
}