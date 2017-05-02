package com.graphhopper.spatialrules;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupArray;
import com.graphhopper.util.shapes.BBox;
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
     * See {@link SpatialRuleLookup} and {@link SpatialRule}.
     *
     * @param jsonFeatureCollection a feature collection
     * @param jsonIdField the name of a property in that feature collection which serves as an id
     * @param spatialRuleFactory a factory which is called with all the (id, geometry) pairs.
     *                           It should provide a SpatialRule for each id it knows about,
     *                           and SpatialRule.EMPTY otherwise.
     * @param maxBBox limit the maximum BBox of the SpatialRuleLookup to the given BBox
     * @return the fully constructed SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection, String jsonIdField, SpatialRuleFactory spatialRuleFactory, BBox maxBBox) {
        BBox polygonBounds = BBox.createInverse(false);
        List<SpatialRule> spatialRules = new ArrayList<>();
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            String id = (String) jsonFeature.getProperty(jsonIdField);
            List<Polygon> borders = new ArrayList<>();
            for (int i=0; i<jsonFeature.getGeometry().getNumGeometries(); i++) {
                borders.add(ghPolygonFromJTS((com.vividsolutions.jts.geom.Polygon) jsonFeature.getGeometry().getGeometryN(i)));
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

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(spatialRules, 0.1, true, calculatedBounds);

        logger.info("Created the SpatialRuleLookup with the following rules: " + Arrays.toString(spatialRules.toArray()));

        return spatialRuleLookup;
    }

    /**
     * Wrapper Method for {@link SpatialRuleLookupBuilder#buildIndex(JsonFeatureCollection, String, SpatialRuleFactory, BBox)}.
     * This method simply passes a world-wide BBox, this won't limit the SpatialRuleLookup.
     */
    public static SpatialRuleLookup buildIndex(JsonFeatureCollection jsonFeatureCollection, String jsonIdField, SpatialRuleFactory spatialRuleFactory) {
        return buildIndex(jsonFeatureCollection, jsonIdField, spatialRuleFactory, new BBox(-180, 180, -90, 90));
    }

    private static Polygon ghPolygonFromJTS(com.vividsolutions.jts.geom.Polygon polygon) {
        double[] lats = new double[polygon.getNumPoints()];
        double[] lons = new double[polygon.getNumPoints()];
        for (int i=0; i<polygon.getNumPoints(); i++) {
            lats[i] = polygon.getCoordinates()[i].y;
            lons[i] = polygon.getCoordinates()[i].x;
        }
        return new Polygon(lats, lons);
    }

}
