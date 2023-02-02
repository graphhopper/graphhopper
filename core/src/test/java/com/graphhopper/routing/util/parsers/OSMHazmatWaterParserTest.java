package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatWaterParserTest {
    private EnumEncodedValue<HazmatWater> hazWaterEnc;
    private OSMHazmatWaterParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        hazWaterEnc = new EnumEncodedValue<>(HazmatWater.KEY, HazmatWater.class);
        hazWaterEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatWaterParser(hazWaterEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = new IntsRef(1);
        readerWay.setTag("hazmat:water", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatWater.NO, hazWaterEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("hazmat:water", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("hazmat:water", "permissive");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatWater.PERMISSIVE, hazWaterEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = new IntsRef(1);
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatWater.YES, hazWaterEnc.getEnum(false, edgeId, intAccess));
    }
}