package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;

public abstract class BaseClass implements EdgeToValueEntry {
    public static EnumEncodedValue<RoadClass> road_class;

    // TODO NOW: how can we avoid replaceAll?
//    Object e(EnumEncodedValue enc) {
//        return edge.get(enc);
//    }
}
