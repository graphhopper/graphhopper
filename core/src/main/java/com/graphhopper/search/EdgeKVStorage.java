/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.search;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Constants;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class stores key-value pairs in an append-only manner.
 *
 * @author Peter Karich
 */
public class EdgeKVStorage {

    private static final long EMPTY_POINTER = 0, START_POINTER = 1;
    // Store the key index in 2 bytes. Use negative values for marking the value as duplicate.
    static final int MAX_UNIQUE_KEYS = (1 << 15);
    // Store string value as byte array and store the length into 1 byte
    private static final int MAX_LENGTH = (1 << 8) - 1;

    // It stores the mapping of "key to index" in the keys DataAccess. E.g. if your first key is "some" then we will
    // store the mapping "1->some" there (the 0th index is skipped on purpose). As this map is 'small' the keys
    // DataAccess is only used for long term storage, i.e. only in loadExisting and flush. For add and getAll we use
    // keyToIndex, indexToClass and indexToClass.
    private final DataAccess keys;

    // The storage layout in the vals DataAccess for one Map of key-value pairs. For example the map:
    // map = new HashMap(); map.put("some", "value"); map.put("some2", "value2"); is added via the method add, then we store:
    // 2 (the size of the Map, 1 byte)
    // --- now the first key-value pair:
    // 1 (the keys index for "some", 2 byte)
    // 4 (the length of the bytes from "some")
    // "some" (the bytes from "some")
    // --- second key-value pair:
    // 2 (the keys index for "some2")
    // 5 (the length of the bytes from "some2")
    // "some2" (the bytes from "some2")

    // So more generic: the values could be of dynamic length, fixed length like int or be duplicates:
    // vals count      (1 byte)
    // --- 1. key-value pair (store String or byte[] with dynamic length)
    // key_idx_0       (2 byte)
    // val_length_0    (1 byte)
    // val_0 (x bytes)
    // --- 2. key-value pair (store int with fixed length)
    // key_idx_1       (2 byte)
    // int             (4 byte)
    // --- 3. key-value pair (store duplicate, then key is negative and the value is a pointer to the actual value (relative to the current pointer)
    // -key_idx_2      (2 byte)
    // delta_pointer_2 (4 byte)
    //
    // Note:
    // 1. The key strings are limited to 32767 unique values (see MAX_UNIQUE_KEYS). A dynamic value has a maximum byte length of 255.
    // 2. Every key can store values only of the same type
    // 3. We need to loop through X entries to get the start val_x.
    // 4. We detect duplicate values for byte[] and String values via smallCache and then use the negative key index as
    //    'duplicate' marker. We then store only the delta (signed int) instead of the absolute unsigned long value to
    //    reduce memory usage when there are duplicate values.
    private final DataAccess vals;
    private final Map<String, Integer> keyToIndex = new HashMap<>();
    private final List<Class<?>> indexToClass = new ArrayList<>();
    private final List<String> indexToKey = new ArrayList<>();
    private final Map<Object, Long> smallCache;
    private final BitUtil bitUtil = BitUtil.LITTLE;
    private long bytePointer = START_POINTER;
    private long lastEntryPointer = -1;
    private Map<String, Object> lastEntryMap;

    public EdgeKVStorage(Directory dir) {
        this(dir, 1000);
    }

