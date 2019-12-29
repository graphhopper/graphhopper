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
    private IntsRef relFlags;
    private EnumEncodedValue<RoadClass> rcEnc;
    private OSMRoadClassParser parser;

    @Before
    public void setUp() {
        parser = new OSMRoadClassParser();
        em = new EncodingManager.Builder().add(parser).build();
        relFlags = em.createRelationFlags();
        rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(RoadClass.PRIMARY, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "unknownstuff");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));

        edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "motorway_link");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(RoadClass.MOTORWAY, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testIgnore() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("route", "ferry");
        parser.handleWayTags(edgeFlags, readerWay, FERRY, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(RoadClass.OTHER, rcEnc.getEnum(false, edgeFlags));
    }
}