package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMMaxWeightParserTest {
    private DecimalEncodedValue mwEnc;
    private OSMMaxWeightParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        mwEnc = MaxWeight.create();
        mwEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMMaxWeightParser(mwEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(5.0, mwEnc.getDecimal(false, edgeId, intAccess), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(25.4, mwEnc.getDecimal(false, edgeId, intAccess), .01);
    }
}