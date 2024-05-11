package com.graphhopper.routing.util;

import java.util.List;

public interface CacheFunction {
    int getIndex(String var);

    List<Object> calc(int edgeKey);
}
