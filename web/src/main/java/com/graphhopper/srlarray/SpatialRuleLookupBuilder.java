package com.graphhopper.srlarray;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SpatialRuleLookupBuilder {

    public interface SpatialRuleFactory {
        SpatialRule createSpatialRule(String id, final List<Polygon> polygons);
    }

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupBuilder.class);

    /**
     * Builds a SpatialRuleLookup by passing the provided JSON features into the provided
     * SpatialRuleFactory and collecting all the SpatialRule instances that it returns,
     * ignoring when it returns SpatialRule.EMPTY.
     *
     * See SpatialRuleLookup and SpatialRule.
     *
     * @param jsonFeatureCollection a feature collection
     * @param jsonIdField the name of a property in that feature collection which serves as an id
     * @param spatialRuleFactory a factory which is called with all the (id, geometry) pairs.
     *                           It should provide a SpatialRule for each id it knows about,
     *                           and SpatialRule.EMPTY otherwise.
     * @return the fully constructed SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection, String jsonIdField, SpatialRuleFactory spatialRuleFactory) {
        List<SpatialRule> spatialRules = new ArrayList<>();
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            String id = (String) jsonFeature.getProperty(jsonIdField);
            List<Polygon> borders = jsonFeature.getGeometry().asPolygon().getPolygons();
            SpatialRule spatialRule = spatialRuleFactory.createSpatialRule(id, borders);
            if (spatialRule != SpatialRule.EMPTY) {
                spatialRules.add(spatialRule);
            }
        }

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(spatialRules, 1.0, true);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(spatialRules.toArray()));

        return spatialRuleLookup;
    }

}
