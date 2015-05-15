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
        // Assign value to use for priority. Could be local or regional but we
        // are chosing local cycle network
        way.setTag("network", "lcn");

        way.setTag("bicycle", "yes");
        way.setTag("foot", "yes");
    }

}
