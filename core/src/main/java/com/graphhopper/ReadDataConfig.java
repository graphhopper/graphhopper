package com.graphhopper;

public final class ReadDataConfig {
    private String dataReaderFile;

    private ReadDataConfig() {
    }

    public static Builder start() {
        return new Builder();
    }

    public String getDataReaderFile() {
        return dataReaderFile;
    }

    public boolean isSortingEnabled() {
        return false;
    }

    public boolean isCleanUpEnabled() {
        return false;
    }

    public int getMinOneWayNetworkSize() {
        return 200;
    }

    public int getMinNetworkSize() {
        return 200;
    }

    public static class Builder {
        ReadDataConfig config = new ReadDataConfig();

        public Builder dataReaderFile(String dataReaderFile) {
            config.dataReaderFile = dataReaderFile;
            return this;
        }

        public ReadDataConfig build() {
            return config;
        }
    }
}
