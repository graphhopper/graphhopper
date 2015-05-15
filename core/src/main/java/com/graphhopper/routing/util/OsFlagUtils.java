package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

/**
 * Utility class to contain more complex flag and tag operations
 *
 * @author phopkins
 *
 */
public class OsFlagUtils {
    public static boolean hasTag(Way way, String key, String value) {
        String wayTag = way.getTag(key);
        if (null != wayTag) {
            String[] values = wayTag.split(",");
            for (String tvalue : values) {
                if (tvalue.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setOrAppendTag(Way way, String key, String value) {
        String currentValue = way.getTag(key);
        if (currentValue != null) {
            way.setTag(key, currentValue + "," + value);
        } else {
            // This is the first time we are adding it so just add it
            way.setTag(key, value);
        }
    }

}
