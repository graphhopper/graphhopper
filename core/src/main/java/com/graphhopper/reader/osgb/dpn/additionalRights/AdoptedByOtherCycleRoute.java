package com.graphhopper.reader.osgb.dpn.additionalRights;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 *
 * Description: A link part of a Cycle Network that is not part of the National
 * Cycle Network
 *
 * Confirmed Allowable users: Pedestrians, Cyclists
 *
 * @author phopkins
 *
 */
public class AdoptedByOtherCycleRoute extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
    }

}
