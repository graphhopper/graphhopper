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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Base class for all network objects
 * <p>
 *
 * @author Nop
 * @author Peter
 */
public abstract class ReaderElement {
    public enum Type {
        NODE,
        WAY,
        RELATION,
        FILEHEADER;
    }

    private final long id;
    private final Type type;
    private final Map<String, Object> properties;

    protected ReaderElement(long id, Type type) {
        this(id, type, new LinkedHashMap<>(4));
    }

    protected ReaderElement(long id, Type type, Map<String, Object> properties) {
        if (id < 0) {
            throw new IllegalArgumentException("Invalid OSM " + type + " Id: " + id + "; Ids must not be negative");
        }
        this.id = id;
        this.type = type;
        this.properties = properties;
    }

    public long getId() {
        return id;
    }

    protected String tagsToString() {
        if (properties.isEmpty())
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

    public Map<String, Object> getTags() {
        return properties;
    }

    public void setTags(Map<String, Object> newTags) {
        properties.clear();
        if (newTags != null)
            for (Entry<String, Object> e : newTags.entrySet()) {
                setTag(e.getKey(), e.getValue());
            }
    }

    public boolean hasTags() {
        return !properties.isEmpty();
    }

    public String getTag(String name) {
        return (String) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getTag(String key, T defaultValue) {
        T val = (T) properties.get(key);
        if (val == null)
            return defaultValue;
        return val;
    }

    public void setTag(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Check that the object has a given tag with a given value.
     */
    public boolean hasTag(String key, Object value) {
        return value.equals(getTag(key, ""));
    }

    /**
     * Check that a given tag has one of the specified values. If no values are given, just checks
     * for presence of the tag
     */
    public boolean hasTag(String key, String... values) {
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
        return values.contains(getTag(key, ""));
    }

    /**
     * Check a number of tags in the given order for any of the given values.
     */
    public boolean hasTag(List<String> keyList, Collection<String> values) {
        for (String key : keyList) {
            if (values.contains(getTag(key, "")))
                return true;
        }
        return false;
    }

    /**
     * Check a number of tags in the given order if their value is equal to the specified value.
     */
    public boolean hasTag(List<String> keyList, Object value) {
        for (String key : keyList) {
            if (value.equals(getTag(key, null)))
                return true;
        }
        return false;
    }

    /**
     * Returns the first existing tag of the specified list where the order is important.
     *
     * @return an empty string if nothing found
     */
    public String getFirstPriorityTag(List<String> restrictions) {
        for (String str : restrictions) {
            Object value = properties.get(str);
            if (value != null)
                return (String) value;
        }
        return "";
    }

    public void removeTag(String name) {
        properties.remove(name);
    }

    public void clearTags() {
        properties.clear();
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return properties.toString();
    }
}
