package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hazmat;
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
        IntsRef intsRef = new IntsRef(1);
        readerWay.setTag("hazmat", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.NO, hazEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay.setTag("hazmat", "yes");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay.setTag("hazmat", "designated");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = new IntsRef(1);
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(Hazmat.YES, hazEnc.getEnum(false, intsRef));
    }
}