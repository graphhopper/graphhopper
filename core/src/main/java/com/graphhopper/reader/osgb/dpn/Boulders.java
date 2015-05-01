package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 13/02/15.
 */
public class Boulders extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("natural", "boulders");
    }

}
