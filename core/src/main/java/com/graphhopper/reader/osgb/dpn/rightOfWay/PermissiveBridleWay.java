package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Created by sadam on 13/02/15.
 */
public class PermissiveBridleWay extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("highway", "bridleway");
        way.setTag("horse", "permissive");
        way.setTag("bicycle", "permissive");
        way.setTag("foot", "permissive");
    }

}
