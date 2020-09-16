package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OSMTollParserTest {
    private EncodingManager em;
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @Before
    public void setUp() {
        parser = new OSMTollParser();
        em = new EncodingManager.Builder().add(parser).build();
        tollEnc = em.getEnumEncodedValue(Toll.KEY, Toll.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef relFlags = em.createRelationFlags();
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.NO, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));
    }
}