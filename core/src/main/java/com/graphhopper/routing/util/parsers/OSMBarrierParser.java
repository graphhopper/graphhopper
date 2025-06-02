package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Barrier;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.util.PMap;

import java.util.List;
import java.util.Map;
import java.util.Collections;

public class OSMBarrierParser extends AbstractAccessParser {
    private final EnumEncodedValue<Barrier> barrierEnc;

    public OSMBarrierParser(BooleanEncodedValue accessEnc, EnumEncodedValue<Barrier> barrierEnc) {
        super(accessEnc, List.of("barrier"));
        this.barrierEnc = barrierEnc;
        barriers.add("bollard");
        barriers.add("gate");
        barriers.add("lift_gate");
        barriers.add("stile");
        barriers.add("kissing_gate");
        barriers.add("turnstile");
        barriers.add("block");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            if (!nodeTags.isEmpty()) {
                Map<String, Object> tags = nodeTags.get(0);
                String barrierValue = (String) tags.get("barrier");
                if (barrierValue != null) {
                    barrierEnc.setEnum(false, edgeId, edgeIntAccess, Barrier.find(barrierValue));
                }
            }
        }
    }

    @Override
    public boolean isBarrier(ReaderNode node) {
        String barrierValue = node.getTag("barrier");
        if (barrierValue == null)
            return false;
        
        Barrier barrier = Barrier.find(barrierValue);
        if (barrier == Barrier.MISSING)
            return false;
            
        return barriers.contains(barrierValue);
    }
} 
