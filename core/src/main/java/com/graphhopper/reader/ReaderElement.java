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
package com.graphhopper.reader;

import java.util.*;

/**
 * Base class for all network objects
 * <p>
 *
 * @author Nop
 * @author Peter
 */
public abstract class ReaderElement {
    public static final int NODE = 0;
    public static final int WAY = 1;
    public static final int RELATION = 2;
    public static final int FILEHEADER = 3;
    private final int type;
    private final long id;
    private Map<String, Object> properties;

    protected ReaderElement(long id, int type) {
        this.id = id;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    protected String tagsToString() {
        if (properties == null || properties.isEmpty())
            return "<empty>";

        StringBuilder tagTxt = new StringBuilder();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            tagTxt.append(entry.getKey());
            tagTxt.append("=");
            tagTxt.append(entry.getValue());
            tagTxt.append("\n");
        }
        return tagTxt.toString();
    }

    protected Map<String, Object> getTags() {
        return properties != null ? properties : Collections.emptyMap();
    }

    public void setTags(Map<String, Object> newTags) {
        properties = newTags;
    }

    public boolean hasTags() {
        return properties != null && !properties.isEmpty();
    }

    public String getTag(String name) {
        if (properties == null) {
            return null;
        }
        return (String) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getTag(String key, T defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        T val = (T) properties.get(key);
        if (val == null)
            return defaultValue;
        return val;
    }

    public List<String> getKeysWithPrefix(String keyPrefix) {
        if (properties == null) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        for (String key : properties.keySet()) {
            if (key.startsWith(keyPrefix)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public void setTag(String name, Object value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    /**
     * Check that the object has a given tag with a given value.
     */
    public boolean hasTag(String key, Object value) {
        if (properties == null) {
            return false;
        }
        return value.equals(getTag(key, ""));
    }

    /**
     * Check that a given tag has one of the specified values. If no values are given, just checks
     * for presence of the tag
     */
    public boolean hasTag(String key, String... values) {
        if (properties == null) {
            return false;
        }
        Object value = properties.get(key);
        if (value == null)
            return false;

        // tag present, no values given: success
        if (values.length == 0)
            return true;

        for (String val : values) {
            if (val.equals(value))
                return true;
        }
        return false;
    }

    /**
     * Check that a given tag has one of the specified values.
     */
    public final boolean hasTag(String key, Collection<String> values) {
        if (properties == null) {
            return false;
        }
        return values.contains(getTag(key, ""));
    }

    /**
     * Check a number of tags in the given order for the any of the given values. Used to parse
     * hierarchical access restrictions
     */
    public boolean hasTag(List<String> keyList, Collection<String> values) {
        if (properties == null) {
            return false;
        }
        for (String key : keyList) {
            if (values.contains(getTag(key, "")))
                return true;
        }
        return false;
    }

    public boolean hasTagWithKeyPrefix(String keyPrefix) {
        if (properties == null) {
            return false;
        }
        for (String key : properties.keySet()) {
            if (key.startsWith(keyPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first existing tag of the specified list where the order is important.
     */
    public String getFirstPriorityTag(List<String> restrictions) {
        if (properties == null) {
            return "";
        }
        for (String str : restrictions) {
            if (hasTag(str))
                return getTag(str);
        }
        return "";
    }

    public void removeTag(String name) {
        if (properties != null) {
            properties.remove(name);
        }
    }

    public void clearTags() {
        if (properties != null) {
            properties.clear();
        }
    }

    public int getType() {
        return type;
    }

    public boolean isType(int type) {
        return this.type == type;
    }

    @Override
    public String toString() {
        if (properties == null) {
            return "{}";
        }
        return properties.toString();
    }
}
