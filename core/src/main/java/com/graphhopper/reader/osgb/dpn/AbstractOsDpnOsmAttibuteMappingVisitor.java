package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;
import com.graphhopper.routing.util.OsFlagUtils;

/**
 * Created by sadam on 13/02/15.
 */
public abstract class AbstractOsDpnOsmAttibuteMappingVisitor implements OsDpnOsmAttributeMappingVisitor {
    protected String visitorName = this.getClass().getSimpleName().toLowerCase();

    @Override
    public boolean visitWayAttribute(String attributeValue, Way way) {
        if (visitorName.equals(attributeValue)) {
            applyAttributes(way);
            return true;
        }
        return false;
    }

    protected abstract void applyAttributes(Way way);

    protected void setOrAppendTag(Way way, String key, String value) {
        OsFlagUtils.setOrAppendTag(way, key, value);
    }
}
