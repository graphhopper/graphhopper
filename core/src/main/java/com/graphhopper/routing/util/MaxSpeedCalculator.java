package com.graphhopper.routing.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.DefaultMaxSpeedParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.DataAccess;
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

public class MaxSpeedCalculator {

    private final LegalDefaultSpeeds defaultSpeeds;
    private final EdgeIntAccess internalMaxSpeedStorage;
    private final DecimalEncodedValue internalMaxSpeedEnc;
    private final DataAccess dataAccess;

    public MaxSpeedCalculator(LegalDefaultSpeeds defaultSpeeds, Directory directory) {
        this.defaultSpeeds = defaultSpeeds;
        this.dataAccess = directory.create("max_speed_storage_tmp").create(1000);
        this.internalMaxSpeedStorage = createMaxSpeedStorage(this.dataAccess);
        this.internalMaxSpeedEnc = new DecimalEncodedValueImpl("tmp", 5, 0, 5, false, false, true);
        internalMaxSpeedEnc.init(new EncodedValue.InitializerConfig());
    }

    DecimalEncodedValue getInternalMaxSpeedEnc() {
        return internalMaxSpeedEnc;
    }

    EdgeIntAccess getInternalMaxSpeedStorage() {
        return internalMaxSpeedStorage;
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
        return new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
    }

    /**
     * Creates temporary uni dir max_speed storage that is removed after import.
     */
    private EdgeIntAccess createMaxSpeedStorage(DataAccess dataAccess) {
        return new EdgeIntAccess() {

            public int getInt(int edgeId, int index) {
                return dataAccess.getByte(edgeId);
            }

            public void setInt(int edgeId, int index, int value) {
                dataAccess.ensureCapacity(edgeId);
                dataAccess.setByte(edgeId, (byte) value);
            }
        };
    }

    public TagParser createParser() {
        return new DefaultMaxSpeedParser(defaultSpeeds, internalMaxSpeedEnc, internalMaxSpeedStorage);
    }

    /**
     * This method sets max_speed values where the value is UNSET_SPEED to a value determined by
     * the default speed library which is country-dependent. The rural max_speed is based on all
     * OSM tags. However, the ciy max_speed is currently only based on the information in the
     * encoded values: road_class, surface, lanes and roundabout.
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

            // This is tricky as it could be a highly customized (car) profile.
            // if (isOneway(iter)) tags.put("oneway", "yes");

            double fwdMaxSpeedPureOSM = iter.get(maxSpeedEnc);
            double bwdMaxSpeedPureOSM = iter.getReverse(maxSpeedEnc);

            // library does not work for the case that forward/backward are different
            if (fwdMaxSpeedPureOSM != bwdMaxSpeedPureOSM) continue;

            // In DefaultMaxSpeedParser and in OSMMaxSpeedParser we don't have the rural/urban info,
            // but now we have and can fill the country-dependent max_speed value.

            UrbanDensity urbanDensity = iter.get(urbanDensityEnc);
            // if RURAL fill gaps of OSM max_speed with lib value (determined while *all* OSM tags were available)
            double maxSpeedRuralDefault = internalMaxSpeedEnc.getDecimal(false, iter.getEdge(), internalMaxSpeedStorage);
            if (maxSpeedRuralDefault != MaxSpeed.UNSET_SPEED && urbanDensity == UrbanDensity.RURAL) {
                iter.set(maxSpeedEnc, maxSpeedRuralDefault, maxSpeedRuralDefault);
            } else {
                // Here either fwd and bwd are UNSET or both have same value.
                // The library does not support forward and backward so do not call it in that case.

                if (fwdMaxSpeedPureOSM != MaxSpeed.UNSET_SPEED)
                    tags.put("maxspeed", "" + Math.round(fwdMaxSpeedPureOSM));

                LegalDefaultSpeeds.Result result = defaultSpeeds.getSpeedLimits(countryCode, tags, relTags, (name, eval) -> {
                    if (eval.invoke()) return true;
                    if ("urban".equals(name))
                        return urbanDensity != UrbanDensity.RURAL;
                    if ("rural".equals(name))
                        return urbanDensity == UrbanDensity.RURAL;
                    return false;
                });
                if (result != null) {
                    Integer max = parseInt(result.getTags().get("maxspeed"));
                    if (max != null)
                        iter.set(maxSpeedEnc, max, max);
                }
            }
        }

        LoggerFactory.getLogger(getClass()).info("max_speed_calculator took: " + sw.stop().getSeconds());
    }

    public void close() {
        dataAccess.close();
    }

    Integer parseInt(String str) {
        if (str == null) return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
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
