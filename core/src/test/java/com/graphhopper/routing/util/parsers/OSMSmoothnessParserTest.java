package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Smoothness;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMSmoothnessParserTest {
    private TagParserManager tpm;
    private EnumEncodedValue<Smoothness> smoothnessEnc;
    private OSMSmoothnessParser parser;

    @BeforeEach
    public void setUp() {
        parser = new OSMSmoothnessParser();
        tpm = new TagParserManager.Builder().add(parser).build();
        smoothnessEnc = tpm.getEnumEncodedValue(Smoothness.KEY, Smoothness.class);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = tpm.createRelationFlags();

        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Smoothness.MISSING, smoothnessEnc.getEnum(false, intsRef));

        readerWay.setTag("smoothness", "bad");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Smoothness.BAD, smoothnessEnc.getEnum(false, intsRef));
        assertTrue(Smoothness.BAD.ordinal() < Smoothness.VERY_BAD.ordinal());
    }
}