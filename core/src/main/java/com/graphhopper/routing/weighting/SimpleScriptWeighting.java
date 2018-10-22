package com.graphhopper.routing.weighting;

import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public interface SimpleScriptWeighting {
    double get(double speed, EdgeIteratorState edge, EnumEncodedValue roadClass, String s1, String s2, String s3, String s4,
               String s5, String s6, String s7, String s8, String s9, String s10, String s11, String s12, String s13,
               String s14, String s15, String s16, String s17, String s18, String s19, String s20, String s21, String s22);
}
