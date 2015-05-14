package com.graphhopper.reader.osgb.dpn.additionalRights;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 *
 * Description: A link part of a Recreational Route
 *
 * Confirmed Allowable users: Pedestrians
 *
 * @author phopkins
 *
 */
public class AdoptedByRecreationalRoute extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("foot", "yes");
    }

}
