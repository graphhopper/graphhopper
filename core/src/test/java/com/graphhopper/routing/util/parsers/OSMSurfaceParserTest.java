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
        surfaceEnc = new EnumEncodedValue<>(Surface.KEY, Surface.class);
        surfaceEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMSurfaceParser(surfaceEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Surface.MISSING, surfaceEnc.getEnum(false, edgeId, intAccess));

        readerWay.setTag("surface", "cobblestone");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Surface.COBBLESTONE, surfaceEnc.getEnum(false, edgeId, intAccess));
        assertTrue(Surface.COBBLESTONE.ordinal() > Surface.ASPHALT.ordinal());

        readerWay.setTag("surface", "earth");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Surface.DIRT, surfaceEnc.getEnum(false, edgeId, intAccess));
    }
}