package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatParserTest {
    private final EnumEncodedValue<Hazmat> hazEnc = Hazmat.create();
    private OSMHazmatParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        hazEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatParser(hazEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("hazmat", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hazmat.NO, hazEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hazmat", "yes");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, edgeAccess));
    }
}
