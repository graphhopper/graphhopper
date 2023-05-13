package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;

import java.util.Collections;
import java.util.Map;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;

public class DefaultMaxSpeedParser implements TagParser {
    private final LegalDefaultSpeeds speeds;
    private final DecimalEncodedValue ruralMaxSpeedEnc;
    private final DecimalEncodedValue urbanMaxSpeedEnc;
    private final EdgeIntAccess externalAccess;

    public DefaultMaxSpeedParser(LegalDefaultSpeeds speeds, DecimalEncodedValue ruralMaxSpeedEnc,
                                 DecimalEncodedValue urbanMaxSpeedEnc, EdgeIntAccess externalAccess) {
        this.speeds = speeds;
        this.ruralMaxSpeedEnc = ruralMaxSpeedEnc;
        this.urbanMaxSpeedEnc = urbanMaxSpeedEnc;
        this.externalAccess = externalAccess;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess _ignoreAccess, ReaderWay way, IntsRef relationFlags) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        Integer ruralSpeedInt = null, urbanSpeedInt = null;
        if (Double.isNaN(maxSpeed)) {
            Country country = way.getTag("country", null);
            if (country != null) {
                // currently the library is fine with objects => force the type instead of copying
                Map<String, String> tags = (Map) way.getTags();
                LegalDefaultSpeeds.Result result = speeds.getSpeedLimits(country.getAlpha2(),
                        tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "rural".equals(name));
                if (result != null) ruralSpeedInt = parseInt(result.getTags().get("maxspeed"));

                result = speeds.getSpeedLimits(country.getAlpha2(),
                        tags, Collections.emptyList(), (name, eval) -> eval.invoke() || "urban".equals(name));
                if (result != null) urbanSpeedInt = parseInt(result.getTags().get("maxspeed"));
            }
        }

        urbanMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, urbanSpeedInt == null ? UNSET_SPEED : urbanSpeedInt);
        ruralMaxSpeedEnc.setDecimal(false, edgeId, externalAccess, ruralSpeedInt == null ? UNSET_SPEED : ruralSpeedInt);
    }

    public static Integer parseInt(String str) {
        if (str == null) return null;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
