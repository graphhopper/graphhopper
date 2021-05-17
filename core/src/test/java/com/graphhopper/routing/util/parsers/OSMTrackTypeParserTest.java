package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.TrackType;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTrackTypeParserTest {

    private EncodingManager em;
    private IntsRef relFlags;
    private EnumEncodedValue<TrackType> ttEnc;
    private OSMTrackTypeParser parser;

    @BeforeEach
    public void setUp() {
        parser = new OSMTrackTypeParser();
        em = new EncodingManager.Builder().add(parser).build();
        ttEnc = em.getEnumEncodedValue(TrackType.KEY, TrackType.class);
        relFlags = em.createRelationFlags();
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "grade1");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.GRADE1, ttEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "grade2");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.GRADE2, ttEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "grade3");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.GRADE3, ttEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "grade4");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.GRADE4, ttEnc.getEnum(false, intsRef));

        intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "grade5");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.GRADE5, ttEnc.getEnum(false, intsRef));
    }

    @Test
    public void testUnkownValue() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("tracktype", "unknownstuff");
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.MISSING, ttEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, false, relFlags);
        assertEquals(TrackType.MISSING, ttEnc.getEnum(false, intsRef));
    }
}
