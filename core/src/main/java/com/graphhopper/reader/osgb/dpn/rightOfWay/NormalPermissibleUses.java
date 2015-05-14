package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: No known restrictions on pedestrians, cyclists or horses being ridden. Restrictions may apply to vehicles.
 *
 * Confirmed Allowable users: Pedestrians, Horses, Cyclists – used for public roads without a restriction
 *
 * @author phopkins
 *
 */
public class NormalPermissibleUses  extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("foot", "yes");
        way.setTag("horse", "yes");
        way.setTag("bicycle", "yes");
    }

}
