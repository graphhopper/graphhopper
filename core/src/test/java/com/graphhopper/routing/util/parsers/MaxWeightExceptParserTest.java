package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaxWeightExceptParserTest {
    private final int edgeId = 0;
    private EnumEncodedValue<MaxWeightExcept> mwEnc;
    private MaxWeightExceptParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        mwEnc = MaxWeightExcept.create();
        mwEnc.init(new EncodedValue.InitializerConfig());
        parser = new MaxWeightExceptParser(mwEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testSimpleTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight:conditional", "none @delivery");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DELIVERY, mwEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.setTag("maxweight:conditional", "no@ (destination)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DESTINATION, mwEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testConditionalTags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.NONE, mwEnc.getEnum(false, edgeId, edgeAccess));

        // weight=5 is missing
        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("vehicle:conditional", "delivery @ (weight > 5)");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.NONE, mwEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("vehicle:conditional", "delivery @ (weight > 7.5)");
        readerWay.setTag("maxweight", "7.5");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DELIVERY, mwEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "destination @ (maxweight > 5)");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DESTINATION, mwEnc.getEnum(false, edgeId, edgeAccess));
    }
}
