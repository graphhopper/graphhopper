package com.graphhopper.routing.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.StopWatch;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import de.westnordost.osm_legal_default_speeds.RoadType;
import de.westnordost.osm_legal_default_speeds.RoadTypeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaxSpeedCalculator {

    private final Graph graph;
    private final LegalDefaultSpeeds defaultSpeeds;
    private final EnumEncodedValue<UrbanDensity> urbanDensityEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<Country> countryEnumEncodedValue;
    private final DecimalEncodedValue maxSpeedEnc;
    private final BooleanEncodedValue roundaboutEnc;

    public MaxSpeedCalculator(LegalDefaultSpeeds defaultSpeeds, Graph graph, EncodingManager em) {
        this.graph = graph;
        this.defaultSpeeds = defaultSpeeds;
        urbanDensityEnc = em.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity.class);
        roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        countryEnumEncodedValue = em.getEnumEncodedValue(Country.KEY, Country.class);
        maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);
        roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
    }

    public static LegalDefaultSpeeds createLegalDefaultSpeeds() {
        SpeedLimitsJson data;
        try {
            data = new ObjectMapper().readValue(MaxSpeedCalculator.class.getResource("legal_default_speeds.json"), SpeedLimitsJson.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
    }

    /**
     * This method sets max_speed values without a value (UNSET_SPEED) to a value depending on
     * the country, road_class etc.
     */
    public void fillMaxSpeed() {
        StopWatch sw = new StopWatch().start();
        List<Map<String, String>> relTags = new ArrayList<>();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            // ISO 3166-1 alpha-2 code optionally concatenated with a ISO 3166-2 code, e.g. "DE", "US" or "BE-VLG"
            String countryCode = iter.get(countryEnumEncodedValue).getAlpha2();
            Map<String, String> tags = new HashMap<>();
            tags.put("highway", iter.get(roadClassEnc).toString());
            if (iter.get(roundaboutEnc)) tags.put("junction", "roundabout");

            double currentCarMax = iter.get(maxSpeedEnc);
            if (currentCarMax == MaxSpeed.UNSET_SPEED) {
                LegalDefaultSpeeds.Result result = defaultSpeeds.getSpeedLimits(countryCode, tags, relTags, (name, eval) -> {
                    if (eval.invoke()) return true;
                    if ("urban".equals(name))
                        return iter.get(urbanDensityEnc) != UrbanDensity.RURAL;
                    if ("rural".equals(name))
                        return iter.get(urbanDensityEnc) == UrbanDensity.RURAL;
                    return false;
                });
                if (result != null) {
                    double resultCarMax = OSMValueExtractor.stringToKmh(result.getTags().get("maxspeed"));
                    if (!Double.isNaN(resultCarMax)) iter.set(maxSpeedEnc, resultCarMax);
                }
            }
        }

        LoggerFactory.getLogger(getClass()).info("filled max_speed from LegalDefaultSpeeds, took: " + sw.stop().getSeconds());
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

        @Nullable
        @Override
        public String getFilter() {
            return filter;
        }

        @Nullable
        @Override
        public String getFuzzyFilter() {
            return fuzzyFilter;
        }

        @Nullable
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

        @Nullable
        @Override
        public String getName() {
            return name;
        }

        @NotNull
        @Override
        public Map<String, String> getTags() {
            return tags;
        }
    }

}
