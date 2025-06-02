package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OSMBarrierParserTest {
    private OSMBarrierParser parser;
    private EnumEncodedValue<Barrier> barrierEnc;
    private BooleanEncodedValue accessEnc;

    @BeforeEach
    public void setup() {
        barrierEnc = new EnumEncodedValue<>(Barrier.KEY, Barrier.class);
        barrierEnc.init(new EncodedValue.InitializerConfig());
        accessEnc = new SimpleBooleanEncodedValue("barrier_access", true);
        accessEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMBarrierParser(accessEnc, barrierEnc);
    }

    @Test
    public void testBarrierEdge() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(1);
        way.setTag("gh:barrier_edge", true);
        
        Map<String, Object> tags = Collections.singletonMap("barrier", "gate");
        way.setTag("node_tags", Collections.singletonList(tags));
        
        parser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(Barrier.GATE, barrierEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testBarrierNode() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        assertTrue(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "unknown");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        assertTrue(parser.isBarrier(node));
    }
} 
