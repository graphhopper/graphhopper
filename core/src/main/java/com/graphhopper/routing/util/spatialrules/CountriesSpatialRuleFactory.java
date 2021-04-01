package com.graphhopper.routing.util.spatialrules;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;

public class CountriesSpatialRuleFactory {
    
    private CountriesSpatialRuleFactory() {
    }
    
    private static final List<SpatialRule> RULES = Collections.unmodifiableList(Arrays.asList(
                    new AustriaSpatialRule(),
                    new GermanySpatialRule()));
    
    public static List<SpatialRule> getRules() {
        return RULES;
    }
}
