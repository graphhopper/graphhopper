package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.Geometry;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.util.shapes.BBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * Crates a SpatialRuleLookup for every relevant Country
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilder {

    private final static BBox DEFAULT_BOUNDS = new BBox(-180, 180, -90, 90);
    private final static double DEFAULT_RESOLUTION = .1;

    private final static SpatialRule[] rules = new SpatialRule[]{
            new AustriaSpatialRule(),
            new GermanySpatialRule()
    };

    public static SpatialRuleLookup build() {
        return SpatialRuleLookupBuilder.build(DEFAULT_BOUNDS, DEFAULT_RESOLUTION);
    }

    public static SpatialRuleLookup build(BBox bounds, double resolution) {
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(bounds, resolution);
        try {
            GHJson ghJson = new GHJsonBuilder().create();
            JsonFeatureCollection jsonFeatureCollection = ghJson.fromJson(new FileReader(new File(SpatialRuleLookupBuilder.class.getResource("countries.json").getFile())), JsonFeatureCollection.class);

            for (SpatialRule spatialRule : rules) {
                for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
                    if (spatialRule.getCountryIsoA3Name().equals(jsonFeature.getProperty("ISO_A3"))) {
                        Geometry geometry = jsonFeature.getGeometry();
                        if (geometry.isPolygon()) {
                            spatialRuleLookup.addRules(spatialRule, geometry.asPolygon().getPolygons());
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        return spatialRuleLookup;
    }

}
