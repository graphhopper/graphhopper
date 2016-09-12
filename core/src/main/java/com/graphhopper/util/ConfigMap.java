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
package com.graphhopper.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A properties map (String to Object) with convenient accessors
 * <p>
 *
 * @author Peter Karich
 * @see PMap
 */
public class ConfigMap {
    private final Map<String, Object> map;

    public ConfigMap() {
        this(5);
    }

    public ConfigMap(int capacity) {
        this(new HashMap<String, Object>(capacity));
    }

    public ConfigMap(Map<String, Object> map) {
        this.map = map;
    }

    public ConfigMap put(ConfigMap map) {
        this.map.putAll(map.map);
        return this;
    }

    String checkKey(String key) {
        if (!key.toLowerCase().equals(key))
            throw new NullPointerException("keys have to be lower case but wasn't: " + key);
        return key;
    }

    public ConfigMap put(String key, Object obj) {
        if (obj == null)
            throw new NullPointerException("Value cannot be null for key " + key + ". Use remove instead.");

        map.put(checkKey(key), obj);
        return this;
    }

    public ConfigMap remove(String key) {
        map.remove(checkKey(key));
        return this;
    }

    public boolean has(String key) {
        return map.containsKey(checkKey(key));
    }

    public long getLong(String key, long _default) {
        Long t = (Long) map.get(checkKey(key));
        if (t == null)
            return _default;
        return t;
    }

    public int getInt(String key, int _default) {
        Integer t = (Integer) map.get(checkKey(key));
        if (t == null)
            return _default;
        return t;
    }

    public boolean getBool(String key, boolean _default) {
        Boolean t = (Boolean) map.get(checkKey(key));
        if (t == null)
            return _default;
        return t;
    }

    public double getDouble(String key, double _default) {
        Double t = (Double) map.get(checkKey(key));
        if (t == null)
            return _default;
        return t;
    }

    public <T> T get(String key, T _default) {
        T t = (T) map.get(checkKey(key));
        if (t == null)
            return _default;
        return t;
    }

    public <T> Map<String, T> getMap(String key, Class<T> embed) {
        return (Map<String, T>) map.get(checkKey(key));
    }

    public Map<String, Object> getMap(String key) {
        return (Map<String, Object>) map.get(checkKey(key));
    }

    public List getList(String key) {
        return (List) map.get(checkKey(key));
    }

    public <T> List<T> getList(String key, Class<T> embed) {
        return (List<T>) map.get(checkKey(key));
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
