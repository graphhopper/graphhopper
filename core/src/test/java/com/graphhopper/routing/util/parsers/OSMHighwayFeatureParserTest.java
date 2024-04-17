package com.graphhopper.routing.util.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.Crossing;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HighwayFeature;
import com.graphhopper.util.PMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OSMHighwayFeatureParserTest {

    private OSMHighwayFeatureParser parser;
    private EnumEncodedValue<HighwayFeature> highwayFeatureEV;

    @BeforeEach
    public void setup() {
        highwayFeatureEV = new EnumEncodedValue<>(HighwayFeature.KEY, HighwayFeature.class);
        highwayFeatureEV.init(new EncodedValue.InitializerConfig());
        parser = new OSMHighwayFeatureParser(highwayFeatureEV);
    }

    @Test
    public void testCrossing() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("highway", "crossing").toMap()), null);
        assertEquals(HighwayFeature.CROSSING, highwayFeatureEV.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testTrafficSignals() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("highway", "traffic_signals").toMap()), null);
        assertEquals(HighwayFeature.TRAFFIC_SIGNALS, highwayFeatureEV.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testStopSigns() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess,
                createReader(new PMap().putObject("highway", "stop").toMap()), null);
        assertEquals(HighwayFeature.STOP, highwayFeatureEV.getEnum(false, edgeId, edgeIntAccess));
    }

    ReaderWay createReader(Map<String, Object> map) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("node_tags", Collections.singletonList(map));
        return way;
    }

}
