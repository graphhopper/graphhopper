package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hazmat;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatParserTest {

    private EncodingManager em;
    private EnumEncodedValue<Hazmat> hazEnc;
    private OSMHazmatParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        parser = new OSMHazmatParser();
        em = new EncodingManager.Builder().add(parser).build();
        relFlags = em.createRelationFlags();
        hazEnc = em.getEnumEncodedValue(Hazmat.KEY, Hazmat.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.NO, hazEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }
}