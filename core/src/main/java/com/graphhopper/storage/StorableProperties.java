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

import com.graphhopper.json.GHJson;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes an in-memory Map into a file on flush. Thread safe, see #743.
 *
 * @author Peter Karich
 */
public class StorableProperties implements Storable<StorableProperties> {
    private final Map<String, Object> map = new TreeMap<>();
    private final String file;
    private final GHJson json;

    public StorableProperties(Directory dir, GHJson json) {
        file = dir.getLocation() + "/properties.json";
        this.json = json;
    }

    @Override
    public synchronized boolean loadExisting() {
        try {
            map.clear();
            map.putAll((Map<String, Object>) json.fromJson(new InputStreamReader(new FileInputStream(file), Helper.UTF_CS), Map.class));
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    @Override
    public synchronized void flush() {
        try {
            json.toJson(map, new OutputStreamWriter(new FileOutputStream(file), Helper.UTF_CS));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public synchronized StorableProperties remove(String key) {
        map.remove(key);
        return this;
    }

    public synchronized StorableProperties putAll(Map<String, String> externMap) {
        map.putAll(externMap);
        return this;
    }

    public synchronized StorableProperties put(String key, String val) {
        map.put(key, val);
        return this;
    }

    /**
     * Before it saves this value it creates a string out of it.
     */
    public synchronized StorableProperties put(String key, Object val) {
        if (!key.equals(key.toLowerCase()))
            throw new IllegalArgumentException("Do not use upper case keys (" + key + ") for StorableProperties since 0.7");

        map.put(key, val.toString());
        return this;
    }

    public synchronized String get(String key) {
        if (!key.equals(key.toLowerCase()))
            throw new IllegalArgumentException("Do not use upper case keys (" + key + ") for StorableProperties since 0.7");

        String ret = (String) map.get(key);
        if (ret == null)
            return "";

        return ret;
    }

    @Override
    public synchronized void close() {
    }

    @Override
    public synchronized boolean isClosed() {
        return false;
    }

    @Override
    public synchronized StorableProperties create(long size) {
        return this;
    }

    @Override
    public synchronized long getCapacity() {
        // unknown
        return 1;
    }

    public synchronized void putCurrentVersions() {
        put("nodes.version", Constants.VERSION_NODE);
        put("edges.version", Constants.VERSION_EDGE);
        put("geometry.version", Constants.VERSION_GEOMETRY);
        put("location_index.version", Constants.VERSION_LOCATION_IDX);
        put("name_index.version", Constants.VERSION_NAME_IDX);
        put("shortcuts.version", Constants.VERSION_SHORTCUT);
    }

    public synchronized String versionsToString() {
        return get("nodes.version") + ","
                + get("edges.version") + ","
                + get("geometry.version") + ","
                + get("location_index.version") + ","
                + get("name_index.version");
    }

    public synchronized boolean checkVersions(boolean silent) {
        if (!check("nodes", Constants.VERSION_NODE, silent))
            return false;

        if (!check("edges", Constants.VERSION_EDGE, silent))
            return false;

        if (!check("geometry", Constants.VERSION_GEOMETRY, silent))
            return false;

        if (!check("location_index", Constants.VERSION_LOCATION_IDX, silent))
            return false;

        if (!check("name_index", Constants.VERSION_NAME_IDX, silent))
            return false;

        if (!check("shortcuts", Constants.VERSION_SHORTCUT, silent))
            return false;

        // The check for the encoder version is done in EncoderManager, as this class does not know about the
        // registered encoders and their version
        return true;
    }

    boolean check(String key, int vers, boolean silent) {
        String str = get(key + ".version");
        if (!str.equals(vers + "")) {
            if (silent)
                return false;

            throw new IllegalStateException("Version of " + key + " unsupported: " + str + ", expected:" + vers + ". "
                    + "Make sure you are using the same GraphHopper version for reading the files that was used for creating them. "
                    + "See https://discuss.graphhopper.com/t/722");
        }
        return true;
    }

    @Override
    public synchronized String toString() {
        return file;
    }
}
