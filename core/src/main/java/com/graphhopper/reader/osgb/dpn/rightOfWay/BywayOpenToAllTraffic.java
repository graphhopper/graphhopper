package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A highway open to all traffic
 *
 * Confirmed Allowable users: Pedestrians, Horses, Cyclists, Motorised Vehicles
 *
 * Created by sadam on 13/02/15.
 */
public class BywayOpenToAllTraffic extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        way.setTag("designation", "byway_open_to_all_traffic");
        way.setTag("highway", "track");
        way.setTag("foot", "yes");
        way.setTag("horse", "yes");
        way.setTag("bicycle", "yes");
        way.setTag("motor_vehicle", "yes");
    }

}
