package com.graphhopper.routing.bwdcompat;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionAnnotation;

public interface AnnotationAccessor {
    InstructionAnnotation get(EdgeIteratorState edge);
}
