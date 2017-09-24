package com.graphhopper.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.routing.util.EncodingManager;

public class GHModule extends SimpleModule {
    public GHModule() {
        addDeserializer(EncodingManager.class, new EncodingManagerDeserializer());
    }
}
