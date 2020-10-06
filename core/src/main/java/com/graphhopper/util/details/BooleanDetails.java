package com.graphhopper.util.details;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public class BooleanDetails extends AbstractPathDetailsBuilder {

    private final BooleanEncodedValue boolEnc;
    private Boolean boolValue;

    public BooleanDetails(String name, BooleanEncodedValue boolEnc) {
        super(name);
        this.boolEnc = boolEnc;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        boolean tmpVal = edge.get(boolEnc);
        if (boolValue == null || tmpVal != boolValue) {
            this.boolValue = tmpVal;
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.boolValue;
    }
}
