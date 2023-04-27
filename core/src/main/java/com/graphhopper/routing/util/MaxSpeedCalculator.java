package com.graphhopper.routing.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.Graph;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import de.westnordost.osm_legal_default_speeds.RoadType;
import de.westnordost.osm_legal_default_speeds.RoadTypeFilter;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaxSpeedCalculator {

    public static void fillMaxSpeed(Graph graph,
                                    EnumEncodedValue<UrbanDensity> urbanDensityEnc,
                                    EnumEncodedValue<RoadClass> roadClassEnc,
                                    BooleanEncodedValue roadClassLinkEnc,
                                    EnumEncodedValue<Country> countryEnumEncodedValue,
                                    DecimalEncodedValue maxSpeedEnc) {

        SpeedLimitsJson data;
        try {
            data = new ObjectMapper().readValue(MaxSpeedCalculator.class.getResource("legal_default_speeds.json"), SpeedLimitsJson.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LegalDefaultSpeeds spLimit = new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
        List<Map<String, String>> relTags = new ArrayList<>();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            // ISO 3166-1 alpha-2 code optionally concatenated with a ISO 3166-2 code, e.g. "DE", "US" or "BE-VLG"
            String countryCode = iter.get(countryEnumEncodedValue).getAlpha2();
            Map<String, String> tags = new HashMap<>();
            tags.put("highway", iter.get(roadClassEnc).toString());
            double currentCarMax = iter.get(maxSpeedEnc);
            if (currentCarMax == MaxSpeed.UNSET_SPEED) {
                LegalDefaultSpeeds.Result result = spLimit.getSpeedLimits(countryCode, tags, relTags, (name, eval) -> {
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
    }

    public static class SpeedLimitsJson {
        Map<String, String> meta;
        Map<String, RoadTypeFilterImpl> roadTypesByName;
        Map<String, List<RoadTypeImpl>> speedLimitsByCountryCode;
        List<String> warnings;

        public void setMeta(Map<String, String> meta) {
            this.meta = meta;
        }

        public void setRoadTypesByName(Map<String, RoadTypeFilterImpl> roadTypesByName) {
            this.roadTypesByName = roadTypesByName;
        }

        public void setSpeedLimitsByCountryCode(Map<String, List<RoadTypeImpl>> speedLimitsByCountryCode) {
            this.speedLimitsByCountryCode = speedLimitsByCountryCode;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }

    public static class RoadTypeFilterImpl implements RoadTypeFilter {

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
            return null;
        }

        @Nullable
        @Override
        public String getRelationFilter() {
            return null;
        }
    }

    public static class RoadTypeImpl implements RoadType {

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
