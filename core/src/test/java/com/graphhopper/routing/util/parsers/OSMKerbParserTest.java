package com.graphhopper.routing.util.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Kerb;
import com.graphhopper.storage.IntsRef;

public class OSMKerbParserTest {
    private EnumEncodedValue<Kerb> kerbEnc;
    private OSMKerbParser parser;

    @BeforeEach
    public void setUp() {
        kerbEnc = Kerb.create();
        kerbEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMKerbParser(kerbEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "footway");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Kerb.MISSING, kerbEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("kerb", "raised");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Kerb.RAISED, kerbEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}