package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

public class FootAverageSpeedParser extends AbstractAverageSpeedParser implements TagParser {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    // larger value required - ferries are faster than pedestrians
    static final int FERRY_SPEED = 15;
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "foot"))));
    }

    protected FootAverageSpeedParser(DecimalEncodedValue speedEnc) {
        super(speedEnc, speedEnc.getNextStorableValue(FERRY_SPEED));
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
                double ferrySpeed = ferrySpeedCalc.getSpeed(way);
                setSpeed(edgeId, edgeIntAccess, true, true, ferrySpeed);
            }
            if (!way.hasTag("railway", "platform") && !way.hasTag("man_made", "pier"))
                return;
        }

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            setSpeed(edgeId, edgeIntAccess, true, true, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
        } else {
            setSpeed(edgeId, edgeIntAccess, true, true, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
        }
    }

    void setSpeed(int edgeId, EdgeIntAccess edgeIntAccess, boolean fwd, boolean bwd, double speed) {
        if (speed > getMaxSpeed())
            speed = getMaxSpeed();
        if (fwd)
            avgSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, speed);
        if (bwd && avgSpeedEnc.isStoreTwoDirections())
            avgSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, speed);
    }
}
