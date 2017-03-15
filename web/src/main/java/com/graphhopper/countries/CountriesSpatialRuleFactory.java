package com.graphhopper.countries;

import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;

import java.util.List;

public class CountriesSpatialRuleFactory implements SpatialRuleFactory {
    @Override
    public SpatialRule createSpatialRule(String id, List<Polygon> polygons) {
        switch (id) {
            case "AUT":
                AustriaSpatialRule austriaSpatialRule = new AustriaSpatialRule();
                austriaSpatialRule.setBorders(polygons);
                return austriaSpatialRule;
            case "DEU":
                GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                germanySpatialRule.setBorders(polygons);
                return germanySpatialRule;
        }
        return SpatialRule.EMPTY;
    }
}
