package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTrackTypeParserTest {
    private IntsRef relFlags;
    private EnumEncodedValue<TrackType> ttEnc;
    private OSMTrackTypeParser parser;

    @BeforeEach
    public void setUp() {
        ttEnc = new EnumEncodedValue<>(TrackType.KEY, TrackType.class);
        ttEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTrackTypeParser(ttEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("tracktype", "grade1");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.GRADE1, ttEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("tracktype", "grade2");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.GRADE2, ttEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("tracktype", "grade3");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.GRADE3, ttEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("tracktype", "grade4");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.GRADE4, ttEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay.setTag("tracktype", "grade5");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.GRADE5, ttEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testUnkownValue() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("tracktype", "unknownstuff");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.MISSING, ttEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(TrackType.MISSING, ttEnc.getEnum(false, edgeId, intAccess));
    }
}
