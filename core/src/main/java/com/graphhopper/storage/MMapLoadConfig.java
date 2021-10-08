package com.graphhopper.storage;

/**
 * This is a special configuration class only considered when the MMAP option is used. It defines which portion of the
 * storage it should try to load into physical memory. The default is baseGraph=100%, index=50%, CH=20%, LM=20%.
 * For maximum speed use 100% for everything. For fastest startup time use 0% for everything. Please note that the
 * underlying procedure is only a best effort which could be stopped before the specified percentage is reached.
 */
public class MMapLoadConfig {
    int baseGraphPercentage = 100;
    int indexPercentage = 50;
    int chPercentage = 20;
    int lmPercentage = 20;

    public MMapLoadConfig() {
    }

    public MMapLoadConfig(int baseGraphPercentage, int indexPercentage, int chPercentage, int lmPercentage) {
        this.baseGraphPercentage = baseGraphPercentage;
        this.indexPercentage = indexPercentage;
        this.chPercentage = chPercentage;
        this.lmPercentage = lmPercentage;
    }

    public int getBaseGraphPercentage() {
        return baseGraphPercentage;
    }

    public int getCHPercentage() {
        return chPercentage;
    }

    public int getLMPercentage() {
        return lmPercentage;
    }

    public int getIndexPercentage() {
        return indexPercentage;
    }
}
