package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;

import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;

public class FootAverageSpeedParser extends AbstractAverageSpeedParser implements TagParser {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("foot")),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY));
    }

    public FootAverageSpeedParser(DecimalEncodedValue speedEnc, DecimalEncodedValue ferrySpeedEnc) {
        super(speedEnc, ferrySpeedEnc);

        routeMap.put(INTERNATIONAL, UNCHANGED.getValue());
        routeMap.put(NATIONAL, UNCHANGED.getValue());
        routeMap.put(REGIONAL, UNCHANGED.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way)) {
                double ferrySpeed = FerrySpeedCalculator.minmax(ferrySpeedEnc.getDecimal(false, edgeId, edgeAccess), avgSpeedEnc);
                setSpeed(false, edgeId, edgeAccess, ferrySpeed);
                if (avgSpeedEnc.isStoreTwoDirections())
                    setSpeed(true, edgeId, edgeAccess, ferrySpeed);
            }
            if (!way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return;
        }

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            setSpeed(false, edgeId, edgeAccess, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeId, edgeAccess, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
        } else {
            setSpeed(false, edgeId, edgeAccess, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeId, edgeAccess, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
        }
    }
}
