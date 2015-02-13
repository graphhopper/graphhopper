package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 13/02/15.
 */
public interface OsDpnOsmAttributeMappingVisitor {
    void visitWayAttribute(String attributeValue, Way way);
}
