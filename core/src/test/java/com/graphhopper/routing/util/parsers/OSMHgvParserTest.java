package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMHgvParserTest {
    private final int edgeId = 0;
    private EnumEncodedValue<Hgv> hgvEnc;
    private OSMHgvParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        hgvEnc = Hgv.create();
        hgvEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHgvParser(hgvEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testSimpleTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv", "destination");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hgv.DESTINATION, hgvEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testConditionalTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 3.5)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hgv.NO, hgvEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        // for now we assume "hgv" to be only above 3.5
        readerWay.setTag("hgv:conditional", "delivery @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(Hgv.MISSING, hgvEnc.getEnum(false, edgeId, edgeAccess));
    }
}
