package com.graphhopper.countries;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class Countries {

    private static final Logger logger = LoggerFactory.getLogger(Countries.class);

    public static SpatialRuleLookup buildIndex(Reader reader, String _rules) {
        GHJson ghJson = new GHJsonBuilder().create();
        JsonFeatureCollection jsonFeatureCollection = ghJson.fromJson(reader, JsonFeatureCollection.class);
        String[] rules = (_rules.split(","));

        List<SpatialRule> spatialRules = new ArrayList<>();
        if (rules.length == 0) {
            throw new IllegalStateException("You have to pass at least one rule");
        }
        for (String rule : rules) {
            try {
                // Makes it easy to define a rule as CountrySpatialRule and skip the fqn
                if (!rule.contains(".")) {
                    rule = "com.graphhopper.routing.util.spatialrules.countries." + rule;
                }
                Object o = Class.forName(rule).newInstance();
                if (SpatialRule.class.isAssignableFrom(o.getClass())) {
                    spatialRules.add((SpatialRule) o);
                } else {
                    String ex = "Cannot find SpatialRule for rule " + rule + " but found " + o.getClass();
                    logger.error(ex);
                    throw new IllegalArgumentException(ex);
                }
            } catch (ReflectiveOperationException e) {
                String ex = "Cannot find SpatialRule for rule " + rule;
                logger.error(ex);
                throw new IllegalArgumentException(ex, e);
            }
        }

        return SpatialRuleLookupBuilder.build(new SpatialRuleLookupBuilder.SpatialRuleListFactory(spatialRules), jsonFeatureCollection, 1, true);
    }

}
