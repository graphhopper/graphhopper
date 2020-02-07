package com.graphhopper;

import com.graphhopper.storage.DAType;

public final class GraphConfig {
    private String graphCacheFolder;

    private GraphConfig() {
    }

    public static Builder start() {
        return new Builder();
    }

    public String getGraphCacheFolder() {
        return graphCacheFolder;
    }

    public DAType getDAType() {
        return DAType.RAM_STORE;
    }

    public boolean hasElevation() {
        return false;
    }

    public int getDefaultSegmentSize() {
        return -1;
    }

    public static class Builder {
        GraphConfig config = new GraphConfig();

        public Builder graphCacheFolder(String graphCacheFolder) {
            config.graphCacheFolder = graphCacheFolder;
            return this;
        }

        public GraphConfig build() {
            return config;
        }
    }
}
