package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

public class CountriesSpatialRuleFactory implements SpatialRuleFactory {
    
    @Override
    public SpatialRule createSpatialRule(String id, List<Polygon> polygons) {
        switch (id) {
        case AustriaSpatialRule.ID:
            return new AustriaSpatialRule(polygons);
        case GermanySpatialRule.ID:
            return new GermanySpatialRule(polygons);
        default:
            return null;
        }
    }
}
