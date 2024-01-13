package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Barrier;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.List;
import java.util.Map;

public class OSMBarrierParser implements TagParser {
    protected final EnumEncodedValue<Barrier> barrierEnc;

    public OSMBarrierParser(EnumEncodedValue<Barrier> barrierEnc) {
        this.barrierEnc = barrierEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        if (way.hasTag("gh:barrier_edge")) return;
        List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
        if (nodeTags == null)
            return;

        Barrier barrierVal = Barrier.MISSING;
        for (Map<String, Object> tags : nodeTags) {
            String barrierStr = (String) tags.get("barrier");
            if (!Helper.isEmpty(barrierStr)) {
                Barrier curr = Barrier.find(barrierStr);
                if (curr == Barrier.MISSING) {
                    if (barrierStr.equals("kissing_gate")) barrierVal = Barrier.GATE;
                    else if (barrierStr.equals("swing_gate")) barrierVal = Barrier.LIFT_GATE;
                    else if (barrierStr.equals("turnstile")) barrierVal = Barrier.STILE;
                } else if (curr.ordinal() > barrierVal.ordinal())
                    barrierVal = curr;
            }
        }

        barrierEnc.setEnum(false, edgeId, edgeIntAccess, barrierVal);
    }
}
