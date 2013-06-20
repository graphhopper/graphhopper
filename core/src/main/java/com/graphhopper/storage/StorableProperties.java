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

import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes an in-memory HashMap into a file on flush.
 *
 * @author Peter Karich
 */
public class StorableProperties implements Storable<StorableProperties> {

    private Map<String, String> map = new LinkedHashMap<String, String>();
    private DataAccess da;

    public StorableProperties(Directory dir) {
        this.da = dir.find("properties");
    }

    @Override public boolean loadExisting() {
        if (!da.loadExisting())
            return false;

        int len = (int) da.capacity();
        byte[] bytes = new byte[len];
        da.getBytes(0, bytes, len);
        try {
            Helper.loadProperties(map, new StringReader(new String(bytes, "UTF-8")));
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override public void flush() {
        try {
            StringWriter sw = new StringWriter();
            Helper.saveProperties(map, sw);
            // TODO at the moment the size is limited to da.segmentSize() !
            byte[] bytes = sw.toString().getBytes("UTF-8");
            da.setBytes(0, bytes, bytes.length);
            da.flush();
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

    @Override public void close() {
        da.close();
    }

    @Override public StorableProperties create(long size) {
        da.create(size);
        return this;
    }

    @Override public long capacity() {
        return da.capacity();
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

    public void copyTo(StorableProperties properties) {
        properties.map.clear();
        properties.map.putAll(map);
        da.copyTo(properties.da);
    }

    @Override
    public String toString() {
        return da.toString();
    }
}
