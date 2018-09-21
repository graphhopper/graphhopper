package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.routing.util.EncodingManager;

public abstract class AbstractTagParser implements EncodingManager.TagParser {

    private final String name;

    public AbstractTagParser(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return getName().equals(((EncodingManager.TagParser) obj).getName());
    }
}
