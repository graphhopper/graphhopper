package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMSmoothnessParserTest {
    private EnumEncodedValue<Smoothness> smoothnessEnc;
    private OSMSmoothnessParser parser;

    @BeforeEach
    public void setUp() {
        smoothnessEnc = new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class);
        smoothnessEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSmoothnessParser(smoothnessEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Smoothness.MISSING, smoothnessEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("smoothness", "bad");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Smoothness.BAD, smoothnessEnc.getEnum(false, edgeId, edgeIntAccess));
        assertTrue(Smoothness.BAD.ordinal() < Smoothness.VERY_BAD.ordinal());
    }
}