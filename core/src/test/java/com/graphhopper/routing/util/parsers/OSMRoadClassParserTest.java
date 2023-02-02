package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMRoadClassParserTest {
    private EnumEncodedValue<RoadClass> rcEnc;
    private OSMRoadClassParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        rcEnc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        rcEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMRoadClassParser(rcEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        ArrayIntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.PRIMARY, rcEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("highway", "unknownstuff");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("highway", "motorway_link");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.MOTORWAY, rcEnc.getEnum(false, edgeId, intAccess));

        readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "cycleway");
        intAccess = new ArrayIntAccess(1);
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.CYCLEWAY, rcEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testIgnore() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("route", "ferry");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        ArrayIntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeId, intAccess));
    }
}