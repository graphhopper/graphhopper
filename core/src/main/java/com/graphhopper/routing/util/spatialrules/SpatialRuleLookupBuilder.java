package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Helper.toLowerCase;

public class SpatialRuleLookupBuilder {

    public interface SpatialRuleFactory {
        SpatialRule createSpatialRule(String id, final List<Polygon> polygons);
    }

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupBuilder.class);

    /**
     * Builds a SpatialRuleLookup by passing the provided JSON features into the provided
     * SpatialRuleFactory and collecting all the SpatialRule instances that it returns,
     * ignoring when it returns SpatialRule.EMPTY.
     * <p>
     * See {@link SpatialRuleLookup} and {@link SpatialRule}.
     *
     * @param jsonFeatureCollection a feature collection
     * @param jsonIdField           the name of a property in that feature collection which serves as an id
     * @param spatialRuleFactory    a factory which is called with all the (id, geometry) pairs.
     *                              It should provide a SpatialRule for each id it knows about,
     *                              and SpatialRule.EMPTY otherwise.
     * @param maxBBox               limit the maximum BBox of the SpatialRuleLookup to the given BBox
     * @return the fully constructed SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection, String jsonIdField,
                                               SpatialRuleFactory spatialRuleFactory, double resolution, BBox maxBBox) {
        BBox polygonBounds = BBox.createInverse(false);
        List<SpatialRule> spatialRules = new ArrayList<>();

        for (int jsonFeatureIdx = 0; jsonFeatureIdx < jsonFeatureCollection.getFeatures().size(); jsonFeatureIdx++) {
            JsonFeature jsonFeature = jsonFeatureCollection.getFeatures().get(jsonFeatureIdx);
            String id = jsonIdField.isEmpty() || toLowerCase(jsonIdField).equals("id") ? jsonFeature.getId() : (String) jsonFeature.getProperty(jsonIdField);
            if (id == null || id.isEmpty())
                throw new IllegalArgumentException("ID cannot be empty but was for JsonFeature " + jsonFeatureIdx);

            List<Polygon> borders = new ArrayList<>();
            for (int i = 0; i < jsonFeature.getGeometry().getNumGeometries(); i++) {
                Geometry poly = jsonFeature.getGeometry().getGeometryN(i);
                if (poly instanceof org.locationtech.jts.geom.Polygon)
                    borders.add(Polygon.create((org.locationtech.jts.geom.Polygon) poly));
                else
                    throw new IllegalArgumentException("Geometry for " + id + " (" + i + ") not supported " + poly.getClass().getSimpleName());
            }
            SpatialRule spatialRule = spatialRuleFactory.createSpatialRule(id, borders);
            if (spatialRule != SpatialRule.EMPTY) {
                spatialRules.add(spatialRule);
                for (Polygon polygon : spatialRule.getBorders()) {
                    polygonBounds.update(polygon.getMinLat(), polygon.getMinLon());
                    polygonBounds.update(polygon.getMaxLat(), polygon.getMaxLon());
                }
            }
        }

        if (!polygonBounds.isValid()) {
            return SpatialRuleLookup.EMPTY;
        }

        BBox calculatedBounds = polygonBounds.calculateIntersection(maxBBox);
        if (calculatedBounds == null)
            return SpatialRuleLookup.EMPTY;

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(spatialRules, resolution, true, calculatedBounds);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(spatialRules.toArray()));

        return spatialRuleLookup;
    }

    /**
     * Wrapper Method for {@link SpatialRuleLookupBuilder#buildIndex(JsonFeatureCollection, String, SpatialRuleFactory, double, BBox)}.
     * This method simply passes a world-wide BBox, this won't limit the SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection, String jsonIdField, SpatialRuleFactory spatialRuleFactory) {
        return buildIndex(jsonFeatureCollection, jsonIdField, spatialRuleFactory, .1, new BBox(-180, 180, -90, 90));
    }
}
