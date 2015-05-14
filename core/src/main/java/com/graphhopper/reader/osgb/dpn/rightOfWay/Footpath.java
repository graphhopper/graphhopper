package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A route where there is a right to travel on foot.
 *
 * Confirmed Allowable users: Pedestrians
 *
 * Created by sadam on 13/02/15.
 */
public class Footpath extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("designation", "public_footpath");
        way.setTag("highway", "footway");
        way.setTag("foot", "yes");
    }

}
