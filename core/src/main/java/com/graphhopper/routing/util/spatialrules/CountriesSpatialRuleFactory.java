package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.LiechtensteinSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SwitzerlandSpatialRule;
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
            case "CHE":
                SwitzerlandSpatialRule switzerlandSpatialRule = new SwitzerlandSpatialRule();
                switzerlandSpatialRule.setBorders(polygons);
                return switzerlandSpatialRule;
            case "DEU":
                GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                germanySpatialRule.setBorders(polygons);
                return germanySpatialRule;
            case "LIE":
                LiechtensteinSpatialRule liechtensteinSpatialRule = new LiechtensteinSpatialRule();
                liechtensteinSpatialRule.setBorders(polygons);
                return liechtensteinSpatialRule;
        }
        return SpatialRule.EMPTY;
    }
}
