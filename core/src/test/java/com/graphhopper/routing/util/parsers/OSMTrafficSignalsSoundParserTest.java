package com.graphhopper.routing.util.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.TrafficSignalsSound;
import com.graphhopper.storage.IntsRef;

public class OSMTrafficSignalsSoundParserTest {
    private EnumEncodedValue<TrafficSignalsSound> trafficSignalsSoundEnc;
    private OSMTrafficSignalsSoundParser parser;

    @BeforeEach
    public void setUp() {
        trafficSignalsSoundEnc = TrafficSignalsSound.create();
        trafficSignalsSoundEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTrafficSignalsSoundParser(trafficSignalsSoundEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("crossing", "traffic_signals");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(TrafficSignalsSound.MISSING,
                        trafficSignalsSoundEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("traffic_signals:sound", "locate");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(TrafficSignalsSound.LOCATE,
                        trafficSignalsSoundEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}