package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Created by sadam on 16/02/15.
 */
public class OtherRouteWithPublicAccess extends AbstractOsDpnOsmAttibuteMappingVisitor {
    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("foot", "yes");
    }
}
