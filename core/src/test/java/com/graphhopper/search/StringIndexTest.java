package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.File;
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
        long aPointer = index.add(createMap("a", "same name",
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
        long aPointer = index.add(createMap("a", "a name",
                "b", "same name"));

        assertEquals("a name", index.get(aPointer));
    }

    @Test
    public void putAB() {
        StringIndex index = create();
        long aPointer = index.add(createMap("a", "a name",
                "b", "b name"));

        assertEquals("a name", index.get(aPointer));
        assertEquals("a name", index.get(aPointer, "a"));
        assertEquals("b name", index.get(aPointer, "b"));
    }

    // TODO NOW
    @Test
    public void putEmpty() {
        StringIndex index = create();
        assertEquals(1, index.add(createMap("", "")));
        assertEquals(1, index.add(createMap("", null)));
        assertEquals(1, index.add(createMap(null, null)));
        assertEquals("", index.get(0));

        assertEquals(1 + 2, index.add(createMap("else", "else")));
    }

    @Test
    public void putMany() {
        StringIndex index = create();
        long aPointer = 0, tmpPointer = 0;

        for (int i = 0; i < 10000; i++) {
            aPointer = index.add(createMap("a", "a name " + i, "b", "b name " + i, "c", "c name " + i));

            if (i == 567)
                tmpPointer = aPointer;
        }

        assertEquals("b name 9999", index.get(aPointer, "b"));
        assertEquals("c name 9999", index.get(aPointer, "c"));

        assertEquals("a name 567", index.get(tmpPointer, "a"));
        assertEquals("b name 567", index.get(tmpPointer, "b"));
        assertEquals("c name 567", index.get(tmpPointer, "c"));
    }

    // TODO NOW
    @Test
    public void putDuplicate() {
        StringIndex index = create();
        long aPointer = index.add(createMap("a", "name", "b", "name"));
        long bPointer = index.add(createMap("else", "else"));
        assertEquals(aPointer + 1 + 2 + "name".getBytes(Helper.UTF_CS).length, bPointer);

        index = create();
        aPointer = index.add(createMap("a", "name", "b", "name"));
        bPointer = index.add(createMap("a", "name", "b", "name"));
        assertEquals(aPointer, bPointer);
    }

    @Test
    public void testNoErrorOnLargeName() {
        StringIndex index = create();
        // 127 => bytes.length == 254
        String str = "";
        for (int i = 0; i < 127; i++) {
            str += "ß";
        }
        long result = index.add(createMap("", str));
        assertEquals(127, index.get(result).length());
    }

    @Test
    public void testTooLongNameNoError() {
        StringIndex index = create();
        // WTH are they doing in OSM? There are exactly two names in the full planet export which violates this limitation!
        index.add(createMap("", "Бухарестская улица (http://ru.wikipedia.org/wiki/%D0%91%D1%83%D1%85%D0%B0%D1%80%D0%B5%D1%81%D1%82%D1%81%D0%BA%D0%B0%D1%8F_%D1%83%D0%BB%D0%B8%D1%86%D0%B0_(%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3))"));

        String str = "sdfsdfds";
        for (int i = 0; i < 256 * 3; i++) {
            str += "Б";
        }
        index.add(createMap("", str));
    }

    @Test
    public void testFlush() {
        String location = "./target/stringindex-store";
        Helper.removeDir(new File(location));

        StringIndex index = new StringIndex(new RAMDirectory(location, true).create()).create(1000);
        long pointer = index.add(createMap("", "test"));
        index.flush();
        index.close();

        index = new StringIndex(new RAMDirectory(location, true));
        assertTrue(index.loadExisting());
        assertEquals("test", index.get(pointer));
        // make sure bytePointer is correctly set after loadExisting
        long newPointer = index.add(createMap("", "testing"));
        assertEquals(newPointer + ">" + pointer, pointer + "test".getBytes().length + 1 + 2, newPointer);
        index.close();

        Helper.removeDir(new File(location));
    }

    // TODO NOW
    @Test
    public void testLoadKeys() {
        String location = "./target/stringindex-store";
        Helper.removeDir(new File(location));

        StringIndex index = new StringIndex(new RAMDirectory(location, true).create()).create(1000);
        long pointerA = index.add(createMap("", "test value"));
        long pointerB = index.add(createMap("a", "value", "b", "another value"));
        index.flush();
        index.close();

        index = new StringIndex(new RAMDirectory(location, true));
        assertTrue(index.loadExisting());
        assertEquals("test value", index.get(pointerA));
        assertEquals("test value", index.get(pointerA, ""));

        assertEquals("value", index.get(pointerB, ""));
        assertEquals("value", index.get(pointerB, "a"));
        assertEquals("another value", index.get(pointerA, "b"));
        index.close();

        Helper.removeDir(new File(location));
    }
}