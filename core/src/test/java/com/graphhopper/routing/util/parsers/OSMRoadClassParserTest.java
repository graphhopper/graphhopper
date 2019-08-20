package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.routing.util.EncodingManager.Access.FERRY;
import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

public class OSMRoadClassParserTest {

    private EncodingManager em;
    private EnumEncodedValue<RoadClass> rcEnc;
    private OSMRoadClassParser parser;

    @Before
    public void setUp() {
        parser = new OSMRoadClassParser();
        em = new EncodingManager.Builder(4).add(parser).build();
        rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(RoadClass.PRIMARY, rcEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "unknownstuff");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("highway", "motorway_link");
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(RoadClass.MOTORWAY, rcEnc.getEnum(false, intsRef));
    }

    @Test
    public void testIgnore() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("route", "ferry");
        parser.handleWayTags(intsRef, readerWay, FERRY, 0);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, WAY, 0);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, intsRef));
    }
}