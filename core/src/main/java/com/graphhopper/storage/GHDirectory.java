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
import java.util.*;

import static com.graphhopper.storage.DAType.RAM_INT;
import static com.graphhopper.storage.DAType.RAM_INT_STORE;
import static com.graphhopper.util.Helper.*;

/**
 * Implements some common methods for the subclasses.
 *
 * @author Peter Karich
 */
public class GHDirectory implements Directory {
    protected final String location;
    private final DAType typeFallback;
    // first rule matches => LinkedHashMap
    private final Map<String, DAType> defaultTypes = new LinkedHashMap<>();
    private final Map<String, Integer> mmapPreloads = new LinkedHashMap<>();
    private final Map<String, DataAccess> map = Collections.synchronizedMap(new HashMap<>());

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
     * can prepend "preload." to the name and specify a percentage which preloads the DataAccess into physical memory of
     * the specified percentage (only applied for load, not for import).
     * As keys can be patterns the order is important and the LinkedHashMap is forced as type.
     */
    public void configure(LinkedHashMap<String, String> config) {
        for (Map.Entry<String, String> kv : config.entrySet()) {
            String value = kv.getValue().trim();
            if (kv.getKey().startsWith("preload."))
                try {
                    String pattern = kv.getKey().substring("preload.".length());
                    mmapPreloads.put(pattern, Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("DataAccess " + kv.getKey() + " has an incorrect preload value: " + value);
                }
            else {
                String pattern = kv.getKey();
                defaultTypes.put(pattern, DAType.fromString(value));
            }
        }
    }

    /**
     * Returns the preload value or 0 if no patterns match.
     * See {@link #configure(LinkedHashMap)}
     */
    int getPreload(String name) {
        for (Map.Entry<String, Integer> entry : mmapPreloads.entrySet())
            if (name.matches(entry.getKey())) return entry.getValue();
        return 0;
    }

    public void loadMMap() {
        for (DataAccess da : map.values()) {
            if (!(da instanceof MMapDataAccess))
                continue;
            int preload = getPreload(da.getName());
            if (preload > 0)
                ((MMapDataAccess) da).load(preload);
        }
    }

    @Override
    public DataAccess create(String name) {
        return create(name, getDefault(name, typeFallback));
    }

    @Override
    public DataAccess create(String name, int segmentSize) {
        return create(name, getDefault(name, typeFallback), segmentSize);
    }

    private DAType getDefault(String name, DAType typeFallback) {
        for (Map.Entry<String, DAType> entry : defaultTypes.entrySet())
            if (name.matches(entry.getKey())) return entry.getValue();
        return typeFallback;
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
                    da = new RAMIntDataAccess(name, location, true, segmentSize);
                else
                    da = new RAMIntDataAccess(name, location, false, segmentSize);
            } else if (type.isStoring())
                da = new RAMDataAccess(name, location, true, segmentSize);
            else
                da = new RAMDataAccess(name, location, false, segmentSize);
        } else if (type.isMMap()) {
            da = new MMapDataAccess(name, location, type.isAllowWrites(), segmentSize);
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
    public void remove(String name) {
        DataAccess old = map.remove(name);
        if (old == null)
            throw new IllegalStateException("Couldn't remove DataAccess: " + name);

        old.close();
        removeBackingFile(old, name);
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
        DAType type = getDefault(dataAccess, typeFallback);
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

    @Override
    public Map<String, DataAccess> getDAs() {
        return map;
    }
}
