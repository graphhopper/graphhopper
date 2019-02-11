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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.util.Helper.*;

/**
 * Implements some common methods for the subclasses.
 * <p>
 *
 * @author Peter Karich
 */
public class GHDirectory implements Directory {
    protected final String location;
    private final DAType defaultType;
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    protected Map<String, DataAccess> map = new HashMap<>();
    protected Map<String, DAType> types = new HashMap<>();

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

    public Directory put(String name, DAType type) {
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Since 0.7 DataAccess objects does no longer accept upper case names");

        types.put(name, type);
        return this;
    }

    @Override
    public DataAccess find(String name) {
        DAType type = types.get(name);
        if (type == null)
            type = defaultType;

        return find(name, type);
    }

    @Override
    public DataAccess find(String name, DAType type) {
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Since 0.7 DataAccess objects does no longer accept upper case names");

        DataAccess da = map.get(name);
        if (da != null) {
            if (!type.equals(da.getType()))
                throw new IllegalStateException("Found existing DataAccess object '" + name
                        + "' but types did not match. Requested:" + type + ", was:" + da.getType());
            return da;
        }

        if (type.isInMemory()) {
            if (type.isInteg()) {
                if (type.isStoring())
                    da = new RAMIntDataAccess(name, location, true, byteOrder);
                else
                    da = new RAMIntDataAccess(name, location, false, byteOrder);
            } else if (type.isStoring())
                da = new RAMDataAccess(name, location, true, byteOrder);
            else
                da = new RAMDataAccess(name, location, false, byteOrder);
        } else if (type.isMMap()) {
            da = new MMapDataAccess(name, location, byteOrder, type.isAllowWrites());
        } else {
            da = new UnsafeDataAccess(name, location, byteOrder);
        }

        map.put(name, da);
        return da;
    }

    @Override
    public void clear() {
        // If there is at least one MMap DA then do not apply the cleanHack 
        // for every single mmap DA as this is very slow if lots of DataAccess objects were collected 
        // => forceClean == false

        MMapDataAccess mmapDA = null;
        for (DataAccess da : map.values()) {
            if (da instanceof MMapDataAccess)
                mmapDA = (MMapDataAccess) da;

            removeDA(da, da.getName(), false);
        }
        if (mmapDA != null)
            cleanHack();
        map.clear();
    }

    @Override
    public void remove(DataAccess da) {
        removeFromMap(da.getName());
        removeDA(da, da.getName(), true);
    }

    void removeDA(DataAccess da, String name, boolean forceClean) {
        if (da instanceof MMapDataAccess)
            ((MMapDataAccess) da).close(forceClean);
        else
            da.close();

        if (da.getType().isStoring())
            removeDir(new File(location + name));
    }

    void removeFromMap(String name) {
        DataAccess da = map.remove(name);
        if (da == null)
            throw new IllegalStateException("Couldn't remove dataAccess object:" + name);
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
    public Collection<DataAccess> getAll() {
        return map.values();
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
