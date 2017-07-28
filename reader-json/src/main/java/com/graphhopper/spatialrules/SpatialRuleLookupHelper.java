package com.graphhopper.spatialrules;

import com.graphhopper.GraphHopper;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.routing.util.spatialrules.CountriesSpatialRuleFactory;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupBuilder;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;

/**
 * Helper class to build the spatial rule index
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupHelper {

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupHelper.class);

    public static void buildAndInjectSpatialRuleIntoGH(GraphHopper graphHopper, CmdArgs args){
        String spatialRuleLocation = args.get("spatial_rules.location", "");
        if (!spatialRuleLocation.isEmpty()) {
            try {
                final BBox maxBounds = BBox.parseBBoxString(args.get("spatial_rules.max_bbox", "-180, 180, -90, 90"));
                final FileReader reader = new FileReader(spatialRuleLocation);
                final SpatialRuleLookup index = SpatialRuleLookupBuilder.buildIndex(new GHJsonFactory().create().fromJson(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory(), .1, maxBounds);
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
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
