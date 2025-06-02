package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMBarrierParserTest {

    private OSMBarrierParser parser;
    private EnumEncodedValue<Barrier> barrierEV;

    @BeforeEach
    public void setup() {
        barrierEV = new EnumEncodedValue<>(Barrier.KEY, Barrier.class);
        barrierEV.init(new EncodedValue.InitializerConfig());
        parser = new OSMBarrierParser(barrierEV);
    }

    @Test
    public void testGate() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("barrier", "gate").toMap()), null);
        assertEquals(Barrier.GATE, barrierEV.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("barrier", "kissing_gate").toMap()), null);
        assertEquals(Barrier.GATE, barrierEV.getEnum(false, edgeId, edgeIntAccess));
    }

    ReaderWay createReader(Map<String, Object> map) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("node_tags", Collections.singletonList(map));
        return way;
    }
}
