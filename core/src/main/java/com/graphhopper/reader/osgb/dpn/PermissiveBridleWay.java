package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 13/02/15.
 */
public class PermissiveBridleWay extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("horse", "permissive");
        way.setTag("bicycle", "permissive");
    }

}
