package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.Toll;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

public class OSMTollParserTest {
    private EncodingManager em;
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @Before
    public void setUp() {
        parser = new OSMTollParser();
        em = new EncodingManager.Builder(4).add(parser).build();
        tollEnc = em.getEnumEncodedValue(Toll.KEY, Toll.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.NO, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));
        
        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));
        
        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, intsRef));
    }
}