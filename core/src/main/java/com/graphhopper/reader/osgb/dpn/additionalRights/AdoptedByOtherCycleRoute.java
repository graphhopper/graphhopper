package com.graphhopper.reader.osgb.dpn.additionalRights;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

public class AdoptedByOtherCycleRoute extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
    }

}
