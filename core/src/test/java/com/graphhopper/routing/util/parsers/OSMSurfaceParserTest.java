package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMSurfaceParserTest {
    private EnumEncodedValue<Surface> surfaceEnc;
    private OSMSurfaceParser parser;

    @BeforeEach
    public void setUp() {
        surfaceEnc = Surface.create();
        surfaceEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSurfaceParser(surfaceEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.MISSING, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "cobblestone");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));
        assertTrue(Surface.COBBLESTONE.ordinal() > Surface.ASPHALT.ordinal());

        readerWay.setTag("surface", "wood");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.WOOD, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testSynonyms() {
        IntsRef relFlags = new IntsRef(2);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        readerWay.setTag("surface", "metal");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.PAVED, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "sett");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "unhewn_cobblestone");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "earth");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.DIRT, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "pebblestone");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.GRAVEL, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "grass_paver");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.GRASS, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testSubtypes() {
        IntsRef relFlags = new IntsRef(2);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        readerWay.setTag("surface", "concrete:plates");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.CONCRETE, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("surface", "cobblestone:flattened");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}