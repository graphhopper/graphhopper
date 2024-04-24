package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
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
        BytesRef relFlags = new BytesRef(8);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.MISSING, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "cobblestone");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeAccess));
        assertTrue(Surface.COBBLESTONE.ordinal() > Surface.ASPHALT.ordinal());

        readerWay.setTag("surface", "wood");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.WOOD, surfaceEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testSynonyms() {
        BytesRef relFlags = new BytesRef(8);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        readerWay.setTag("surface", "metal");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.PAVED, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "sett");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "unhewn_cobblestone");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "earth");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.DIRT, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "pebblestone");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.GRAVEL, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "grass_paver");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.GRASS, surfaceEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testSubtypes() {
        BytesRef relFlags = new BytesRef(8);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        readerWay.setTag("surface", "concrete:plates");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.CONCRETE, surfaceEnc.getEnum(false, edgeId, edgeAccess));

        readerWay.setTag("surface", "cobblestone:flattened");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, edgeAccess));
    }
}
