package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTollParserTest {
    private TagParserManager tpm;
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @BeforeEach
    public void setUp() {
        parser = new OSMTollParser();
        tpm = new TagParserManager.Builder().add(parser).build();
        tollEnc = tpm.getEnumEncodedValue(Toll.KEY, Toll.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef relFlags = tpm.createRelationFlags();
        IntsRef intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.MISSING, tollEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));

        intsRef = tpm.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));
    }
}