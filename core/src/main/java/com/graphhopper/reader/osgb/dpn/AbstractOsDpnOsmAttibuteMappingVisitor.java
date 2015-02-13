package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 13/02/15.
 */
public abstract class AbstractOsDpnOsmAttibuteMappingVisitor implements OsDpnOsmAttributeMappingVisitor {
    protected String visitorName = this.getClass().getSimpleName();

    @Override
    public void visitWayAttribute(String attributeValue, Way way) {
        System.out.println(visitorName);
        if (visitorName.equals(attributeValue.replaceAll(" ", ""))) {
            applyAttributes(way);
        }
    }

    protected abstract void applyAttributes(Way way);
}
