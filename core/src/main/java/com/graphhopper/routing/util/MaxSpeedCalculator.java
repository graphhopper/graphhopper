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
import java.util.List;
import java.util.Map;

public class MaxSpeedCalculator {

    private final LegalDefaultSpeeds defaultSpeeds;
    private final EdgeIntAccess internalMaxSpeedStorage;
    private final DecimalEncodedValue ruralMaxSpeedEnc;
    private final DecimalEncodedValue urbanMaxSpeedEnc;
    private final DataAccess dataAccess;

    public MaxSpeedCalculator(LegalDefaultSpeeds defaultSpeeds, Directory directory) {
        this.defaultSpeeds = defaultSpeeds;
        this.dataAccess = directory.create("max_speed_storage_tmp").create(1000);
        this.internalMaxSpeedStorage = createMaxSpeedStorage(this.dataAccess);
        this.ruralMaxSpeedEnc = new DecimalEncodedValueImpl("tmp_rural", 7, 0, 2, false, false, true);
        this.urbanMaxSpeedEnc = new DecimalEncodedValueImpl("tmp_urban", 7, 0, 2, false, false, true);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        ruralMaxSpeedEnc.init(config);
        urbanMaxSpeedEnc.init(config);
        if (config.getRequiredBits() > 16)
            throw new IllegalStateException("bits are not sufficient " + config.getRequiredBits());
    }

    DecimalEncodedValue getRuralMaxSpeedEnc() {
        return ruralMaxSpeedEnc;
    }

    public DecimalEncodedValue getUrbanMaxSpeedEnc() {
        return urbanMaxSpeedEnc;
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
        LegalDefaultSpeeds speeds = new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
//        speeds.setLimitOtherSpeeds(false);
        return speeds;
    }

    /**
     * Creates temporary uni dir max_speed storage that is removed after import.
     */
    private EdgeIntAccess createMaxSpeedStorage(DataAccess dataAccess) {
        return new EdgeIntAccess() {

            public int getInt(int edgeId, int index) {
                dataAccess.ensureCapacity(edgeId * 2L + 2L);
                return dataAccess.getShort(edgeId * 2L);
            }

            public void setInt(int edgeId, int index, int value) {
                dataAccess.ensureCapacity(edgeId * 2L + 2L);
                if (value > Short.MAX_VALUE)
                    throw new IllegalStateException("value too large for short: " + value);
                dataAccess.setShort(edgeId * 2L, (short) value);
            }
        };
    }

    public TagParser createParser() {
        return new DefaultMaxSpeedParser(defaultSpeeds, ruralMaxSpeedEnc, urbanMaxSpeedEnc, internalMaxSpeedStorage);
    }

    /**
     * This method sets max_speed values where the value is UNSET_SPEED to a value determined by
     * the default speed library which is country-dependent.
     */
    public void fillMaxSpeed(Graph graph, EncodingManager em) {
        EnumEncodedValue<UrbanDensity> urbanDensityEnc = em.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity.class);
        DecimalEncodedValue maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);

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

            // In DefaultMaxSpeedParser and in OSMMaxSpeedParser we don't have the rural/urban info,
            // but now we have and can fill the country-dependent max_speed value.
            UrbanDensity urbanDensity = iter.get(urbanDensityEnc);
            if (urbanDensity == UrbanDensity.RURAL) {
                double maxSpeedRuralDefault = ruralMaxSpeedEnc.getDecimal(false, iter.getEdge(), internalMaxSpeedStorage);
                if (maxSpeedRuralDefault != MaxSpeed.UNSET_SPEED)
                    iter.set(maxSpeedEnc, maxSpeedRuralDefault, maxSpeedRuralDefault);
            } else {
                double maxSpeedUrbanDefault = urbanMaxSpeedEnc.getDecimal(false, iter.getEdge(), internalMaxSpeedStorage);
                if (maxSpeedUrbanDefault != MaxSpeed.UNSET_SPEED)
                    iter.set(maxSpeedEnc, maxSpeedUrbanDefault, maxSpeedUrbanDefault);
            }
        }

        LoggerFactory.getLogger(getClass()).info("max_speed_calculator took: " + sw.stop().getSeconds());
    }

    public void close() {
        dataAccess.close();
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
