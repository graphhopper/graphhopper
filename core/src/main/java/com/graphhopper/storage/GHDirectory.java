/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.util.Helper;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements some common methods for the subclasses.
 *
 * @author Peter Karich
 */
public class GHDirectory implements Directory {

    protected Map<String, DataAccess> map = new HashMap<String, DataAccess>();
    protected Map<String, DAType> types = new HashMap<String, DAType>();
    protected final String location;
    private DAType defaultType;

    public GHDirectory(String _location, DAType defaultType) {
        this.defaultType = defaultType;
        if (Helper.isEmpty(_location))
            _location = new File("").getAbsolutePath();
        if (!_location.endsWith("/"))
            _location += "/";
        location = _location;
        File dir = new File(location);
        if (dir.exists() && !dir.isDirectory())
            throw new RuntimeException("file '" + dir + "' exists but is not a directory");

        // set default access to integer based
        // improves performance on server side, 10% faster for queries, 20% faster for preparation
        if (!this.defaultType.equals(DAType.MMAP)) {
            if (isStoring()) {
                put("locationIndex", DAType.RAM_INT_STORE);
                put("edges", DAType.RAM_INT_STORE);
                put("nodes", DAType.RAM_INT_STORE);
            } else {
                put("locationIndex", DAType.RAM_INT);
                put("edges", DAType.RAM_INT);
                put("nodes", DAType.RAM_INT);
            }
        }
        mkdirs();
    }

    public Directory put(String name, DAType type) {
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
        DataAccess da = map.get(name);
        if (da != null)
            return da;

        switch (type) {
            case MMAP:
                da = new MMapDataAccess(name, location);
                break;
            case RAM:
                da = new RAMDataAccess(name, location, false);
                break;
            case RAM_STORE:
                da = new RAMDataAccess(name, location, true);
                break;
            case RAM_INT:
                da = new RAMIntDataAccess(name, location, false);
                break;
            case RAM_INT_STORE:
                da = new RAMIntDataAccess(name, location, true);
                break;
        }

        map.put(name, da);
        return da;
    }

    @Override
    public DataAccess rename(DataAccess da, String newName) {
        String oldName = da.name();
        da.rename(newName);
        removeByName(oldName);
        map.put(newName, da);
        return da;
    }

    @Override
    public void remove(DataAccess da) {
        removeByName(da.name());
    }

    void removeByName(String name) {
        if (map.remove(name) == null)
            throw new IllegalStateException("Couldn't remove dataAccess object:" + name);

        Helper.removeDir(new File(location + name));
    }

    public boolean isStoring() {
        return defaultType.equals(DAType.MMAP) || defaultType.equals(DAType.RAM_INT_STORE)
                || defaultType.equals(DAType.RAM_STORE);
    }

    protected void mkdirs() {
        if (isStoring())
            new File(location).mkdirs();
    }

    Collection<DataAccess> getAll() {
        return map.values();
    }

    @Override
    public String toString() {
        return location();
    }

    @Override
    public String location() {
        return location;
    }
}
