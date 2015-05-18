package com.graphhopper.reader.osgb.dpn.additionalRights;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 *
 * Description: A link within Access Land
 *
 * Confirmed Allowable users: Pedestrians Note for Private Roads where the only
 * right to use is because the road is in Access Land there may not be a right
 * to use the road itself.
 *
 * @author phopkins
 *
 */
public class WithinAccessLand extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("foot", "yes");
    }

}
