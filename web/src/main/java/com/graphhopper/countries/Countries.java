package com.graphhopper.countries;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;

import java.io.Reader;
import java.util.HashMap;

public class Countries {

    public static SpatialRuleLookup buildIndex(Reader reader, String rules) {
        GHJson ghJson = new GHJsonBuilder().create();
        JsonFeatureCollection jsonFeatureCollection = ghJson.fromJson(reader, JsonFeatureCollection.class);
        return new SpatialRuleLookupBuilder().build(new SpatialRuleListReflectionFactory(rules), jsonFeatureCollection, 1, true);
    }

    static class SpatialRuleListReflectionFactory extends SpatialRuleLookupBuilder.SpatialRuleListFactory {
        SpatialRuleListReflectionFactory(String rules) {
            this(rules.split(","));
        }

        SpatialRuleListReflectionFactory(String... rules) {
            if (rules.length == 0) {
                throw new IllegalStateException("You have to pass at least one rule");
            }
            ruleMap = new HashMap<>(rules.length);
            for (String rule : rules) {
                try {
                    // Makes it easy to define a rule as CountrySpatialRule and skip the fqn
                    if (!rule.contains(".")) {
                        rule = "com.graphhopper.routing.util.spatialrules.countries." + rule;
                    }
                    Object o = Class.forName(rule).newInstance();
                    if (SpatialRule.class.isAssignableFrom(o.getClass())) {
                        ruleMap.put(((SpatialRule) o).getId(), (SpatialRule) o);
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
        }

    }
}
