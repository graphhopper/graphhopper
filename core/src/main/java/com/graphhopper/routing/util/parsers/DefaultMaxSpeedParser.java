package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;

import java.util.*;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;

public class DefaultMaxSpeedParser implements TagParser {
    private final LegalDefaultSpeeds speeds;
    private DecimalEncodedValue ruralMaxSpeedEnc;
    private DecimalEncodedValue urbanMaxSpeedEnc;
    private EdgeIntAccess externalAccess;

    public DefaultMaxSpeedParser(LegalDefaultSpeeds speeds) {
        this.speeds = speeds;
    }

    public void init(DecimalEncodedValue ruralMaxSpeedEnc, DecimalEncodedValue urbanMaxSpeedEnc, EdgeIntAccess externalAccess) {
        this.ruralMaxSpeedEnc = ruralMaxSpeedEnc;
        this.urbanMaxSpeedEnc = urbanMaxSpeedEnc;
        this.externalAccess = externalAccess;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess _ignoreAccess, ReaderWay way, IntsRef relationFlags) {
        if (externalAccess == null)
            throw new IllegalArgumentException("Call init before using " + getClass().getName());
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        Integer ruralSpeedInt = null, urbanSpeedInt = null;
        if (Double.isNaN(maxSpeed)) {
            Country country = way.getTag("country", null);
            if (country != null) {
                Map<String, String> tags = filter(way.getTags());
                // We could also use the Map as cache key But this would be slightly slower, and we would have to keep the "country" pseudo tag.
                String cacheKey = tags.toString() + country;
                Result cachedResult = cache.get(cacheKey);
                if (cachedResult != null) {
                    ruralSpeedInt = cachedResult.rural;
                    urbanSpeedInt = cachedResult.urban;
                } else {
                    LegalDefaultSpeeds.Result result = speeds.getSpeedLimits(country.getAlpha2(),
                            tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "rural".equals(name));
                    if (result != null) ruralSpeedInt = parseInt(result.getTags().get("maxspeed"));

                    result = speeds.getSpeedLimits(country.getAlpha2(),
                            tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "urban".equals(name));
                    if (result != null) urbanSpeedInt = parseInt(result.getTags().get("maxspeed"));
                    if (urbanSpeedInt != null || ruralSpeedInt != null)
                        cache.put(cacheKey, new Result(urbanSpeedInt, ruralSpeedInt));
                }
            }
        }

        urbanMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, urbanSpeedInt == null ? UNSET_SPEED : urbanSpeedInt);
        ruralMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, ruralSpeedInt == null ? UNSET_SPEED : ruralSpeedInt);
    }

    // keys from roadTypesByName in legal_default_speeds.json but ignore relationFilter
    private static final Collection<String> allowedKeys = new HashSet<>(Arrays.asList(
            "abutters", "bicycle_road", "bridge", "cyclestreet",
            "designation", "dual_carriageway", "expressway", "frontage_road",
            "hazard", "highway", "junction",
            "lane_markings", "lanes", "lit",
            "maxweight", "maxgcweight"/*abandoned*/, "motorroad", "oneway", "playground_zone",
            "ref", "restriction", "rural",
            "school_zone", "service", "shoulder", "side_road", "sidewalk", "silver_zone", "surface",
            "tracktype", "tunnel", "width"));

    private static Map<String, String> filter(Map<String, Object> tags) {
        Map<String, String> map = new HashMap<>(tags.size());
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey();
            if (allowedKeys.contains(key)
                    || key.startsWith("shoulder:")
                    || key.startsWith("sidewalk:")
                    || key.startsWith("zone:")
                    // the :conditional tags are not yet necessary for us and expensive in the speeds library
                    // see https://github.com/westnordost/osm-legal-default-speeds/issues/7
                    || key.startsWith("maxspeed:") && !key.endsWith(":conditional"))
                map.put(key, entry.getValue().toString());
        }
        return map;
    }

    private static class Result {
        Integer urban, rural;

        public Result(Integer urban, Integer rural) {
            this.urban = urban;
            this.rural = rural;
        }
    }

    private final int SIZE = 3000;
    private final Map<String, Result> cache = new LinkedHashMap<String, Result>(SIZE + 1, .75F, true) {
        public boolean removeEldestEntry(Map.Entry eldest) {
            return size() > SIZE;
        }
    };

    public static Integer parseInt(String str) {
        if (str == null) return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
