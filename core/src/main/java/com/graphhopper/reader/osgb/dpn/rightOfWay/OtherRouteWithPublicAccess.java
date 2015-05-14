package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A route that is the responsibility of local highway authorities and maintained at public expense.
 * All ORPAs have rights for pedestrians. Beyond that, any particular ORPA may, or may not, have rights for cyclists and equestrians,
 * and may or may not have rights for motor vehicles. Other Routes with Public Access (ORPA) are sometimes known as unclassified
 * unsurfaced roads (or unclassified country roads).
 *
 * Confirmed Allowable users: Pedestrians *
 *
 * * Other rights may exist; these will need to be determined from the local Highway Authority
 *
 * Created by sadam on 16/02/15.
 */
public class OtherRouteWithPublicAccess extends AbstractOsDpnOsmAttibuteMappingVisitor {
    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("foot", "yes");
    }
}
