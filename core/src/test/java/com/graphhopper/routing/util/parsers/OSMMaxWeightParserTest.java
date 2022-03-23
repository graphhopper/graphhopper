package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.MaxWeight;
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
        IntsRef intsRef = new IntsRef(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(5.0, mwEnc.getDecimal(false, intsRef), .01);

        // if value is beyond the maximum then do not use infinity instead fallback to more restrictive maximum
        intsRef = new IntsRef(1);
        readerWay.setTag("maxweight", "50");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(mwEnc.getMaxDecimal(), mwEnc.getDecimal(false, intsRef), .01);
    }
}