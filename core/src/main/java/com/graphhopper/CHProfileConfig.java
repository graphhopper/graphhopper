package com.graphhopper;

import com.graphhopper.util.PMap;

public class CHProfileConfig {

    private CHProfileConfig() {
    }

    public static Builder start() {
        return new Builder();
    }

    public PMap asMap() {
        // add here parameters for the CH preparation like logging and algorithm paramaters
        return new PMap();
    }

    public static class Builder {
        CHProfileConfig config = new CHProfileConfig();

        public CHProfileConfig build() {
            return config;
        }
    }
}
