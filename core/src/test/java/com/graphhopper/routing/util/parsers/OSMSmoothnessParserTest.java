package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMSmoothnessParserTest {
    private EnumEncodedValue<Smoothness> smoothnessEnc;
    private OSMSmoothnessParser parser;

    @BeforeEach
    public void setUp() {
        smoothnessEnc = Smoothness.create();
        smoothnessEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSmoothnessParser(smoothnessEnc);
    }

    @Test
    public void testSimpleTags() {
        BytesRef relFlags = new BytesRef(8);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Smoothness.MISSING, smoothnessEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("smoothness", "bad");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Smoothness.BAD, smoothnessEnc.getEnum(false, edgeId, edgeAccess));
        assertTrue(Smoothness.BAD.ordinal() < Smoothness.VERY_BAD.ordinal());
    }
}
