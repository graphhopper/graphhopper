package com.graphhopper.util.details;

import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.SpatialRuleId;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.util.EdgeIteratorState;

public class SpatialRuleDetails extends AbstractPathDetailsBuilder {

    private final IntEncodedValue ev;
    private final SpatialRuleLookup spatialRuleLookup;
    private int curRuleIndex = -1;
    private String curSpatialRuleId;

    public SpatialRuleDetails(IntEncodedValue ev, SpatialRuleLookup spatialRuleLookup) {
        super(SpatialRuleId.KEY);
        this.ev = ev;
        this.spatialRuleLookup = spatialRuleLookup;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int index = edge.get(ev);
        if (index != curRuleIndex) {
            this.curRuleIndex = index;
            if (this.curRuleIndex == 0) {
                this.curSpatialRuleId = null;
            } else {
                this.curSpatialRuleId = spatialRuleLookup.getRules().get(index-1).getId();
            }
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return curSpatialRuleId;
    }
}
