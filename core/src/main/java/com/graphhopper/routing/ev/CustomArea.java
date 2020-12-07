package com.graphhopper.routing.ev;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class CustomArea {
    
    private static final String CUSTOM_EV_PREFIX = "custom_area";
    
    public static String key(String str) {
        return getKey(CUSTOM_EV_PREFIX, str);
    }
}
