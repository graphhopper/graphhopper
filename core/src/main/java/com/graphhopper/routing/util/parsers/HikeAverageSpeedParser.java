package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.BEST;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

public class HikeAverageSpeedParser extends FootAverageSpeedParser {

    public HikeAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "hike"))));
    }

    protected HikeAverageSpeedParser(DecimalEncodedValue speedEnc) {
        super(speedEnc);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, VERY_NICE.getValue());

        // hiking allows all sac_scale values
        allowedSacScale.add("alpine_hiking");
        allowedSacScale.add("demanding_alpine_hiking");
        allowedSacScale.add("difficult_alpine_hiking");
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way) {
        super.handleWayTags(edgeId, intAccess, way);
        applyWayTags(way, edgeId, intAccess);
    }

    public void applyWayTags(ReaderWay way, int edgeId, IntAccess intAccess) {
        PointList pl = way.getTag("point_list", null);
        if (pl == null)
            throw new IllegalArgumentException("The artificial point_list tag is missing");
        if (!pl.is3D())
            return;

        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
            // do not change speed
            // note: although tunnel can have a difference in elevation it is unlikely that the elevation data is correct for a tunnel
            return;

        // Decrease the speed for ele increase (incline), and slightly decrease the speed for ele decrease (decline)
        double prevEle = pl.getEle(0);
        if (!way.hasTag("edge_distance"))
            throw new IllegalArgumentException("The artificial edge_distance tag is missing");
        double fullDistance = way.getTag("edge_distance", 0d);

        // for short edges an incline makes no sense and for 0 distances could lead to NaN values for speed, see #432
        if (fullDistance < 2)
            return;

        double eleDelta = Math.abs(pl.getEle(pl.size() - 1) - prevEle);
        double slope = eleDelta / fullDistance;

        // see #1679 => v_hor=4.5km/h for horizontal speed; v_vert=2*0.5km/h for vertical speed (assumption: elevation < edge distance/4.5)
        // s_3d/v=h/v_vert + s_2d/v_hor => v = s_3d / (h/v_vert + s_2d/v_hor) = sqrt(s²_2d + h²) / (h/v_vert + s_2d/v_hor)
        // slope=h/s_2d=~h/2_3d              = sqrt(1+slope²)/(slope+1/4.5) km/h
        // maximum slope is 0.37 (Ffordd Pen Llech)
        double newSpeed = Math.sqrt(1 + slope * slope) / (slope + 1 / 5.4);
        avgSpeedEnc.setDecimal(false, edgeId, intAccess, Helper.keepIn(newSpeed, 1, 5));
    }


}
