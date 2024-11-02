package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.MaxSpeed.UNLIMITED_SIGN_SPEED;
import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;
import static com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.stringToKmh;

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
        double maxSpeed = Math.max(OSMMaxSpeedParser.parseMaxSpeed(way, false), OSMMaxSpeedParser.parseMaxSpeed(way, true));
        Integer ruralSpeedInt = null, urbanSpeedInt = null;
        if (maxSpeed == UNSET_SPEED) {
            Country country = way.getTag("country", Country.MISSING);
            State state = way.getTag("country_state", State.MISSING);
            if (country != Country.MISSING) {
                String code = state == State.MISSING ? country.getAlpha2() : state.getStateCode();
                Map<String, String> tags = filter(way.getTags());
                // Workaround for GBR. Default is used for "urban" but ignored for "rural".
                if (country == Country.GBR) tags.put("lit", "yes");

                // with computeIfAbsent we calculate the expensive hashCode of the key only once
                Result result = cache.computeIfAbsent(tags, (key) -> {
                    Result internRes = new Result();
                    LegalDefaultSpeeds.Result tmpResult = speeds.getSpeedLimits(code,
                            tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "rural".equals(name));
                    if (tmpResult != null) {
                        internRes.rural = parseInt(tmpResult.getTags().get("maxspeed"));
                        if (internRes.rural == null && "130".equals(tmpResult.getTags().get("maxspeed:advisory")))
                            internRes.rural = (int) UNLIMITED_SIGN_SPEED;
                    }

                    tmpResult = speeds.getSpeedLimits(code,
                            tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "urban".equals(name));
                    if (tmpResult != null) {
                        internRes.urban = parseInt(tmpResult.getTags().get("maxspeed"));
                        if (internRes.urban == null && "130".equals(tmpResult.getTags().get("maxspeed:advisory")))
                            internRes.urban = (int) UNLIMITED_SIGN_SPEED;
                    }
                    return internRes;
                });

                ruralSpeedInt = result.rural;
                urbanSpeedInt = result.urban;
            }
        }

        urbanMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, urbanSpeedInt == null ? UNSET_SPEED : urbanSpeedInt);
        ruralMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, ruralSpeedInt == null ? UNSET_SPEED : ruralSpeedInt);
    }

    private Map<String, String> filter(Map<String, Object> tags) {
        Map<String, String> map = new HashMap<>(tags.size());
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey();
            if (speeds.isRelevantTagKey(key)
                    || key.equals("country")
                    || key.equals("country_state")
                    // the :conditional tags are not yet necessary for us and expensive in the speeds library
                    // see https://github.com/westnordost/osm-legal-default-speeds/issues/7
                    || key.startsWith("maxspeed:") && !key.endsWith(":conditional"))
                map.put(key, entry.getValue().toString());
        }
        return map;
    }

    private static class Result {
        Integer urban, rural;
    }

    private final int SIZE = 3_000;
    private final Map<Map<String, String>, Result> cache = new LinkedHashMap<Map<String, String>, Result>(SIZE + 1, .75F, true) {
        public boolean removeEldestEntry(Map.Entry eldest) {
            return size() > SIZE;
        }
    };

    private static Integer parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
