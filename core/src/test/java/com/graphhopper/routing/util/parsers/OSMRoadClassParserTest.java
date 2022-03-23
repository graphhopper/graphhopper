package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
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
        IntsRef edgeFlags = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.PRIMARY, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = new IntsRef(1);
        readerWay.setTag("highway", "unknownstuff");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = new IntsRef(1);
        readerWay.setTag("highway", "motorway_link");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.MOTORWAY, rcEnc.getEnum(false, edgeFlags));

        readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "cycleway");
        edgeFlags = new IntsRef(1);
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.CYCLEWAY, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testIgnore() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = new IntsRef(1);
        readerWay.setTag("route", "ferry");
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = new IntsRef(1);
        parser.handleWayTags(edgeFlags, readerWay, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }
}