package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.BelgiumSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.CzechiaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.DenmarkSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.LiechtensteinSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.NetherlandsSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.PolandSpatialRule;
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
            case "BEL":
                BelgiumSpatialRule belgiumSpatialRule = new BelgiumSpatialRule();
                belgiumSpatialRule.setBorders(polygons);
                return belgiumSpatialRule;
            case "CHE":
                SwitzerlandSpatialRule switzerlandSpatialRule = new SwitzerlandSpatialRule();
                switzerlandSpatialRule.setBorders(polygons);
                return switzerlandSpatialRule;
            case "CZE":
                CzechiaSpatialRule czechiaSpatialRule = new CzechiaSpatialRule();
                czechiaSpatialRule.setBorders(polygons);
                return czechiaSpatialRule;
            case "DEU":
                GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                germanySpatialRule.setBorders(polygons);
                return germanySpatialRule;
            case "DNK":
                DenmarkSpatialRule denmarkSpatialRule = new DenmarkSpatialRule();
                denmarkSpatialRule.setBorders(polygons);
                return denmarkSpatialRule;
            case "LIE":
                LiechtensteinSpatialRule liechtensteinSpatialRule = new LiechtensteinSpatialRule();
                liechtensteinSpatialRule.setBorders(polygons);
                return liechtensteinSpatialRule;
            case "NLD":
                NetherlandsSpatialRule netherlandsSpatialRule = new NetherlandsSpatialRule();
                netherlandsSpatialRule.setBorders(polygons);
                return netherlandsSpatialRule;
            case "POL":
                PolandSpatialRule polandSpatialRule = new PolandSpatialRule();
                polandSpatialRule.setBorders(polygons);
                return polandSpatialRule;
            default:
                return SpatialRule.EMPTY;
        }
    }
}
