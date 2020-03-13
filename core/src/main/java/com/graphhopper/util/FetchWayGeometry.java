package com.graphhopper.util;

public enum FetchWayGeometry {
    TOWER_ONLY, PILLAR_ONLY, BASE_AND_PILLAR, PILLAR_AND_ADJ, ALL;

    public static int count(int count, FetchWayGeometry mode) {
        switch (mode) {
            case TOWER_ONLY:
                return 2;
            case PILLAR_ONLY:
                return count;
            case BASE_AND_PILLAR:
            case PILLAR_AND_ADJ:
                return count + 1;
            case ALL:
                return count + 2;
        }
        throw new IllegalArgumentException("Mode isn't handled " + mode);
    }
}
