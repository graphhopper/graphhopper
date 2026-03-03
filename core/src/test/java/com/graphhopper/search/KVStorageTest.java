package com.graphhopper.search;

import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.search.KVStorage.KValue;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static com.graphhopper.search.KVStorage.MAX_UNIQUE_KEYS;
import static com.graphhopper.search.KVStorage.cutString;
import static com.graphhopper.util.Helper.UTF_CS;
import static org.junit.jupiter.api.Assertions.*;

public class KVStorageTest {

    private final static String location = "./target/edge-kv-storage";

    private KVStorage create() {
        return new KVStorage(new GHDirectory("", DAType.RAM), true).create(1000);
    }

    Map<String, KValue> createMap(Object... keyValues) {
        if (keyValues.length % 2 != 0)
            throw new IllegalArgumentException("Cannot create list from " + Arrays.toString(keyValues));
        Map<String, KValue> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], new KValue(keyValues[i + 1]));
        }
        return map;
    }

    @Test
    public void putSame() {
        KVStorage index = create();
        long aPointer = index.add(createMap("a", "same name", "b", "same name"));

        assertNull(index.get(aPointer, "", false));
        assertEquals("same name", index.get(aPointer, "a", false));
        assertEquals("same name", index.get(aPointer, "b", false));
        assertNull(index.get(aPointer, "c", false));

        index = create();
        aPointer = index.add(createMap("a", "a name", "b", "same name"));
        assertEquals("a name", index.get(aPointer, "a", false));
    }

    @Test
    public void putAB() {
        KVStorage index = create();
        long aPointer = index.add(createMap("a", "a name", "b", "b name"));

        assertNull(index.get(aPointer, "", false));
        assertEquals("a name", index.get(aPointer, "a", false));
        assertEquals("b name", index.get(aPointer, "b", false));
    }

    @Test
    public void getForwardBackward() {
        KVStorage index = create();
        Map<String, KValue> map = new LinkedHashMap<>();
        map.put("keyA", new KValue("FORWARD", null));
        map.put("keyB", new KValue(null, "BACKWARD"));
        map.put("keyC", new KValue("BOTH"));
        map.put("keyD", new KValue("BOTH1", "BOTH2"));
        long aPointer = index.add(map);

        assertNull(index.get(aPointer, "", false));
        Map<String, KValue> deserializedList = index.getAll(aPointer);
        assertEquals(map, deserializedList);

        assertEquals("FORWARD", index.get(aPointer, "keyA", false));
        assertNull(index.get(aPointer, "keyA", true));

        assertNull(index.get(aPointer, "keyB", false));
        assertEquals("BACKWARD", index.get(aPointer, "keyB", true));

        assertEquals("BOTH", index.get(aPointer, "keyC", false));
        assertEquals("BOTH", index.get(aPointer, "keyC", true));

        assertEquals("BOTH1", index.get(aPointer, "keyD", false));
        assertEquals("BOTH2", index.get(aPointer, "keyD", true));
    }

    @Test
    public void putEmpty() {
        KVStorage index = create();
        long emptyKeyPointer = index.add(createMap("", ""));
        // First pointer should be at START_POINTER (aligned to 4)
        assertEquals(4, emptyKeyPointer);
        // cannot store null (in its first version we accepted null once it was clear which type the value has, but this is inconsequential)
        assertThrows(IllegalArgumentException.class, () -> index.add(createMap("", null)));
        assertThrows(IllegalArgumentException.class, () -> index.add(createMap("blup", null)));
        assertThrows(IllegalArgumentException.class, () -> index.add(createMap(null, null)));

        assertNull(index.get(0, "", false));

        long elsePointer = index.add(createMap("else", "else"));
        assertTrue(elsePointer > emptyKeyPointer, "second pointer should be larger than first");
        assertEquals("else", index.get(elsePointer, "else", false));
    }

    @Test
    public void putMany() {
        KVStorage index = create();
        long aPointer = 0, tmpPointer = 0;

        for (int i = 0; i < 10000; i++) {
            aPointer = index.add(createMap("a", "a name " + i, "b", "b name " + i, "c", "c name " + i));
            if (i == 567)
                tmpPointer = aPointer;
        }

        assertEquals("b name 9999", index.get(aPointer, "b", false));
        assertEquals("c name 9999", index.get(aPointer, "c", false));

        assertEquals("a name 567", index.get(tmpPointer, "a", false));
        assertEquals("b name 567", index.get(tmpPointer, "b", false));
        assertEquals("c name 567", index.get(tmpPointer, "c", false));
    }

    @Test
    public void putManyKeys() {
        KVStorage index = create();
        // one key is already stored => empty key
        for (int i = 1; i < MAX_UNIQUE_KEYS; i++) {
            index.add(createMap("a" + i, "a name"));
        }
        try {
            index.add(createMap("new", "a name"));
            fail();
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testHighKeyIndicesUpToDesignLimit() {
        // This test verifies that key indices >= 8192 work correctly.
        // Previously there was a sign extension bug when reading shorts for key indices >= 8192
        // because (keyIndex << 2) exceeds 32767 and becomes negative when stored as a signed short.
        KVStorage index = create();

        // Create MAX_UNIQUE_KEYS - 1 unique keys (index 0 is reserved for empty key)
        // This gives us key indices from 1 to MAX_UNIQUE_KEYS - 1 (i.e., 1 to 16383)
        List<Long> pointers = new ArrayList<>();
        for (int i = 1; i < MAX_UNIQUE_KEYS; i++) {
            long pointer = index.add(createMap("key" + i, "value" + i));
            pointers.add(pointer);
        }

        // Verify we can read back entries that use high key indices (>= 8192)
        // Key index 8192 is the first one that triggers the sign extension issue
        for (int i = 8192; i < MAX_UNIQUE_KEYS; i++) {
            long pointer = pointers.get(i - 1); // pointers list is 0-indexed, keys start at 1
            String expectedKey = "key" + i;
            String expectedValue = "value" + i;

            // Test get() method
            assertEquals(expectedValue, index.get(pointer, expectedKey, false),
                    "get() failed for key index " + i);

            // Test getMap() method
            Map<String, Object> map = index.getMap(pointer);
            assertEquals(expectedValue, map.get(expectedKey),
                    "getMap() failed for key index " + i);

            // Test getAll() method
            Map<String, KValue> allMap = index.getAll(pointer);
            assertEquals(expectedValue, allMap.get(expectedKey).getFwd(),
                    "getAll() failed for key index " + i);
        }
    }

    @Test
    public void testNoErrorOnLargeStringValue() {
        KVStorage index = create();
        String str = "";
        for (int i = 0; i < 127; i++) {
            str += "ß";
        }
        assertEquals(254, str.getBytes(Helper.UTF_CS).length);
        long result = index.add(createMap("", str));
        assertEquals(127, ((String) index.get(result, "", false)).length());
    }

    @Test
    public void testTooLongStringValueError() {
        KVStorage index = create();
        assertThrows(IllegalArgumentException.class, () -> index.add(createMap("", "Бухарестская улица (http://ru.wikipedia.org/wiki" +
                "/%D0%91%D1%83%D1%85%D0%B0%D1%80%D0%B5%D1%81%D1%82%D1%81%D0%BA%D0%B0%D1%8F_%D1%83%D0%BB%D0%B8%D1%86%D0%B0_(%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3))")));

        String str = "sdfsdfds";
        for (int i = 0; i < 256 * 3; i++) {
            str += "Б";
        }
        final String finalStr = str;
        assertThrows(IllegalArgumentException.class, () -> index.add(createMap("", finalStr)));
    }

    @Test
    public void testNoErrorOnLargestByteArray() {
        KVStorage index = create();
        byte[] bytes = new byte[255];
        byte[] copy = new byte[255];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 255);
            copy[i] = bytes[i];
        }
        long result = index.add(Map.of("myval", new KValue(bytes)));
        bytes = (byte[]) index.get(result, "myval", false);
        assertArrayEquals(copy, bytes);

        final byte[] biggerByteArray = Arrays.copyOf(bytes, 256);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> index.add(Map.of("myval2", new KValue(biggerByteArray))));
        assertTrue(e.getMessage().contains("bytes.length cannot be > 255"));
    }

    @Test
    public void testIntLongDoubleFloat() {
        KVStorage index = create();
        long intres = index.add(Map.of("intres", new KValue(4)));
        long doubleres = index.add(Map.of("doubleres", new KValue(4d)));
        long floatres = index.add(Map.of("floatres", new KValue(4f)));
        long longres = index.add(Map.of("longres", new KValue(4L)));
        long after4Inserts = index.add(Map.of("somenext", new KValue(0)));

        // Pointers should be sequential and increasing, aligned to 4-byte boundaries
        assertTrue(intres < doubleres);
        assertTrue(doubleres < floatres);
        assertTrue(floatres < longres);
        assertTrue(longres < after4Inserts);
        // Verify all pointers are 4-byte aligned
        assertEquals(0, intres % 4);
        assertEquals(0, doubleres % 4);
        assertEquals(0, floatres % 4);
        assertEquals(0, longres % 4);
        assertEquals(0, after4Inserts % 4);

        assertEquals(4f, index.get(floatres, "floatres", false));
        assertEquals(4L, index.get(longres, "longres", false));
        assertEquals(4d, index.get(doubleres, "doubleres", false));
        assertEquals(4, index.get(intres, "intres", false));
    }

    @Test
    public void testIntLongDoubleFloat2() {
        KVStorage index = create();
        Map<String, KValue> map = new LinkedHashMap<>();
        map.put("int", new KValue(4));
        map.put("long", new KValue(4L));
        map.put("double", new KValue(4d));
        map.put("float", new KValue(4f));
        long allInOne = index.add(map);

        long afterMapInsert = index.add(Map.of("somenext", new KValue(0)));

        // Pointer should increase after adding more data, and both should be aligned
        assertTrue(afterMapInsert > allInOne);
        assertEquals(0, allInOne % 4);
        assertEquals(0, afterMapInsert % 4);

        Map<String, KValue> resMap = index.getAll(allInOne);
        assertEquals(4, resMap.get("int").getFwd());
        assertEquals(4L, resMap.get("long").getFwd());
        assertEquals(4d, resMap.get("double").getFwd());
        assertEquals(4f, resMap.get("float").getFwd());
    }

    @Test
    public void testFlush() {
        Helper.removeDir(new File(location));

        KVStorage index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE).create(), true);
        long pointer = index.add(createMap("", "test"));
        index.flush();
        index.close();

        index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE), true);
        assertTrue(index.loadExisting());
        assertEquals("test", index.get(pointer, "", false));
        // make sure bytePointer is correctly set after loadExisting
        long newPointer = index.add(createMap("", "testing"));
        assertTrue(newPointer > pointer, "newPointer " + newPointer + " should be > pointer " + pointer);
        assertEquals(0, newPointer % 4, "newPointer should be 4-byte aligned");
        assertEquals("testing", index.get(newPointer, "", false));
        index.close();

        Helper.removeDir(new File(location));
    }

    @Test
    public void testLoadKeys() {
        Helper.removeDir(new File(location));

        KVStorage index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE).create(), true).create(1000);
        long pointerA = index.add(createMap("c", "test value"));
        assertEquals(2, index.getKeys().size());
        long pointerB = index.add(createMap("a", "value", "b", "another value"));
        // empty string is always the first key
        assertEquals("[, c, a, b]", index.getKeys().toString());
        index.flush();
        index.close();

        index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE), true);
        assertTrue(index.loadExisting());
        assertEquals("[, c, a, b]", index.getKeys().toString());
        assertEquals("test value", index.get(pointerA, "c", false));
        assertNull(index.get(pointerA, "b", false));

        assertNull(index.get(pointerB, "", false));
        assertEquals("value", index.get(pointerB, "a", false));
        assertEquals("another value", index.get(pointerB, "b", false));
        assertEquals("{a=value, b=another value}", index.getAll(pointerB).toString());
        index.close();

        Helper.removeDir(new File(location));
    }

    @Test
    public void testEmptyKey() {
        KVStorage index = create();
        long pointerA = index.add(createMap("", "test value"));
        long pointerB = index.add(createMap("a", "value", "b", "another value"));

        assertEquals("test value", index.get(pointerA, "", false));
        assertNull(index.get(pointerA, "a", false));

        assertEquals("value", index.get(pointerB, "a", false));
        assertNull(index.get(pointerB, "", false));
    }

    @Test
    public void testDifferentValuePerDirection() {
        Map<String, KValue> map = new LinkedHashMap<>();
        map.put("test", new KValue("forw", "back"));

        KVStorage index = create();
        long pointerA = index.add(map);

        assertEquals("forw", index.get(pointerA, "test", false));
        assertEquals("back", index.get(pointerA, "test", true));
    }

    @Test
    public void testSameByteArray() {
        KVStorage index = create();

        long pointerA = index.add(createMap("mykey", new byte[]{1, 2, 3, 4}));
        long pointerB = index.add(createMap("mykey", new byte[]{1, 2, 3, 4}));
        assertEquals(pointerA, pointerB);

        byte[] sameRef = new byte[]{1, 2, 3, 4};
        pointerA = index.add(createMap("mykey", sameRef));
        pointerB = index.add(createMap("mykey", sameRef));
        assertEquals(pointerA, pointerB);
    }

    @Test
    public void testUnknownValueClass() {
        KVStorage index = create();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> index.add(createMap("mykey", new Object())));
        assertTrue(ex.getMessage().contains("The Class of a value was Object, currently supported"), ex.getMessage());
    }

    @RepeatedTest(20)
    public void testRandom() {
        final long seed = new Random().nextLong();
        try {
            KVStorage index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE).create(), true).create(1000);
            Random random = new Random(seed);
            List<String> keys = createRandomStringList(random, "_key", 100);
            List<Integer> values = createRandomMap(random, 500);

            int size = 10000;
            LongArrayList pointers = new LongArrayList(size);
            for (int i = 0; i < size; i++) {
                Map<String, KValue> list = createRandomMap(random, keys, values);
                long pointer = index.add(list);
                try {
                    assertEquals(list.size(), index.getAll(pointer).size(), "" + i);
                } catch (Exception ex) {
                    throw new RuntimeException(i + " " + list + ", " + pointer, ex);
                }
                pointers.add(pointer);
            }

            for (int i = 0; i < size; i++) {
                Map<String, KValue> map = index.getAll(pointers.get(i));
                assertFalse(map.isEmpty(), i + " " + map);
                for (Map.Entry<String, KValue> entry : map.entrySet()) {
                    Object value = index.get(pointers.get(i), entry.getKey(), false);
                    assertEquals(entry.getValue().getFwd(), value, i + " " + map);
                }
            }
            index.flush();
            index.close();

            index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE).create(), true);
            assertTrue(index.loadExisting());
            for (int i = 0; i < size; i++) {
                Map<String, KValue> map = index.getAll(pointers.get(i));
                assertFalse(map.isEmpty(), i + " " + map);
                for (Map.Entry<String, KValue> entry : map.entrySet()) {
                    Object value = index.get(pointers.get(i), entry.getKey(), false);
                    assertEquals(entry.getValue().getFwd(), value, i + " " + map);
                }
            }
            index.close();
        } catch (Throwable t) {
            throw new RuntimeException("KVStorageTest.testRandom seed:" + seed, t);
        }
    }

    private List<Integer> createRandomMap(Random random, int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(size * 5));
        }
        return list;
    }

    private List<String> createRandomStringList(Random random, String postfix, int size) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(size * 5) + postfix);
        }
        return list;
    }

    private Map<String, KValue> createRandomMap(Random random, List<String> keys, List<Integer> values) {
        int count = random.nextInt(10) + 2;
        Set<String> avoidDuplicates = new HashSet<>(); // otherwise index.get returns potentially wrong value
        Map<String, KValue> list = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = keys.get(random.nextInt(keys.size()));
            if (!avoidDuplicates.add(key))
                continue;
            Object o = values.get(random.nextInt(values.size()));
            list.put(key, new KValue(key.endsWith("_s") ? o + "_s" : o));
        }
        return list;
    }

    // @RepeatedTest(1000)
    public void ignoreRandomString() {
        String s = "";
        long seed = new Random().nextLong();
        Random rand = new Random(seed);
        for (int i = 0; i < 255; i++) {
            s += (char) rand.nextInt();
        }

        s = cutString(s);
        assertTrue(s.getBytes(UTF_CS).length <= 255, s.getBytes(UTF_CS).length + " -> seed " + seed);
    }

    @Test
    public void testCutString() {
        String s = cutString("Бухарестская улица (http://ru.wikipedia.org/wiki/" +
                "%D0%91%D1%83%D1%85%D0%B0%D1%80%D0%B5%D1%81%D1%82%D1%81%D0%BA%D0%B0%D1%8F_%D1%83%D0%BB%D0%B8%D1%86%D0%B0_(%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3))");
        assertEquals(250, s.getBytes(UTF_CS).length);
    }

    @Test
    public void testMax() {
        long pointer = Integer.MAX_VALUE;
        int storedPointer = (int) (pointer + 100);
        assertTrue(storedPointer < 0);
        assertEquals(pointer + 100, Integer.toUnsignedLong(storedPointer));
    }

    @Test
    public void testGetByKeyIndex() {
        KVStorage index = create();
        long pointer = index.add(createMap("a", "a value", "b", "b value"));

        int keyIndexA = index.getKeyIndex("a");
        int keyIndexB = index.getKeyIndex("b");
        int keyIndexUnknown = index.getKeyIndex("unknown");

        assertTrue(keyIndexA >= 0);
        assertTrue(keyIndexB >= 0);
        assertEquals(-1, keyIndexUnknown);

        // int-based get should match string-based get
        assertEquals(index.get(pointer, "a", false), index.get(pointer, keyIndexA, false));
        assertEquals(index.get(pointer, "b", false), index.get(pointer, keyIndexB, false));
        assertNull(index.get(pointer, keyIndexUnknown, false));
    }

    @Test
    public void testGetByKeyIndexDirectional() {
        KVStorage index = create();
        Map<String, KValue> map = new LinkedHashMap<>();
        map.put("keyA", new KValue("FORWARD", null));
        map.put("keyB", new KValue(null, "BACKWARD"));
        map.put("keyC", new KValue("BOTH"));
        long pointer = index.add(map);

        int idxA = index.getKeyIndex("keyA");
        int idxB = index.getKeyIndex("keyB");
        int idxC = index.getKeyIndex("keyC");

        assertEquals("FORWARD", index.get(pointer, idxA, false));
        assertNull(index.get(pointer, idxA, true));
        assertNull(index.get(pointer, idxB, false));
        assertEquals("BACKWARD", index.get(pointer, idxB, true));
        assertEquals("BOTH", index.get(pointer, idxC, false));
        assertEquals("BOTH", index.get(pointer, idxC, true));
    }

    @Test
    public void testReserveKey() {
        KVStorage index = create();
        assertEquals(-1, index.getKeyIndex("cycleway"));

        index.reserveKey("cycleway", String.class);
        int keyIndex = index.getKeyIndex("cycleway");
        assertTrue(keyIndex >= 0);

        // now add data using the reserved key
        long pointer = index.add(createMap("cycleway", "lane"));
        assertEquals("lane", index.get(pointer, "cycleway", false));
        assertEquals("lane", index.get(pointer, keyIndex, false));
    }

    @Test
    public void testGetByKeyIndexAfterLoad() {
        Helper.removeDir(new File(location));

        KVStorage index = new KVStorage(new GHDirectory(location, DAType.RAM_STORE).create(), true).create(1000);
        index.reserveKey("cycleway", String.class);
        long pointer = index.add(createMap("cycleway", "lane"));
        assertEquals("lane", index.get(pointer, index.getKeyIndex("cycleway"), false));
        index.flush();
        index.close();

        KVStorage loaded = new KVStorage(new GHDirectory(location, DAType.RAM_STORE), true);
        assertTrue(loaded.loadExisting());
        // not possible
        assertThrows(IllegalArgumentException.class, () -> loaded.reserveKey("cycleway", String.class));
        int keyIndex = loaded.getKeyIndex("cycleway");
        assertEquals("lane", loaded.get(pointer, keyIndex, false));
        loaded.close();

        Helper.removeDir(new File(location));
    }
}
