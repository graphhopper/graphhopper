package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.profiles.Country;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static com.graphhopper.util.Helper.toUpperCase;

public class CountriesSpatialRuleFactory implements SpatialRuleLookupBuilder.SpatialRuleFactory {

    @Override
    public SpatialRule createSpatialRule(String id, List<Polygon> polygons) {
        try {
            Country country = Country.valueOf(toUpperCase(id));
            switch (country) {
                case AUT:
                    AustriaSpatialRule austriaSpatialRule = new AustriaSpatialRule();
                    austriaSpatialRule.setBorders(polygons);
                    return austriaSpatialRule;
                case DEU:
                    GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                    germanySpatialRule.setBorders(polygons);
                    return germanySpatialRule;
            }
        } catch (Exception ex) {
        }

        return SpatialRule.EMPTY;
    }
}
