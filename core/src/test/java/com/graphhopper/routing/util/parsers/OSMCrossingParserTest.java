package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMCrossingParserTest {

    private OSMCrossingParser parser;
    private EnumEncodedValue<Crossing> crossingEV;

    @BeforeEach
    public void setup() {
        crossingEV = new EnumEncodedValue<>(Crossing.KEY, Crossing.class);
        crossingEV.init(new EncodedValue.InitializerConfig());
        parser = new OSMCrossingParser(crossingEV);
    }

    @Test
    public void testRailway() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("railway", "level_crossing").toMap()), null);
        assertEquals(Crossing.RAILWAY, crossingEV.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testSignals() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("crossing", "traffic_signals").toMap()), null);
        assertEquals(Crossing.TRAFFIC_SIGNALS, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing:signals", "yes").toMap()), null);
        assertEquals(Crossing.TRAFFIC_SIGNALS, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing:signals", "no").toMap()), null);
        assertEquals(Crossing.UNMARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testMarked() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, createReader(new HashMap<>()), null);
        assertEquals(Crossing.MISSING, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("highway", "crossing").toMap()), null);
        assertEquals(Crossing.UNMARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing", "marked").toMap()), null);
        assertEquals(Crossing.MARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing:markings", "yes").toMap()), null);
        assertEquals(Crossing.MARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing:markings", "no").toMap()), null);
        assertEquals(Crossing.UNMARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));

        parser.handleWayTags(edgeId, edgeIntAccess = new ArrayEdgeIntAccess(1),
                createReader(new PMap().putObject("crossing:signals", "no").putObject("crossing:markings", "yes").toMap()), null);
        assertEquals(Crossing.MARKED, crossingEV.getEnum(false, edgeId, edgeIntAccess));
    }

    ReaderWay createReader(Map<String, Object> map) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("node_tags", Collections.singletonList(map));
        return way;
    }

}