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
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.util.Helper.*;

/**
 * Implements some common methods for the subclasses.
 *
 * @author Peter Karich
 */
public class GHDirectory implements Directory {
    protected final String location;
    private final DAType defaultType;
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    protected Map<String, DataAccess> map = new HashMap<>();

    public GHDirectory(String _location, DAType defaultType) {
        this.defaultType = defaultType;
        if (isEmpty(_location))
            _location = new File("").getAbsolutePath();

        if (!_location.endsWith("/"))
            _location += "/";

        location = _location;
        File dir = new File(location);
        if (dir.exists() && !dir.isDirectory())
            throw new RuntimeException("file '" + dir + "' exists but is not a directory");
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public DataAccess create(String name) {
        return create(name, defaultType);
    }

    @Override
    public DataAccess create(String name, int segmentSize) {
        return create(name, defaultType, segmentSize);
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
        return defaultType;
    }

    public boolean isStoring() {
        return defaultType.isStoring();
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
