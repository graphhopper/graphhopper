package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;

public class FootAverageSpeedParser extends AbstractAverageSpeedParser implements TagParser {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "foot"))),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY));
    }

    protected FootAverageSpeedParser(DecimalEncodedValue speedEnc, DecimalEncodedValue ferrySpeedEnc) {
        super(speedEnc, ferrySpeedEnc);

        routeMap.put(INTERNATIONAL, UNCHANGED.getValue());
        routeMap.put(NATIONAL, UNCHANGED.getValue());
        routeMap.put(REGIONAL, UNCHANGED.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                double ferrySpeed = FerrySpeedCalculator.minmax(ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess), avgSpeedEnc);
                setSpeed(false, edgeId, edgeIntAccess, ferrySpeed);
                if (avgSpeedEnc.isStoreTwoDirections())
                    setSpeed(true, edgeId, edgeIntAccess, ferrySpeed);
            }
            if (!way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return;
        }

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            setSpeed(false, edgeId, edgeIntAccess, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeId, edgeIntAccess, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
        } else {
            setSpeed(false, edgeId, edgeIntAccess, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeId, edgeIntAccess, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
        }
    }
}
