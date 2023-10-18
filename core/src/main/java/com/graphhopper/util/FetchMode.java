package com.graphhopper.util;

/**
 * This enum is used to specify which nodes should be included in the PointList when calling
 * {@link EdgeIteratorState#fetchWayGeometry(FetchMode)}. See also docs/core/low-level-api.md
 */
public enum FetchMode {
    TOWER_ONLY, PILLAR_ONLY, BASE_AND_PILLAR, PILLAR_AND_ADJ, ALL
}
