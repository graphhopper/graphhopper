package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.GraphHopper;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to build the spatial rule index
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupHelper {

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupHelper.class);

    public static void buildAndInjectSpatialRuleIntoGH(GraphHopper graphHopper, BBox maxBounds, JsonFeatureCollection jsonFeatureCollection) {
        final SpatialRuleLookup index = SpatialRuleLookupBuilder.buildIndex(jsonFeatureCollection, "ISO_A3", new CountriesSpatialRuleFactory(), .1, maxBounds);
        logger.info("Set spatial rule lookup with " + index.size() + " rules");
        final FlagEncoderFactory oldFEF = graphHopper.getFlagEncoderFactory();
        graphHopper.setFlagEncoderFactory(new FlagEncoderFactory() {
            @Override
            public FlagEncoder createFlagEncoder(String name, PMap configuration) {
                if (name.equals(GENERIC)) {
                    return new DataFlagEncoder(configuration).setSpatialRuleLookup(index);
                }

                return oldFEF.createFlagEncoder(name, configuration);
            }
        });
    }

}
