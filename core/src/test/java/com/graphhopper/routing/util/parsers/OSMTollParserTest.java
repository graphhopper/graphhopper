package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMTollParserTest {
    private EnumEncodedValue<Toll> tollEnc;
    private OSMTollParser parser;

    @BeforeEach
    public void setUp() {
        tollEnc = Toll.create();
        tollEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTollParser(tollEnc);
    }

    @Test
    public void testSimpleTags() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef relFlags = new IntsRef(2);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "primary");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.MISSING, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:hgv", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N2", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.HGV, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        readerWay.setTag("highway", "primary");
        readerWay.setTag("toll", "yes");
        readerWay.setTag("toll:hgv", "yes");
        readerWay.setTag("toll:N2", "yes");
        readerWay.setTag("toll:N3", "yes");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(Toll.ALL, tollEnc.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    void country() {
        assertEquals(Toll.ALL, getToll("motorway", "", Country.HUN));
        assertEquals(Toll.HGV, getToll("trunk", "", Country.HUN));
        assertEquals(Toll.HGV, getToll("primary", "", Country.HUN));
        assertEquals(Toll.MISSING, getToll("secondary", "", Country.HUN));
        assertEquals(Toll.MISSING, getToll("tertiary", "", Country.HUN));

        assertEquals(Toll.ALL, getToll("motorway", "", Country.FRA));
        assertEquals(Toll.MISSING, getToll("trunk", "", Country.FRA));
        assertEquals(Toll.MISSING, getToll("primary", "", Country.FRA));

        assertEquals(Toll.MISSING, getToll("motorway", "", Country.MEX));
        assertEquals(Toll.MISSING, getToll("trunk", "", Country.MEX));
        assertEquals(Toll.MISSING, getToll("primary", "", Country.MEX));

        assertEquals(Toll.ALL, getToll("secondary", "toll=yes", Country.HUN));
        assertEquals(Toll.HGV, getToll("secondary", "toll:hgv=yes", Country.HUN));
        assertEquals(Toll.HGV, getToll("secondary", "toll:N3=yes", Country.HUN));
        assertEquals(Toll.NO, getToll("secondary", "toll=no", Country.HUN));
    }

    private Toll getToll(String highway, String toll, Country country) {
        ReaderWay readerWay = new ReaderWay(123L);
        readerWay.setTag("highway", highway);
        readerWay.setTag("country", country);
        String[] tollKV = toll.split("=");
        if (tollKV.length > 1)
            readerWay.setTag(tollKV[0], tollKV[1]);
        IntsRef relFlags = new IntsRef(2);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        return tollEnc.getEnum(false, edgeId, edgeIntAccess);
    }
}
