package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultMaxSpeedParser implements TagParser {
    private final LegalDefaultSpeeds speeds;
    private final DecimalEncodedValue carMaxSpeedEnc;
    private final EdgeIntAccess externalAccess;

    public DefaultMaxSpeedParser(LegalDefaultSpeeds speeds, DecimalEncodedValue carMaxSpeedEnc, EdgeIntAccess externalAccess) {
        this.speeds = speeds;
        this.carMaxSpeedEnc = carMaxSpeedEnc;
        this.externalAccess = externalAccess;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess _ignoreAccess, ReaderWay way, IntsRef relationFlags) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));

        if (Double.isNaN(maxSpeed)) {
            Country country = way.getTag("country", null);
            if (country != null) {
                LegalDefaultSpeeds.Result result = speeds.getSpeedLimits(country.getAlpha2(),
                        fixType(way.getTags()), Collections.emptyList(), (name, eval) -> eval.invoke());
                if (result != null)
                    maxSpeed = OSMValueExtractor.stringToKmh(result.getTags().get("maxspeed"));
            }
        }

        carMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, maxSpeed);
    }

    Map<String, String> fixType(Map<String, Object> tags) {
        Map<String, String> map = new HashMap<>(tags.size());
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String)
                map.put(entry.getKey(), (String) entry.getValue());
        }
        return map;
    }
}
