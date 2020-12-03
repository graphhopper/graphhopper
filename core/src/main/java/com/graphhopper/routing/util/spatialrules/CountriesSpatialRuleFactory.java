package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;

public class CountriesSpatialRuleFactory implements SpatialRuleFactory {
    
    @Override
    public SpatialRule createSpatialRule(String id) {
        switch (Country.find(id)) {
        case AUT:
            return new AustriaSpatialRule();
        case DEU:
            return new GermanySpatialRule();
        default:
            return null;
        }
    }
}
