package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatWaterParserTest {
    private EnumEncodedValue<HazmatWater> hazWaterEnc;
    private OSMHazmatWaterParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        hazWaterEnc = HazmatWater.create();
        hazWaterEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatWaterParser(hazWaterEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        readerWay.setTag("hazmat:water", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatWater.NO, hazWaterEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hazmat:water", "yes");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("hazmat:water", "permissive");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatWater.PERMISSIVE, hazWaterEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, edgeId, edgeAccess));
    }
}
