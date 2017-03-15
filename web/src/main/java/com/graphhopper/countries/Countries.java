package com.graphhopper.countries;

import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.srlarray.SpatialRuleLookupArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.*;

public class Countries {

    private static final Logger logger = LoggerFactory.getLogger(Countries.class);
    private static final String JSON_ID_FIELD = "ISO_A3";

    public static SpatialRuleLookup buildIndex(Reader reader, String _rules) {
        JsonFeatureCollection jsonFeatureCollection = new GHJsonBuilder().create().fromJson(reader, JsonFeatureCollection.class);

        Map<String, JsonFeature> jsonFeatures = toMap(jsonFeatureCollection);

        List<SpatialRule> spatialRules = new ArrayList<>();
        String[] rules = (_rules.split(","));
        if (rules.length == 0) {
            throw new IllegalStateException("You have to pass at least one rule");
        }
        for (String rule : rules) {
            spatialRules.add(createRuleByReflection(rule, jsonFeatures));
        }

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(spatialRules, (double) 1, true);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(spatialRules.toArray()));

        return spatialRuleLookup;
    }

    private static Map<String, JsonFeature> toMap(JsonFeatureCollection jsonFeatureCollection) {
        Map<String, JsonFeature> jsonFeatures = new HashMap<>();
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            JsonFeature previous = jsonFeatures.put((String) jsonFeature.getProperty(JSON_ID_FIELD), jsonFeature);
            if (previous != null) {
                throw new RuntimeException("The id " + ((String) jsonFeature.getProperty(JSON_ID_FIELD)) + " was already used. Either leave the json property '" + JSON_ID_FIELD + "' empty or use an unique id.");
            }
        }
        return jsonFeatures;
    }

    private static SpatialRule createRuleByReflection(String ruleClassName, Map<String, JsonFeature> jsonFeatures) {
        SpatialRule o1;
        try {
            // Makes it easy to define a rule as CountrySpatialRule and skip the fqn
            if (!ruleClassName.contains(".")) {
                ruleClassName = "com.graphhopper.routing.util.spatialrules.countries." + ruleClassName;
            }
            Object o = Class.forName(ruleClassName).newInstance();
            if (SpatialRule.class.isAssignableFrom(o.getClass())) {
                o1 = (SpatialRule) o;
            } else {
                String ex = "Cannot find SpatialRule for rule " + ruleClassName + " but found " + o.getClass();
                logger.error(ex);
                throw new IllegalArgumentException(ex);
            }
        } catch (ReflectiveOperationException e) {
            String ex = "Cannot find SpatialRule for rule " + ruleClassName;
            logger.error(ex);
            throw new IllegalArgumentException(ex, e);
        }
        o1.setBorders(jsonFeatures.get(o1.getId()).getGeometry().asPolygon().getPolygons());
        return o1;
    }

}
