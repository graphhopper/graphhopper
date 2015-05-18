package com.graphhopper.reader.osgb.dpn.potentialHazards;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.AbstractOsDpnOsmAttibuteMappingVisitor;

/**
 * Created by sadam on 13/02/15.
 */
public class Sand extends AbstractOsDpnOsmAttibuteMappingVisitor {

    @Override
    protected void applyAttributes(Way way) {
        setOrAppendTag(way, "natural", "sand");
    }

}
