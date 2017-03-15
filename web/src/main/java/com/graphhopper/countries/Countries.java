package com.graphhopper.countries;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.srlarray.SpatialRuleLookupArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Countries {

    private static final Logger logger = LoggerFactory.getLogger(Countries.class);
    private static final String JSON_ID_FIELD = "ISO_A3";

    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection) {
        CountriesSpatialRuleFactory countriesSpatialRuleFactory = new CountriesSpatialRuleFactory();
        List<SpatialRule> spatialRules = new ArrayList<>();
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            String id = (String) jsonFeature.getProperty(JSON_ID_FIELD);
            List<Polygon> borders = jsonFeature.getGeometry().asPolygon().getPolygons();
            SpatialRule spatialRule = countriesSpatialRuleFactory.createSpatialRule(id, borders);
            if (spatialRule != SpatialRule.EMPTY) {
                spatialRules.add(spatialRule);
            }
        }

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(spatialRules, (double) 1, true);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(spatialRules.toArray()));

        return spatialRuleLookup;
    }

}
