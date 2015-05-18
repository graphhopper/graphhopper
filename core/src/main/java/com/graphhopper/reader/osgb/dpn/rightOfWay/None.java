package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A route where no permissive or conventional right of way has been identified. Use may be allowed if the route has been identified as a cycle route,
 * see NationalCycleRoute, OtherCycleRoute, part of a RecreationalRoute or as lying within an area of Access Land.
 *
 * Confirmed Allowable users: See NationalCycleRoute, OtherCycleRoute, part of a RecreationalRoute or as lying within an area of Access Land.
 *
 * Created by sadam on 16/02/15.
 */
public class None extends AbstractOsDpnOsmAttibuteMappingVisitor {

    /**
     * This code is written with the assumption that any further DPN elements that will override the bicycle=no will be processed after the rightOfWay element.
     * This assumption is based on the current DPN xml structure. If this were to change in the future we would need to rethink how we process this rightOfWay.
     */
    @Override
    protected void applyAttributes(Way way) {
        way.setTag("foot", "no");
        way.setTag("bicycle", "no");
        way.setTag("horse", "no");
    }
}
