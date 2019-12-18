package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.MtbScale;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.routing.util.EncodingManager.Access.WAY;
import static org.junit.Assert.assertEquals;

public class OSMMtbScaleParserTest {
    private EncodingManager em;
    private IntsRef relFlags;
    private EnumEncodedValue<MtbScale> mtbEnc;
    private OSMMtbScaleParser parser;

    @Before
    public void setUp() {
        parser = new OSMMtbScaleParser();
        em = new EncodingManager.Builder().add(parser).build();
        relFlags = em.createRelationFlags();
        mtbEnc = em.getEnumEncodedValue(MtbScale.KEY, MtbScale.class);
    }

    @Test
    public void testSimple() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "path");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.NONE, mtbEnc.getEnum(false, edgeFlags));

        readerWay.setTag("mtb:scale", "0");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.MTB_0, mtbEnc.getEnum(false, edgeFlags));

        readerWay.setTag("mtb:scale", "1");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.MTB_1, mtbEnc.getEnum(false, edgeFlags));

        readerWay.setTag("mtb:scale", "1+");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.MTB_1, mtbEnc.getEnum(false, edgeFlags));

        readerWay.setTag("mtb:scale", "1-");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.MTB_1, mtbEnc.getEnum(false, edgeFlags));
    }

    /**
     * "21" should return as no scale, not scale 2.
     */
    @Test
    public void testInvalid21() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "path");
        readerWay.setTag("mtb:scale", "21");
        parser.handleWayTags(edgeFlags, readerWay, WAY, relFlags);
        assertEquals(MtbScale.NONE, mtbEnc.getEnum(false, edgeFlags));
    }
}