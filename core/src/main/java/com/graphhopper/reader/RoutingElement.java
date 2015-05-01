package com.graphhopper.reader;

import java.util.List;
import java.util.Set;

public interface RoutingElement {

    void setTag(String name, Object value);

    String getTag(String name);

    <T> T getTag(String key, T defaultValue);

    boolean hasTags();

    boolean hasTag(String key, String... values);

    boolean hasTag(String key, Object value);

    boolean hasTag(String key, Set<String> values);

    boolean hasTag(List<String> keyList, Set<String> values);

    int getType();

    boolean isType(int way);

}