package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMScenicParserTest {
    private BooleanEncodedValue scenicEnc;
    private OSMScenicParser parser;

    @BeforeEach
    public void setUp() {
        scenicEnc = Scenic.create();
        scenicEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMScenicParser(scenicEnc);
    }

    @Test
    public void testScenicTags() {
        IntsRef relFlags = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(false, scenicEnc.getBool(false, edgeId, edgeIntAccess));

        readerWay.setTag("scenic", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(true, scenicEnc.getBool(false, edgeId, edgeIntAccess));
    }
}
