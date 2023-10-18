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

import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.graphhopper.util.Helper.*;

/**
 * Writes an in-memory HashMap into a file on flush. Thread safe, see #743.
 *
 * @author Peter Karich
 */
public class StorableProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorableProperties.class);

    private final Map<String, String> map = new LinkedHashMap<>();
    private final DataAccess da;

    public StorableProperties(Directory dir) {
        // reduce size
        int segmentSize = 1 << 15;
        this.da = dir.create("properties", segmentSize);
    }

    public synchronized boolean loadExisting() {
        if (!da.loadExisting())
            return false;

        int len = (int) da.getCapacity();
        byte[] bytes = new byte[len];
        da.getBytes(0, bytes, len);
        try {
            loadProperties(map, new StringReader(new String(bytes, UTF_CS)));
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public synchronized void flush() {
        try {
            StringWriter sw = new StringWriter();
            saveProperties(map, sw);
            // TODO at the moment the size is limited to da.segmentSize() !
            byte[] bytes = sw.toString().getBytes(UTF_CS);
            da.setBytes(0, bytes, bytes.length);
            da.flush();
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
        if (!key.equals(toLowerCase(key)))
            throw new IllegalArgumentException("Do not use upper case keys (" + key + ") for StorableProperties since 0.7");

        map.put(key, val.toString());
        return this;
    }

    public synchronized String get(String key) {
        if (!key.equals(toLowerCase(key)))
            throw new IllegalArgumentException("Do not use upper case keys (" + key + ") for StorableProperties since 0.7");
        return map.getOrDefault(key, "");
    }

    public synchronized Map<String, String> getAll() {
        return map;
    }

    public synchronized void close() {
        da.close();
    }

    public synchronized boolean isClosed() {
        return da.isClosed();
    }

    public synchronized StorableProperties create(long size) {
        da.create(size);
        return this;
    }

    public synchronized long getCapacity() {
        return da.getCapacity();
    }

    public synchronized boolean containsVersion() {
        return map.containsKey("nodes.version") ||
                map.containsKey("edges.version") ||
                map.containsKey("geometry.version") ||
                map.containsKey("location_index.version") ||
                map.containsKey("string_index.version");
    }

    @Override
    public synchronized String toString() {
        return da.toString();
    }

    static void loadProperties(Map<String, String> map, Reader tmpReader) throws IOException {
        BufferedReader reader = new BufferedReader(tmpReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }

                if (Helper.isEmpty(line)) {
                    continue;
                }

                int index = line.indexOf("=");
                if (index < 0) {
                    LOGGER.warn("Skipping configuration at line:" + line);
                    continue;
                }

                String field = line.substring(0, index);
                String value = line.substring(index + 1);
                map.put(field.trim(), value.trim());
            }
        } finally {
            reader.close();
        }
    }
}
