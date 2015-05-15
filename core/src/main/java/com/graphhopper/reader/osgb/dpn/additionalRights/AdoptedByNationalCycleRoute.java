package com.graphhopper.reader.osgb.dpn.additionalRights;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A link part of the National Cycle Network
 *
 * Confirmed Allowable users: Pedestrians, Cyclists
 *
 * @author phopkins
 *
 */
public class AdoptedByNationalCycleRoute extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        // Assign value to use for priority
        way.setTag("network", "ncn");

        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
    }

}
