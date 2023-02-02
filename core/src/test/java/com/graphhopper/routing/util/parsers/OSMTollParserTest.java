package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTollParserTest {
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @BeforeEach
    public void setUp() {
        tollEnc = new EnumEncodedValue<>(Toll.KEY, Toll.class);
        tollEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTollParser(tollEnc);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef relFlags = new IntsRef(2);
        IntsRef intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.MISSING, tollEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, intAccess));

        intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, intAccess));
    }
}