package com.graphhopper.routing.util.parsers;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.Hazmat;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMHazmatParserTest {

    private EncodingManager em;
    private EnumEncodedValue<Hazmat> hazEnc;
    private OSMHazmatParser parser;

    @Before
    public void setUp() {
        parser = new OSMHazmatParser();
        em = new EncodingManager.Builder().add(parser).build();
        hazEnc = em.getEnumEncodedValue(Hazmat.KEY, Hazmat.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "no");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Hazmat.NO, hazEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "yes");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
        
        intsRef = em.createEdgeFlags();
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }
    
    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }
}