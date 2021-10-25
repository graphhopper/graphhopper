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
package com.graphhopper.storage;

import java.io.File;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.storage.DAType.RAM_INT;
import static com.graphhopper.storage.DAType.RAM_INT_STORE;
import static com.graphhopper.util.Helper.*;

/**
 * Implements some common methods for the subclasses.
 *
 * @author Peter Karich
 */
public class GHDirectory implements Directory {
    private final String location;
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private final Map<String, DataAccess> map = new HashMap<>();
    private final Map<String, DAType> defaultTypes = new HashMap<>();
    private final DAType typeFallback;
    private final Map<String, Integer> mmapPreloads = new HashMap<>();

    public GHDirectory(String _location, DAType defaultType) {
        this.typeFallback = defaultType;
        if (isEmpty(_location))
            _location = new File("").getAbsolutePath();

        if (!_location.endsWith("/"))
            _location += "/";

        location = _location;
        File dir = new File(location);
        if (dir.exists() && !dir.isDirectory())
            throw new RuntimeException("file '" + dir + "' exists but is not a directory");
    }

    /**
     * Configure the DAType (specified by the value) of a single DataAccess object (specified by the key). For "MMAP" you
     * can append a percentage like ";<int>" e.g. "MMAP;20". This preloads the DataAccess into physical memory of the
     * specified percentage.
     */
    public void configure(Map<String, String> config) {
        for (Entry entry : convert(config)) {
            defaultTypes.put(entry.name, entry.type);
            mmapPreloads.put(entry.name, entry.preload);
        }
    }

    static List<Entry> convert(Map<String, String> configs) {
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, String> kv : configs.entrySet()) {
            Entry e = new Entry();
            e.name = kv.getKey();
            String[] values = kv.getValue().split(";");
            if (values.length == 0)
                throw new IllegalArgumentException("DataAccess " + kv.getKey() + " has an empty value");
            e.type = DAType.fromString(values[0].trim());
            if (values.length > 1)
                try {
                    e.preload = Integer.parseInt(values[1]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("DataAccess " + kv.getKey() + " has an incorrect preload value. Use e.g. MMAP;20");
                }

            result.add(e);
        }
        return result;
    }

    public void loadMMap() {
        for (Map.Entry<String, Integer> entry : mmapPreloads.entrySet()) {
            DataAccess da = map.get(entry.getKey());
            if (da == null)
                throw new IllegalArgumentException("Cannot preload DataAccess " + entry.getKey() + ". Does not exist. Existing: " + map.keySet());
            if (!(da instanceof MMapDataAccess))
                throw new IllegalArgumentException("Can only preload MMapDataAccess " + da.getName() + ". But was " + da.getType());
            ((MMapDataAccess) da).load(entry.getValue());
        }
    }

    public static class Entry {
        String name;
        DAType type;
        int preload;
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public DataAccess create(String name) {
        return create(name, defaultTypes.getOrDefault(name, typeFallback));
    }

    @Override
    public DataAccess create(String name, int segmentSize) {
        return create(name, defaultTypes.getOrDefault(name, typeFallback), segmentSize);
    }

    @Override
    public DataAccess create(String name, DAType type) {
        return create(name, type, -1);
    }

    @Override
    public DataAccess create(String name, DAType type, int segmentSize) {
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Since 0.7 DataAccess objects does no longer accept upper case names");

        if (map.containsKey(name))
            // we do not allow creating two DataAccess with the same name, because on disk there can only be one DA
            // per file name
            throw new IllegalStateException("DataAccess " + name + " has already been created");

        DataAccess da;
        if (type.isInMemory()) {
            if (type.isInteg()) {
                if (type.isStoring())
                    da = new RAMIntDataAccess(name, location, true, byteOrder, segmentSize);
                else
                    da = new RAMIntDataAccess(name, location, false, byteOrder, segmentSize);
            } else if (type.isStoring())
                da = new RAMDataAccess(name, location, true, byteOrder, segmentSize);
            else
                da = new RAMDataAccess(name, location, false, byteOrder, segmentSize);
        } else if (type.isMMap()) {
            da = new MMapDataAccess(name, location, byteOrder, type.isAllowWrites(), segmentSize);
        } else {
            throw new IllegalArgumentException("DAType not supported " + type);
        }

        map.put(name, da);
        return da;
    }

    @Override
    public void close() {
        for (DataAccess da : map.values()) {
            da.close();
        }
        map.clear();
    }

    @Override
    public void clear() {
        for (DataAccess da : map.values()) {
            da.close();
            removeBackingFile(da, da.getName());
        }
        map.clear();
    }

    @Override
    public void remove(DataAccess da) {
        DataAccess old = map.remove(da.getName());
        if (old == null)
            throw new IllegalStateException("Couldn't remove DataAccess: " + da.getName());

        da.close();
        removeBackingFile(da, da.getName());
    }

    private void removeBackingFile(DataAccess da, String name) {
        if (da.getType().isStoring())
            removeDir(new File(location + name));
    }

    @Override
    public DAType getDefaultType() {
        return typeFallback;
    }

    /**
     * This method returns the default DAType of the specified DataAccess (as string). If preferInts is true then this
     * method returns e.g. RAM_INT if the type of the specified DataAccess is RAM.
     */
    public DAType getDefaultType(String dataAccess, boolean preferInts) {
        DAType type = defaultTypes.getOrDefault(dataAccess, typeFallback);
        if (preferInts && type.isInMemory())
            return type.isStoring() ? RAM_INT_STORE : RAM_INT;
        return type;
    }

    public boolean isStoring() {
        return typeFallback.isStoring();
    }

    @Override
    public Directory create() {
        if (isStoring())
            new File(location).mkdirs();
        return this;
    }

    @Override
    public String toString() {
        return getLocation();
    }

    @Override
    public String getLocation() {
        return location;
    }
}