    /**
     * Specify a larger cacheSize to reduce disk usage. Note that this increases the memory usage of this object.
     */
    public EdgeKVStorage(Directory dir, final int cacheSize) {
        keys = dir.create("edgekv_keys", 10 * 1024);
        vals = dir.create("edgekv_vals");

        smallCache = new LinkedHashMap<Object, Long>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Long> entry) {
                return size() > cacheSize;
            }
        };
    }

    public EdgeKVStorage create(long initBytes) {
        keys.create(initBytes);
        vals.create(initBytes);
        // add special empty case to have a reliable duplicate detection via negative keyIndex
        keyToIndex.put("", 0);
        indexToKey.add("");
        indexToClass.add(String.class);
        return this;
    }

    public boolean loadExisting() {
        if (vals.loadExisting()) {
            if (!keys.loadExisting()) throw new IllegalStateException("Loaded values but cannot load keys");
            bytePointer = bitUtil.combineIntsToLong(vals.getHeader(0), vals.getHeader(4));
            GHUtility.checkDAVersion(vals.getName(), Constants.VERSION_EDGEKV_STORAGE, vals.getHeader(8));
            GHUtility.checkDAVersion(keys.getName(), Constants.VERSION_EDGEKV_STORAGE, keys.getHeader(0));

            // load keys into memory
            int count = keys.getShort(0);
            long keyBytePointer = 2;
            for (int i = 0; i < count; i++) {
                int keyLength = keys.getShort(keyBytePointer);
                keyBytePointer += 2;
                byte[] keyBytes = new byte[keyLength];
                keys.getBytes(keyBytePointer, keyBytes, keyLength);
                String valueStr = new String(keyBytes, Helper.UTF_CS);
                keyBytePointer += keyLength;

                keyToIndex.put(valueStr, keyToIndex.size());
                indexToKey.add(valueStr);

                int shortClassNameLength = 1;
                byte[] classBytes = new byte[shortClassNameLength];
                keys.getBytes(keyBytePointer, classBytes, shortClassNameLength);
                keyBytePointer += shortClassNameLength;
                indexToClass.add(shortNameToClass(new String(classBytes, Helper.UTF_CS)));
            }
            return true;
        }

        return false;
    }

    Collection<String> getKeys() {
        return indexToKey;
    }

    /**
     * This method writes the specified entryMap (key-value pairs) into the storage. Please note that null keys or null
     * values are rejected. The Class of a value can be only: byte[], String, int, long, float or double
     * (or more precisely, their wrapper equivalent). For all other types an exception is thrown. The first call of add
     * assigns a Class to every key in the Map and future calls of add will throw an exception if this Class differs.
     *
     * @return entryPointer with which you can later fetch the entryMap via the get or getAll method
     */
    public long add(final Map<String, Object> entryMap) {
        if (entryMap == null) throw new IllegalArgumentException("specified Map must not be null");
        if (entryMap.isEmpty()) return EMPTY_POINTER;
        else if (entryMap.size() > 200)
            throw new IllegalArgumentException("Cannot store more than 200 entries per entry");

        // This is a very important "compression" mechanism because one OSM way is split into multiple edges and so we
        // can often re-use the serialized key-value pairs of the previous edge.
        if (isEquals(entryMap, lastEntryMap)) return lastEntryPointer;

        // If the Class of a value is unknown it should already fail here, before we modify internal data. (see #2597#discussion_r896469840)
        for (Map.Entry<String, Object> entry : entryMap.entrySet())
            if (keyToIndex.get(entry.getKey()) != null)
                getBytesForValue(indexToClass.get(keyToIndex.get(entry.getKey())), entry.getValue());

        lastEntryMap = entryMap;
        lastEntryPointer = bytePointer;
        // while adding there could be exceptions and we need to avoid that the bytePointer is modified
        long currentPointer = bytePointer;

        vals.ensureCapacity(currentPointer + 1);
        vals.setByte(currentPointer, (byte) entryMap.size());
        currentPointer += 1;
        for (Map.Entry<String, Object> entry : entryMap.entrySet()) {
            String key = entry.getKey();
            if (key == null) throw new IllegalArgumentException("key cannot be null");
            Object value = entry.getValue();
            if (value == null) throw new IllegalArgumentException("value for key " + key + " cannot be null");
            Integer keyIndex = keyToIndex.get(key);
            Class<?> clazz;
            if (keyIndex == null) {
                keyIndex = keyToIndex.size();
                if (keyIndex >= MAX_UNIQUE_KEYS)
                    throw new IllegalArgumentException("Cannot store more than " + MAX_UNIQUE_KEYS + " unique keys");
                keyToIndex.put(key, keyIndex);
                indexToKey.add(key);
                indexToClass.add(clazz = value.getClass());
            } else {
                clazz = indexToClass.get(keyIndex);
                if (clazz != value.getClass())
                    throw new IllegalArgumentException("Class of value for key " + key + " must be " + clazz.getSimpleName() + " but was " + value.getClass().getSimpleName());
            }

            boolean hasDynLength = hasDynLength(clazz);
            if (hasDynLength) {
                // optimization for empty string or empty byte array
                if (clazz.equals(String.class) && ((String) value).isEmpty()
                        || clazz.equals(byte[].class) && ((byte[]) value).length == 0) {
                    vals.ensureCapacity(currentPointer + 3);
                    vals.setShort(currentPointer, keyIndex.shortValue());
                    // ensure that also in case of MMap value is set to 0
                    vals.setByte(currentPointer + 2, (byte) 0);
                    currentPointer += 3;
                    continue;
                }

                Long existingRef = smallCache.get(value);
                if (existingRef != null) {
                    long delta = lastEntryPointer - existingRef;
                    if (delta < Integer.MAX_VALUE && delta > Integer.MIN_VALUE) {
                        vals.ensureCapacity(currentPointer + 2 + 4);
                        vals.setShort(currentPointer, (short) -keyIndex);
                        currentPointer += 2;
                        // do not store valueBytes.length as we know it already: it is 4!
                        byte[] valueBytes = new byte[4];
                        bitUtil.fromInt(valueBytes, (int) delta);
                        vals.setBytes(currentPointer, valueBytes, valueBytes.length);
                        currentPointer += valueBytes.length;
                        continue;
                    } else {
                        smallCache.remove(value);
                    }
                }
            }

            final byte[] valueBytes = getBytesForValue(clazz, value);
            // only cache value if storing via duplicate marker is valuable (the delta costs 4 bytes minus 1 due to omitted valueBytes.length storage)
            if (hasDynLength && valueBytes.length > 3) smallCache.put(value, currentPointer);
            vals.ensureCapacity(currentPointer + 2 + 1 + valueBytes.length);
            vals.setShort(currentPointer, keyIndex.shortValue());
            currentPointer += 2;
            if (hasDynLength) {
                vals.setByte(currentPointer, (byte) valueBytes.length);
                currentPointer++;
            }
            vals.setBytes(currentPointer, valueBytes, valueBytes.length);
            currentPointer += valueBytes.length;
        }
        bytePointer = currentPointer;
        if (bytePointer < 0)
            throw new IllegalStateException("Negative bytePointer in EdgeKVStorage");
        return lastEntryPointer;
    }

    private boolean isEquals(Map<String, Object> entryMap, Map<String, Object> lastEntryMap) {
        if (lastEntryMap != null && entryMap.size() == lastEntryMap.size()) {
            for (Map.Entry<String, Object> entry : entryMap.entrySet()) {
                Object val = entry.getValue();
                if (val == null)
                    throw new IllegalArgumentException("value for key " + entry.getKey() + " cannot be null");
                Object lastVal = lastEntryMap.get(entry.getKey());
                if (val instanceof byte[] && lastVal instanceof byte[] && Arrays.equals((byte[]) lastVal, (byte[]) val)
                        || val.equals(lastVal)) continue;
                return false;
            }
            return true;
        }
        return false;
    }

    public Map<String, Object> getAll(final long entryPointer) {
        if (entryPointer < 0)
            throw new IllegalStateException("Pointer to access EdgeKVStorage cannot be negative:" + entryPointer);

        if (entryPointer == EMPTY_POINTER) return Collections.emptyMap();

        int keyCount = vals.getByte(entryPointer) & 0xFF;
        if (keyCount == 0) return Collections.emptyMap();

        Map<String, Object> map = new HashMap<>(keyCount);
        long tmpPointer = entryPointer + 1;
        AtomicInteger sizeOfObject = new AtomicInteger();
        for (int i = 0; i < keyCount; i++) {
            int currentKeyIndex = vals.getShort(tmpPointer);
            tmpPointer += 2;

            Object object;
            if (currentKeyIndex < 0) {
                // deserialize duplicate
                currentKeyIndex = -currentKeyIndex;
                byte[] valueBytes = new byte[4];
                vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
                tmpPointer += 4;

                long dupPointer = entryPointer - bitUtil.toInt(valueBytes);
                dupPointer += 2;
                if (dupPointer > bytePointer)
                    throw new IllegalStateException("dup marker should exist but points into not yet allocated area " + dupPointer + " > " + bytePointer);
                object = deserializeObj(null, dupPointer, indexToClass.get(currentKeyIndex));
            } else {
                object = deserializeObj(sizeOfObject, tmpPointer, indexToClass.get(currentKeyIndex));
                tmpPointer += sizeOfObject.get();
            }
            String key = indexToKey.get(currentKeyIndex);
            map.put(key, object);
        }

        return map;
    }

    private boolean hasDynLength(Class<?> clazz) {
        return clazz.equals(String.class) || clazz.equals(byte[].class);
    }

    private int getFixLength(Class<?> clazz) {
        if (clazz.equals(Integer.class) || clazz.equals(Float.class)) return 4;
        else if (clazz.equals(Long.class) || clazz.equals(Double.class)) return 8;
        else throw new IllegalArgumentException("unknown class " + clazz);
    }

    private byte[] getBytesForValue(Class<?> clazz, Object value) {
        byte[] bytes;
        if (clazz.equals(String.class)) {
            bytes = ((String) value).getBytes(Helper.UTF_CS);
        } else if (clazz.equals(Integer.class)) {
            return bitUtil.fromInt((int) value);
        } else if (clazz.equals(Long.class)) {
            return bitUtil.fromLong((long) value);
        } else if (clazz.equals(Float.class)) {
            return bitUtil.fromFloat((float) value);
        } else if (clazz.equals(Double.class)) {
            return bitUtil.fromDouble((double) value);
        } else if (clazz.equals(byte[].class)) {
            bytes = (byte[]) value;
        } else
            throw new IllegalArgumentException("The Class of a value was " + clazz.getSimpleName() + ", currently supported: byte[], String, int, long, float and double");
        if (bytes.length > MAX_LENGTH)
            throw new IllegalArgumentException("bytes.length cannot be > " + MAX_LENGTH + " but was " + bytes.length);
        return bytes;
    }

    private String classToShortName(Class<?> clazz) {
        if (clazz.equals(String.class)) return "S";
        else if (clazz.equals(Integer.class)) return "i";
        else if (clazz.equals(Long.class)) return "l";
        else if (clazz.equals(Float.class)) return "f";
        else if (clazz.equals(Double.class)) return "d";
        else if (clazz.equals(byte[].class)) return "[";
        else throw new IllegalArgumentException("Cannot find short name. Unknown class " + clazz);
    }

    private Class<?> shortNameToClass(String name) {
        if (name.equals("S")) return String.class;
        else if (name.equals("i")) return Integer.class;
        else if (name.equals("l")) return Long.class;
        else if (name.equals("f")) return Float.class;
        else if (name.equals("d")) return Double.class;
        else if (name.equals("[")) return byte[].class;
        else throw new IllegalArgumentException("Cannot find class. Unknown short name " + name);
    }

    /**
     * This method creates an Object (type Class) which is located at the specified pointer
     */
    private Object deserializeObj(AtomicInteger sizeOfObject, long pointer, Class<?> clazz) {
        if (hasDynLength(clazz)) {
            int valueLength = vals.getByte(pointer) & 0xFF;
            pointer++;
            byte[] valueBytes = new byte[valueLength];
            vals.getBytes(pointer, valueBytes, valueBytes.length);
            if (sizeOfObject != null)
                sizeOfObject.set(1 + valueLength); // For String and byte[] we store the length and the value
            if (clazz.equals(String.class)) return new String(valueBytes, Helper.UTF_CS);
            else if (clazz.equals(byte[].class)) return valueBytes;
            throw new IllegalArgumentException();
        } else {
            byte[] valueBytes = new byte[getFixLength(clazz)];
            vals.getBytes(pointer, valueBytes, valueBytes.length);
            if (clazz.equals(Integer.class)) {
                if (sizeOfObject != null) sizeOfObject.set(4);
                return bitUtil.toInt(valueBytes, 0);
            } else if (clazz.equals(Long.class)) {
                if (sizeOfObject != null) sizeOfObject.set(8);
                return bitUtil.toLong(valueBytes, 0);
            } else if (clazz.equals(Float.class)) {
                if (sizeOfObject != null) sizeOfObject.set(4);
                return bitUtil.toFloat(valueBytes, 0);
            } else if (clazz.equals(Double.class)) {
                if (sizeOfObject != null) sizeOfObject.set(8);
                return bitUtil.toDouble(valueBytes, 0);
            } else {
                throw new IllegalArgumentException("unknown class " + clazz);
            }
        }
    }

    public Object get(final long entryPointer, String key) {
        if (entryPointer < 0)
            throw new IllegalStateException("Pointer to access EdgeKVStorage cannot be negative:" + entryPointer);

        if (entryPointer == EMPTY_POINTER) return null;

        Integer keyIndex = keyToIndex.get(key);
        if (keyIndex == null) return null; // key wasn't stored before

        int keyCount = vals.getByte(entryPointer) & 0xFF;
        if (keyCount == 0) return null; // no entries

        long tmpPointer = entryPointer + 1;
        for (int i = 0; i < keyCount; i++) {
            int currentKeyIndexRaw = vals.getShort(tmpPointer);
            int keyIndexPositive = Math.abs(currentKeyIndexRaw);
            assert keyIndexPositive < indexToKey.size() : "invalid key index " + keyIndexPositive + ">=" + indexToKey.size() + ", entryPointer=" + entryPointer + ", max=" + bytePointer;
            tmpPointer += 2;
            if (keyIndexPositive == keyIndex) {
                if (currentKeyIndexRaw < 0) {
                    byte[] valueBytes = new byte[4];
                    vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
                    tmpPointer = entryPointer - bitUtil.toInt(valueBytes);
                    tmpPointer += 2;
                    if (tmpPointer > bytePointer)
                        throw new IllegalStateException("dup marker " + bytePointer + " should exist but points into not yet allocated area " + tmpPointer);
                }

                return deserializeObj(null, tmpPointer, indexToClass.get(keyIndex));
            }

            // skip to next entry of same edge either via skipping the pointer (raw<0) or the real value
            if (currentKeyIndexRaw < 0) {
                tmpPointer += 4;
            } else {
                Class<?> clazz = indexToClass.get(keyIndexPositive);
                int valueLength = hasDynLength(clazz) ? 1 + vals.getByte(tmpPointer) & 0xFF : getFixLength(clazz);
                tmpPointer += valueLength;
            }
        }

        // value for specified key does not exist for the specified pointer
        return null;
    }

    public void flush() {
        keys.ensureCapacity(2);
        keys.setShort(0, (short) keyToIndex.size());
        long keyBytePointer = 2;
        for (int i = 0; i < indexToKey.size(); i++) {
            String key = indexToKey.get(i);
            byte[] keyBytes = getBytesForValue(String.class, key);
            keys.ensureCapacity(keyBytePointer + 2 + keyBytes.length);
            keys.setShort(keyBytePointer, (short) keyBytes.length);
            keyBytePointer += 2;

            keys.setBytes(keyBytePointer, keyBytes, keyBytes.length);
            keyBytePointer += keyBytes.length;

            Class<?> clazz = indexToClass.get(i);
            byte[] clazzBytes = getBytesForValue(String.class, classToShortName(clazz));
            if (clazzBytes.length != 1)
                throw new IllegalArgumentException("class name byte length must be 1 but was " + clazzBytes.length);
            keys.ensureCapacity(keyBytePointer + 1);
            keys.setBytes(keyBytePointer, clazzBytes, 1);
            keyBytePointer += 1;
        }
        keys.setHeader(0, Constants.VERSION_EDGEKV_STORAGE);
        keys.flush();

        vals.setHeader(0, bitUtil.getIntLow(bytePointer));
        vals.setHeader(4, bitUtil.getIntHigh(bytePointer));
        vals.setHeader(8, Constants.VERSION_EDGEKV_STORAGE);
        vals.flush();
    }

    public void close() {
        keys.close();
        vals.close();
    }

    public boolean isClosed() {
        return vals.isClosed() && keys.isClosed();
    }

    public long getCapacity() {
        return vals.getCapacity() + keys.getCapacity();
    }
}
