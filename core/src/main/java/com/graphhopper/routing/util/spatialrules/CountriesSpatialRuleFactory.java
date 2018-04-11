package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.util.shapes.Polygon;

import java.util.List;

public class CountriesSpatialRuleFactory implements SpatialRuleLookupBuilder.SpatialRuleFactory {
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
