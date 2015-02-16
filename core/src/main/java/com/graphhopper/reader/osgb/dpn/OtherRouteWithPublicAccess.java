package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 16/02/15.
 */
public class OtherRouteWithPublicAccess extends AbstractOsDpnOsmAttibuteMappingVisitor {
    @Override
    protected void applyAttributes(Way way) {
        way.setTag("foot", "yes");
    }
}
