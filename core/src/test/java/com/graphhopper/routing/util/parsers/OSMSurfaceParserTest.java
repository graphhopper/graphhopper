package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Surface;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OSMSurfaceParserTest {
    private EncodingManager em;
    private EnumEncodedValue<Surface> surfaceEnc;
    private OSMSurfaceParser parser;

    @Before
    public void setUp() {
        parser = new OSMSurfaceParser();
        em = new EncodingManager.Builder().add(parser).build();
        surfaceEnc = em.getEnumEncodedValue(Surface.KEY, Surface.class);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = em.createRelationFlags();

        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Surface.OTHER, surfaceEnc.getEnum(false, intsRef));

        readerWay.setTag("surface", "cobblestone");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, intsRef));
        assertTrue(Surface.COBBLESTONE.ordinal() > Surface.ASPHALT.ordinal());

        readerWay.setTag("surface", "earth");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(Surface.DIRT, surfaceEnc.getEnum(false, intsRef));
    }
}