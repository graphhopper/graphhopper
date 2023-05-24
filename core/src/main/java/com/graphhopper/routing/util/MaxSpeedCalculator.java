package com.graphhopper.routing.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.StopWatch;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import de.westnordost.osm_legal_default_speeds.RoadType;
import de.westnordost.osm_legal_default_speeds.RoadTypeFilter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;

public class MaxSpeedCalculator {

    private final LegalDefaultSpeeds defaultSpeeds;

    public MaxSpeedCalculator(LegalDefaultSpeeds defaultSpeeds) {
        this.defaultSpeeds = defaultSpeeds;
    }

    public LegalDefaultSpeeds getDefaultSpeeds() {
        return defaultSpeeds;
    }

    public static LegalDefaultSpeeds createLegalDefaultSpeeds() {
        SpeedLimitsJson data;
        try {
            data = new ObjectMapper().readValue(MaxSpeedCalculator.class.getResource("legal_default_speeds.json"), SpeedLimitsJson.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LegalDefaultSpeeds speeds = new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
//        speeds.setLimitOtherSpeeds(false);
        return speeds;
    }

    /**
     * This method sets max_speed values where the value is UNSET_SPEED to a value determined by
     * the default speed library which is country-dependent.
     */
    public void fillMaxSpeed(Graph graph, EncodingManager em) {
        EnumEncodedValue<UrbanDensity> urbanDensityEnc = em.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity.class);
        EnumEncodedValue<RoadClass> roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<Country> countryEnumEncodedValue = em.getEnumEncodedValue(Country.KEY, Country.class);
        DecimalEncodedValue maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);
        BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
        IntEncodedValue lanesEnc = em.hasEncodedValue(Lanes.KEY) ? em.getIntEncodedValue(Lanes.KEY) : null;
        EnumEncodedValue<Surface> surfaceEnc = em.hasEncodedValue(Surface.KEY) ? em.getEnumEncodedValue(Surface.KEY, Surface.class) : null;

        StopWatch sw = new StopWatch().start();
        List<Map<String, String>> relTags = new ArrayList<>();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            // This is tricky as it could be a highly customized (car) profile.
            // if (isOneway(iter)) tags.put("oneway", "yes");

            double fwdMaxSpeedPureOSM = iter.get(maxSpeedEnc);
            double bwdMaxSpeedPureOSM = iter.getReverse(maxSpeedEnc);

            // skip library if max_speed is known
            if (fwdMaxSpeedPureOSM != MaxSpeed.UNSET_SPEED) continue;
            // library does not work for the case that forward/backward are different
            if (fwdMaxSpeedPureOSM != bwdMaxSpeedPureOSM) continue;

            // In OSMMaxSpeedParser we don't have the rural/urban info, but now we have and can
            // fill the country-dependent max_speed value.
            UrbanDensity urbanDensity = iter.get(urbanDensityEnc);

            // ISO 3166-1 alpha-2 code optionally concatenated with a ISO 3166-2 code, e.g. "DE", "US" or "BE-VLG"
            String countryCode = iter.get(countryEnumEncodedValue).getAlpha2();
            Map<String, String> tags = new HashMap<>();
            tags.put("highway", iter.get(roadClassEnc).toString());
            if (iter.get(roundaboutEnc))
                tags.put("junction", "roundabout");
            if (lanesEnc != null && iter.get(lanesEnc) > 0)
                tags.put("lanes", "" + iter.get(lanesEnc));
            if (surfaceEnc != null && iter.get(surfaceEnc) != Surface.MISSING)
                tags.put("surface", iter.get(surfaceEnc).toString());

            LegalDefaultSpeeds.Result result = defaultSpeeds.getSpeedLimits(countryCode, tags, relTags, (name, eval) -> {
                if (eval.invoke()) return true;
                if ("urban".equals(name))
                    return urbanDensity != UrbanDensity.RURAL;
                if ("rural".equals(name))
                    return urbanDensity == UrbanDensity.RURAL;
                return false;
            });
            if (result != null)
                try {
                    int max = Integer.parseInt(result.getTags().get("maxspeed"));
                    iter.set(maxSpeedEnc, max, max);
                } catch (NumberFormatException ex) {
                }
        }

        LoggerFactory.getLogger(getClass()).info("max_speed_calculator took: " + sw.stop().getSeconds());
    }

    static class SpeedLimitsJson {
        @JsonProperty
        private Map<String, String> meta;
        @JsonProperty
        private Map<String, RoadTypeFilterImpl> roadTypesByName;
        @JsonProperty
        private Map<String, List<RoadTypeImpl>> speedLimitsByCountryCode;
        @JsonProperty
        private List<String> warnings;
    }

    static class RoadTypeFilterImpl implements RoadTypeFilter {

        private String filter, fuzzyFilter, relationFilter;

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public void setFuzzyFilter(String fuzzyFilter) {
            this.fuzzyFilter = fuzzyFilter;
        }

        public void setRelationFilter(String relationFilter) {
            this.relationFilter = relationFilter;
        }

        @Override
        public String getFilter() {
            return filter;
        }

        @Override
        public String getFuzzyFilter() {
            return fuzzyFilter;
        }

        @Override
        public String getRelationFilter() {
            return relationFilter;
        }
    }

    static class RoadTypeImpl implements RoadType {

        private String name;
        private Map<String, String> tags;

        public void setName(String name) {
            this.name = name;
        }

        public void setTags(Map<String, String> tags) {
            this.tags = tags;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getTags() {
            return tags;
        }
    }
}
