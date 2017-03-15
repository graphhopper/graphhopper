package com.graphhopper.countries;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.Geometry;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.Polygon;
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

        List<SpatialRule> completeRules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int unknownCounter = 0;
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            Geometry geometry = jsonFeature.getGeometry();
            if (!geometry.isPolygon())
                continue;

            List<Polygon> borders = geometry.asPolygon().getPolygons();
            String id = (String) jsonFeature.getProperty(JSON_ID_FIELD);
            if (id == null || id.isEmpty()) {
                id = "_unknown_id_" + unknownCounter;
                unknownCounter++;
            }

            if (ids.contains(id))
                throw new RuntimeException("The id " + id + " was already used. Either leave the json property '" + JSON_ID_FIELD + "' empty or use an unique id.");

            ids.add(id);

            Map<String, SpatialRule> ruleMap;

            ruleMap = new HashMap<>(spatialRules.size());
            for (SpatialRule rule : spatialRules) {
                ruleMap.put(rule.getId(), rule);
            }

            if (id == null)
                throw new IllegalArgumentException("ID cannot be null to find a SpatialRule");

            SpatialRule spatialRule = ruleMap.get(id);
            if (spatialRule != null) {
                spatialRule.setBorders(borders);
            } else {
                spatialRule = SpatialRule.EMPTY;
            }



            if (spatialRule == SpatialRule.EMPTY)
                continue;

            completeRules.add(spatialRule);

        }

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(completeRules, (double) 1, true);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(completeRules.toArray()));

        return spatialRuleLookup;
    }

}
