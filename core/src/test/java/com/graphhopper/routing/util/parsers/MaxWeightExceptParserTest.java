package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaxWeightExceptParserTest {
    private final int edgeId = 0;
    private EnumEncodedValue<MaxWeightExcept> mwEnc;
    private MaxWeightExceptParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        mwEnc = MaxWeightExcept.create();
        mwEnc.init(new EncodedValue.InitializerConfig());
        parser = new MaxWeightExceptParser(mwEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("maxweight:conditional", "none @delivery");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DELIVERY, mwEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("maxweight:conditional", "no@ (destination)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DESTINATION, mwEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testConditionalTags() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "no @ (weight > 7.5)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.MISSING, mwEnc.getEnum(false, edgeId, edgeIntAccess));

        // weight=5 is missing
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("vehicle:conditional", "delivery @ (weight > 5)");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.MISSING, mwEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("vehicle:conditional", "delivery @ (weight > 7.5)");
        readerWay.setTag("maxweight", "7.5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DELIVERY, mwEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.clearTags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("hgv:conditional", "destination @ (maxweight > 5)");
        readerWay.setTag("maxweight", "5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(MaxWeightExcept.DESTINATION, mwEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}
