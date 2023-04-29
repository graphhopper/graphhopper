package com.graphhopper.search;

import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.search.KVStorage.KeyValue;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static com.graphhopper.search.KVStorage.KeyValue.createKV;
import static com.graphhopper.search.KVStorage.MAX_UNIQUE_KEYS;
import static com.graphhopper.search.KVStorage.cutString;
import static com.graphhopper.util.Helper.UTF_CS;
import static org.junit.jupiter.api.Assertions.*;

public class KVStorageTest {

    private final static String location = "./target/edge-kv-storage";

    private KVStorage create() {
        return new KVStorage(new RAMDirectory(), true).create(1000);
    }

    List<KeyValue> createList(Object... keyValues) {
        if (keyValues.length % 2 != 0)
            throw new IllegalArgumentException("Cannot create list from " + Arrays.toString(keyValues));
        List<KeyValue> map = new ArrayList<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.add(new KeyValue((String) keyValues[i], keyValues[i + 1]));
        }
        return map;
    }

    @Test
    public void putSame() {
        KVStorage index = create();
        long aPointer = index.add(createList("a", "same name", "b", "same name"));

        assertNull(index.get(aPointer, "", false));
        assertEquals("same name", index.get(aPointer, "a", false));
        assertEquals("same name", index.get(aPointer, "b", false));
        assertNull(index.get(aPointer, "c", false));

        index = create();
        aPointer = index.add(createList("a", "a name", "b", "same name"));
        assertEquals("a name", index.get(aPointer, "a", false));
    }

    @Test
    public void putAB() {
        KVStorage index = create();
        long aPointer = index.add(createList("a", "a name", "b", "b name"));

        assertNull(index.get(aPointer, "", false));
        assertEquals("a name", index.get(aPointer, "a", false));
        assertEquals("b name", index.get(aPointer, "b", false));
    }

    @Test
    public void getForwardBackward() {
        KVStorage index = create();
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("keyA", "FORWARD", true, false));
        list.add(new KeyValue("keyB", "BACKWARD", false, true));
        list.add(new KeyValue("keyC", "BOTH", true, true));
        long aPointer = index.add(list);

        assertNull(index.get(aPointer, "", false));
        List<KeyValue> deserializedList = index.getAll(aPointer);
        assertEquals(list, deserializedList);

        assertEquals("FORWARD", index.get(aPointer, "keyA", false));
        assertNull(index.get(aPointer, "keyA", true));

        assertNull(index.get(aPointer, "keyB", false));
        assertEquals("BACKWARD", index.get(aPointer, "keyB", true));

        assertEquals("BOTH", index.get(aPointer, "keyC", false));
        assertEquals("BOTH", index.get(aPointer, "keyC", true));
    }

    @Test
    public void putEmpty() {
        KVStorage index = create();
        assertEquals(1, index.add(createList("", "")));
        // cannot store null (in its first version we accepted null once it was clear which type the value has, but this is inconsequential)
        assertThrows(IllegalArgumentException.class, () -> assertEquals(5, index.add(createList("", null))));
        assertThrows(IllegalArgumentException.class, () -> index.add(createList("blup", null)));
        assertThrows(IllegalArgumentException.class, () -> index.add(createList(null, null)));

        assertNull(index.get(0, "", false));

        assertEquals(5, index.add(createList("else", "else")));
    }

    @Test
    public void putMany() {
        KVStorage index = create();
        long aPointer = 0, tmpPointer = 0;

        for (int i = 0; i < 10000; i++) {
            aPointer = index.add(createList("a", "a name " + i, "b", "b name " + i, "c", "c name " + i));
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
            index.add(createList("a" + i, "a name"));
        }
        try {
            index.add(createList("new", "a name"));
            fail();
        } catch (IllegalArgumentException ex) {
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
        long result = index.add(createList("", str));
        assertEquals(127, ((String) index.get(result, "", false)).length());
    }

    @Test
    public void testTooLongStringValueError() {
        KVStorage index = create();
        assertThrows(IllegalArgumentException.class, () -> index.add(createList("", "Бухарестская улица (http://ru.wikipedia.org/wiki" +
                "/%D0%91%D1%83%D1%85%D0%B0%D1%80%D0%B5%D1%81%D1%82%D1%81%D0%BA%D0%B0%D1%8F_%D1%83%D0%BB%D0%B8%D1%86%D0%B0_(%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3))")));

        String str = "sdfsdfds";
        for (int i = 0; i < 256 * 3; i++) {
            str += "Б";
        }
        final String finalStr = str;
        assertThrows(IllegalArgumentException.class, () -> index.add(createList("", finalStr)));
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
        long result = index.add(createKV("myval", bytes));
        bytes = (byte[]) index.get(result, "myval", false);
        assertArrayEquals(copy, bytes);

        final byte[] biggerByteArray = Arrays.copyOf(bytes, 256);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> index.add(createKV("myval2", biggerByteArray)));
        assertTrue(e.getMessage().contains("bytes.length cannot be > 255"));
    }

    @Test
    public void testIntLongDoubleFloat() {
        KVStorage index = create();
        long intres = index.add(createKV("intres", 4));
        long doubleres = index.add(createKV("doubleres", 4d));
        long floatres = index.add(createKV("floatres", 4f));
        long longres = index.add(createKV("longres", 4L));
        long after4Inserts = index.add(createKV("somenext", 0));

        // initial point is 1, then twice plus 1 + (2+4) and twice plus 1 + (2+8)
        assertEquals(1 + 36, after4Inserts);

        assertEquals(4f, index.get(floatres, "floatres", false));
        assertEquals(4L, index.get(longres, "longres", false));
        assertEquals(4d, index.get(doubleres, "doubleres", false));
        assertEquals(4, index.get(intres, "intres", false));
    }

    @Test
    public void testIntLongDoubleFloat2() {
        KVStorage index = create();
        List<KeyValue> list = new ArrayList<>();
        list.add(new KeyValue("int", 4));
        list.add(new KeyValue("long", 4L));
        list.add(new KeyValue("double", 4d));
        list.add(new KeyValue("float", 4f));
        long allInOne = index.add(list);

        long afterMapInsert = index.add(createKV("somenext", 0));

        // 1 + 1 + (2+4) + (2+8) + (2+8) + (2+4)
        assertEquals(1 + 1 + 32, afterMapInsert);

        List<KeyValue> resMap = index.getAll(allInOne);
        assertEquals(4, resMap.get(0).value);
        assertEquals(4L, resMap.get(1).value);
        assertEquals(4d, resMap.get(2).value);
        assertEquals(4f, resMap.get(3).value);
    }

    @Test
    public void testFlush() {
        Helper.removeDir(new File(location));

        KVStorage index = new KVStorage(new RAMDirectory(location, true).create(), true);
        long pointer = index.add(createList("", "test"));
        index.flush();
        index.close();

        index = new KVStorage(new RAMDirectory(location, true), true);
        assertTrue(index.loadExisting());
        assertEquals("test", index.get(pointer, "", false));
        // make sure bytePointer is correctly set after loadExisting
        long newPointer = index.add(createList("", "testing"));
        assertEquals(pointer + 1 + 3 + "test".getBytes().length, newPointer, newPointer + ">" + pointer);
        index.close();

        Helper.removeDir(new File(location));
    }

    @Test
    public void testLoadKeys() {
        Helper.removeDir(new File(location));

        KVStorage index = new KVStorage(new RAMDirectory(location, true).create(), true).create(1000);
        long pointerA = index.add(createList("c", "test value"));
        assertEquals(2, index.getKeys().size());
        long pointerB = index.add(createList("a", "value", "b", "another value"));
        // empty string is always the first key
        assertEquals("[, c, a, b]", index.getKeys().toString());
        index.flush();
        index.close();

        index = new KVStorage(new RAMDirectory(location, true), true);
        assertTrue(index.loadExisting());
        assertEquals("[, c, a, b]", index.getKeys().toString());
        assertEquals("test value", index.get(pointerA, "c", false));
        assertNull(index.get(pointerA, "b", false));

        assertNull(index.get(pointerB, "", false));
        assertEquals("value", index.get(pointerB, "a", false));
        assertEquals("another value", index.get(pointerB, "b", false));
        assertEquals("[a=value (true|true), b=another value (true|true)]", index.getAll(pointerB).toString());
        index.close();

        Helper.removeDir(new File(location));
    }

    @Test
    public void testEmptyKey() {
        KVStorage index = create();
        long pointerA = index.add(createList("", "test value"));
        long pointerB = index.add(createList("a", "value", "b", "another value"));

        assertEquals("test value", index.get(pointerA, "", false));
        assertNull(index.get(pointerA, "a", false));

        assertEquals("value", index.get(pointerB, "a", false));
        assertNull(index.get(pointerB, "", false));
    }

    @Test
    public void testSameByteArray() {
        KVStorage index = create();

        long pointerA = index.add(createList("mykey", new byte[]{1, 2, 3, 4}));
        long pointerB = index.add(createList("mykey", new byte[]{1, 2, 3, 4}));
        assertEquals(pointerA, pointerB);

        byte[] sameRef = new byte[]{1, 2, 3, 4};
        pointerA = index.add(createList("mykey", sameRef));
        pointerB = index.add(createList("mykey", sameRef));
        assertEquals(pointerA, pointerB);
    }

    @Test
    public void testUnknownValueClass() {
        KVStorage index = create();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> index.add(createList("mykey", new Object())));
        assertTrue(ex.getMessage().contains("The Class of a value was Object, currently supported"), ex.getMessage());
    }

    @RepeatedTest(20)
    public void testRandom() {
        final long seed = new Random().nextLong();
        try {
            KVStorage index = new KVStorage(new RAMDirectory(location, true).create(), true).create(1000);
            Random random = new Random(seed);
            List<String> keys = createRandomStringList(random, "_key", 100);
            List<Integer> values = createRandomList(random, 500);

            int size = 10000;
            LongArrayList pointers = new LongArrayList(size);
            for (int i = 0; i < size; i++) {
                List<KeyValue> list = createRandomList(random, keys, values);
                long pointer = index.add(list);
                try {
                    assertEquals(list.size(), index.getAll(pointer).size(), "" + i);
                } catch (Exception ex) {
                    throw new RuntimeException(i + " " + list + ", " + pointer, ex);
                }
                pointers.add(pointer);
            }

            for (int i = 0; i < size; i++) {
                List<KeyValue> list = index.getAll(pointers.get(i));
                assertTrue(list.size() > 0, i + " " + list);
                for (KeyValue entry : list) {
                    Object value = index.get(pointers.get(i), entry.key, false);
                    assertEquals(entry.value, value, i + " " + list);
                }
            }
            index.flush();
            index.close();

            index = new KVStorage(new RAMDirectory(location, true).create(), true);
            assertTrue(index.loadExisting());
            for (int i = 0; i < size; i++) {
                List<KeyValue> list = index.getAll(pointers.get(i));
                assertTrue(list.size() > 0, i + " " + list);
                for (KeyValue entry : list) {
                    Object value = index.get(pointers.get(i), entry.key, false);
                    assertEquals(entry.value, value, i + " " + list);
                }
            }
            index.close();
        } catch (Throwable t) {
            throw new RuntimeException("KVStorageTest.testRandom seed:" + seed, t);
        }
    }

    private List<Integer> createRandomList(Random random, int size) {
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

    private List<KeyValue> createRandomList(Random random, List<String> keys, List<Integer> values) {
        int count = random.nextInt(10) + 2;
        Set<String> avoidDuplicates = new HashSet<>(); // otherwise index.get returns potentially wrong value
        List<KeyValue> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = keys.get(random.nextInt(keys.size()));
            if (!avoidDuplicates.add(key))
                continue;
            Object o = values.get(random.nextInt(values.size()));
            list.add(new KeyValue(key, key.endsWith("_s") ? o + "_s" : o));
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
        assertEquals(pointer + 100, Helper.toUnsignedLong(storedPointer));
    }
}