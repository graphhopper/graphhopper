package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.ev.Country;
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
                    return new AustriaSpatialRule(polygons);
                case DEU:
                    return new GermanySpatialRule(polygons);
            }
        } catch (Exception ex) {
        }

        return null;
    }
}
