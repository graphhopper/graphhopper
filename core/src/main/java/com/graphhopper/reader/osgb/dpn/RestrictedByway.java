package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 13/02/15.
 */
public class RestrictedByway extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("designation", "restricted_byway");
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "no");
    }

}
