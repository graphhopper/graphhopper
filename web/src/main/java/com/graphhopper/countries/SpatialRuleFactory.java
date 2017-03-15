package com.graphhopper.countries;

import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;

import java.util.List;

public interface SpatialRuleFactory {
    /**
     * This method creates a SpatialRule out of the provided polygons indicating the 'border'.
     */
    SpatialRule createSpatialRule(String id, final List<Polygon> polygons);
}
