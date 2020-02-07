package com.graphhopper;

public class CHProfileConfig {

    private CHProfileConfig() {
    }

    // add here parameters for the CH preparation like logging and algorithm paramaters
    public static Builder start() {
        return new Builder();
    }

    public static class Builder {
        CHProfileConfig config = new CHProfileConfig();

        public CHProfileConfig build() {
            return config;
        }
    }
}
