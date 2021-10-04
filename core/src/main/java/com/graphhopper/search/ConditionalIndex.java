package com.graphhopper.search;

import com.graphhopper.storage.Directory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrzej Oles
 */
public class ConditionalIndex extends NameIndex {
    Map<String, Long> values = new HashMap<>();

    @Override
    public long put(String name) {
        Long index = values.get(name);

        if (index == null) {
            index = super.put(name);
            values.put(name, index);
        }

        return index;
    }

    public ConditionalIndex(Directory dir, String filename) {
        super(dir, filename);
    }

}
