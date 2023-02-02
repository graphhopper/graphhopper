package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatParserTest {
    private final EnumEncodedValue<Hazmat> hazEnc = new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class);
    private OSMHazmatParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        hazEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatParser(hazEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("hazmat", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Hazmat.NO, hazEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("hazmat", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, edgeId, intAccess));
    }
}