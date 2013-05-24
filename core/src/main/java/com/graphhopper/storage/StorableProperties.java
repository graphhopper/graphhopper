/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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

import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TODO instead of a separate 'file' this class should be directly handled from
 * a Directory, but for this DataAccess needs to be extended to support storing
 * clear UTF-text from as well.
 *
 * @author Peter Karich
 */
public class StorableProperties implements Storable<StorableProperties> {

    private Map<String, String> map = new LinkedHashMap<String, String>();
    private Directory dir;
    private String name;

    public StorableProperties(Directory dir, String name) {
        this.dir = dir;
        this.name = name;
    }

    @Override public boolean loadExisting() {
        if (!dir.isLoadRequired())
            return true;
        String file = dir.location() + name;
        if (!new File(file).exists())
            return false;
        try {
            Helper.loadProperties(map, new FileReader(file));
            return true;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public StorableProperties put(String key, String val) {
        map.put(key, val);
        return this;
    }

    public StorableProperties put(String key, Object val) {
        map.put(key, val.toString());
        return this;
    }

    public String get(String key) {
        String ret = map.get(key);
        if (ret == null)
            return "";
        return ret;
    }

    @Override public void flush() {
        if (!dir.isLoadRequired())
            return;

        try {
            String file = dir.location() + name;
            Helper.saveProperties(map, new FileWriter(file));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override public void close() {
    }

    @Override public StorableProperties create(long size) {
        return this;
    }

    @Override public long capacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void putCurrentVersions() {
        put("nodes.version", Constants.VERSION_NODE);
        put("edges.version", Constants.VERSION_EDGE);
        put("geometry.version", Constants.VERSION_GEOMETRY);
        put("locationIndex.version", Constants.VERSION_LOCATION_IDX);
    }

    public String versionsToString() {
        return get("nodes.version") + ","
                + get("edges.version") + ","
                + get("geometry.version") + ","
                + get("locationIndex.version");
    }

    public boolean checkVersions(boolean silent) {
        if (!check("nodes", Constants.VERSION_NODE, silent))
            return false;
        if (!check("edges", Constants.VERSION_EDGE, silent))
            return false;
        if (!check("geometry", Constants.VERSION_GEOMETRY, silent))
            return false;
        if (!check("locationIndex", Constants.VERSION_LOCATION_IDX, silent))
            return false;
        return true;
    }

    boolean check(String key, int vers, boolean silent) {
        String str = get(key + ".version");
        if (!str.equals(vers + "")) {
            if (silent)
                return false;
            throw new IllegalStateException("Version of " + key + " unsupported: " + str + ", expected:" + vers);
        }
        return true;
    }
}
