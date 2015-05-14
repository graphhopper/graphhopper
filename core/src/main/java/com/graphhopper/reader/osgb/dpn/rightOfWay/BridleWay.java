package com.graphhopper.reader.osgb.dpn.rightOfWay;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Description: A route where there is a right to travel on foot, on horseback, to lead a horse and to ride a bicycle
 *
 * Confirmed Allowable users: Pedestrians, Horses, Cyclists
 *
 * Created by sadam on 13/02/15.
 */
public class BridleWay extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way)
    {
        way.setTag("designation", "public_bridleway");
        way.setTag("highway", "bridleway");

        way.setTag("foot", "yes");
        way.setTag("horse", "yes");
        way.setTag("bicycle", "yes");
    }

}
