package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.Helper;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.util.Helper.toLowerCase;

public class SpatialRuleLookupBuilder {

    public interface SpatialRuleFactory {
        SpatialRule createSpatialRule(String id, final List<Polygon> borders);
    }

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupBuilder.class);

    /**
     * Builds a SpatialRuleLookup by passing the provided JSON features into the provided
     * SpatialRuleFactory and collecting all the SpatialRule instances that it returns,
     * ignoring when it returns SpatialRule.EMPTY.
     * <p>
     * See {@link SpatialRuleLookup} and {@link SpatialRule}.
     *
     * @param jsonFeatureCollections a List of feature collections
     * @param jsonIdField            the name of a property in that feature collection which serves as an id
     * @param spatialRuleFactory     a factory which is called with all the (id, geometry) pairs.
     *                               It should provide a SpatialRule for each id it knows about,
     *                               and SpatialRule.EMPTY otherwise.
     * @param maxBBox                limit the maximum BBox of the SpatialRuleLookup to the given Envelope
     * @return the fully constructed SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(List<JsonFeatureCollection> jsonFeatureCollections, String jsonIdField,
                                               SpatialRuleFactory spatialRuleFactory, Envelope maxBBox) {
        Envelope envelope = new Envelope();
        List<SpatialRule> spatialRules = new ArrayList<>();
        Map<String, JsonFeature> featureMap = new HashMap<>();
        for (JsonFeatureCollection featureCollection : jsonFeatureCollections) {
            for (JsonFeature jsonFeature : featureCollection.getFeatures()) {
                String id = jsonIdField.isEmpty() || toLowerCase(jsonIdField).equals("id") ? jsonFeature.getId() : (String) jsonFeature.getProperty(jsonIdField);
                if (id == null || id.isEmpty())
                    throw new IllegalArgumentException("ID cannot be empty but was for JsonFeature " + featureCollection.getFeatures().indexOf(jsonFeature));

                JsonFeature old = featureMap.put(id, jsonFeature);
                if (old != null)
                    throw new IllegalStateException("SpatialRule with ID " + id + " already exists: " + old.getProperties());
                List<Polygon> borders = new ArrayList<>();
                for (int i = 0; i < jsonFeature.getGeometry().getNumGeometries(); i++) {
                    Geometry poly = jsonFeature.getGeometry().getGeometryN(i);
                    if (poly instanceof Polygon)
                        borders.add((Polygon) poly);
                    else
                        throw new IllegalArgumentException("Geometry for " + id + " (" + i + ") not supported " + poly.getClass().getSimpleName());
                }

                SpatialRule spatialRule = spatialRuleFactory.createSpatialRule(Helper.toLowerCase(id), borders);
                if (spatialRule != SpatialRule.EMPTY) {
                    spatialRules.add(spatialRule);
                    for (Polygon polygon : spatialRule.getBorders()) {
                        envelope.expandToInclude(polygon.getEnvelopeInternal());
                    }
                }
            }
        }

        Envelope calculatedBounds = envelope.intersection(maxBBox);
        if (calculatedBounds.isNull())
            return SpatialRuleLookup.EMPTY;

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupJTS(spatialRules, calculatedBounds);
        logger.info("Created the SpatialRuleLookup with the following rules: {}", Arrays.toString(spatialRules.toArray()));
        return spatialRuleLookup;
    }

    /**
     * Wrapper Method for {@link SpatialRuleLookupBuilder#buildIndex(List, String, SpatialRuleFactory, Envelope)}.
     * This method simply passes a world-wide BBox, this won't limit the SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(List<JsonFeatureCollection> jsonFeatureCollections, String jsonIdField, SpatialRuleFactory spatialRuleFactory) {
        return buildIndex(jsonFeatureCollections, jsonIdField, spatialRuleFactory, new Envelope(-180, 180, -90, 90));
    }
}
