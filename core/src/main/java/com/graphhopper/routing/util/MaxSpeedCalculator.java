package com.graphhopper.routing.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.DefaultMaxSpeedParser;
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.StopWatch;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;
import de.westnordost.osm_legal_default_speeds.RoadType;
import de.westnordost.osm_legal_default_speeds.RoadTypeFilter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MaxSpeedCalculator {

    private final DefaultMaxSpeedParser parser;
    private final LegalDefaultSpeeds defaultSpeeds;
    private EdgeIntAccess internalMaxSpeedStorage;
    private DecimalEncodedValue ruralMaxSpeedEnc;
    private DecimalEncodedValue urbanMaxSpeedEnc;
    private DataAccess dataAccess;

    public MaxSpeedCalculator(LegalDefaultSpeeds defaultSpeeds) {
        this.defaultSpeeds = defaultSpeeds;
        parser = new DefaultMaxSpeedParser(defaultSpeeds);
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

        // pre-converts kmh, mph and "walk" into kmh
        convertMaxspeed(data.speedLimitsByCountryCode.entrySet());

        LegalDefaultSpeeds speeds = new LegalDefaultSpeeds(data.roadTypesByName, data.speedLimitsByCountryCode);
        return speeds;
    }

    private static void convertMaxspeed(Set<Map.Entry<String, List<RoadTypeImpl>>> entrySet) {
        for (Map.Entry<String, List<RoadTypeImpl>> entry : entrySet) {
            for (RoadTypeImpl roadType : entry.getValue()) {
                Map<String, String> newTags = new HashMap<>(roadType.getTags().size());
                for (Map.Entry<String, String> tags : roadType.getTags().entrySet()) {
                    // note, we could remove conditional tags here to reduce load a bit at import

                    if ("maxspeed".equals(tags.getKey())
                            || "maxspeed:advisory".equals(tags.getKey())) {
                        double tmp = OSMValueExtractor.stringToKmh(tags.getValue());
                        if (tmp == MaxSpeed.UNSET_SPEED || tmp == OSMValueExtractor.MAXSPEED_NONE)
                            throw new IllegalStateException("illegal maxspeed " + tags.getValue());
                        newTags.put(tags.getKey(), "" + Math.round(tmp));
                    }
                }
                roadType.setTags(newTags);
            }
        }
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

    public TagParser getParser() {
        return parser;
    }

    public void createDataAccessForParser(Directory directory) {
        this.dataAccess = directory.create("max_speed_storage_tmp").create(1000);
        this.internalMaxSpeedStorage = createMaxSpeedStorage(this.dataAccess);
        this.ruralMaxSpeedEnc = new DecimalEncodedValueImpl("tmp_rural", 7, 0, 2, false, false, true);
        this.urbanMaxSpeedEnc = new DecimalEncodedValueImpl("tmp_urban", 7, 0, 2, false, false, true);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        ruralMaxSpeedEnc.init(config);
        urbanMaxSpeedEnc.init(config);
        if (config.getRequiredBytes() > 2)
            throw new IllegalStateException("bytes are not sufficient " + config.getRequiredBytes());

        parser.init(ruralMaxSpeedEnc, urbanMaxSpeedEnc, internalMaxSpeedStorage);
    }

    /**
     * This method sets max_speed values where the value is UNSET_SPEED to a value determined by
     * the default speed library which is country-dependent.
     */
    public void fillMaxSpeed(Graph graph, EncodingManager em) {
        // In DefaultMaxSpeedParser and in OSMMaxSpeedParser we don't have the rural/urban info,
        // but now we have and can fill the country-dependent max_speed value where missing.
        EnumEncodedValue<UrbanDensity> udEnc = em.getEnumEncodedValue(UrbanDensity.KEY, UrbanDensity.class);
        fillMaxSpeed(graph, em, edge -> edge.get(udEnc) != UrbanDensity.RURAL);
    }

    public void fillMaxSpeed(Graph graph, EncodingManager em, Function<EdgeIteratorState, Boolean> isUrbanDensityFun) {
        DecimalEncodedValue maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);
        BooleanEncodedValue maxSpeedEstEnc = em.getBooleanEncodedValue(MaxSpeedEstimated.KEY);

        StopWatch sw = new StopWatch().start();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            double fwdMaxSpeedPureOSM = iter.get(maxSpeedEnc);
            double bwdMaxSpeedPureOSM = iter.getReverse(maxSpeedEnc);

            // skip speeds-library if max_speed is known for both directions
            if (fwdMaxSpeedPureOSM != MaxSpeed.UNSET_SPEED
                    && bwdMaxSpeedPureOSM != MaxSpeed.UNSET_SPEED) continue;

            double maxSpeed = isUrbanDensityFun.apply(iter)
                    ? urbanMaxSpeedEnc.getDecimal(false, iter.getEdge(), internalMaxSpeedStorage)
                    : ruralMaxSpeedEnc.getDecimal(false, iter.getEdge(), internalMaxSpeedStorage);
            if (maxSpeed != MaxSpeed.UNSET_SPEED) {
                if (maxSpeed == 0) {
                    // TODO fix properly: RestrictionSetter adds artificial edges for which
                    //  we didn't set the speed in DefaultMaxSpeedParser, #2914
                    iter.set(maxSpeedEnc, MaxSpeed.UNSET_SPEED, MaxSpeed.UNSET_SPEED);
                } else {
                    iter.set(maxSpeedEnc,
                            fwdMaxSpeedPureOSM == MaxSpeed.UNSET_SPEED ? maxSpeed : fwdMaxSpeedPureOSM,
                            bwdMaxSpeedPureOSM == MaxSpeed.UNSET_SPEED ? maxSpeed : bwdMaxSpeedPureOSM);
                    iter.set(maxSpeedEstEnc, true);
                }
            }
        }

        LoggerFactory.getLogger(getClass()).info("max_speed_calculator took: " + sw.stop().getSeconds());
    }

    public void close() {
        dataAccess.close();
    }

    public void checkEncodedValues(EncodingManager encodingManager) {
        if (!encodingManager.hasEncodedValue(Country.KEY))
            throw new IllegalArgumentException("max_speed_calculator needs country");
        if (!encodingManager.hasEncodedValue(UrbanDensity.KEY))
            throw new IllegalArgumentException("max_speed_calculator needs urban_density");
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
