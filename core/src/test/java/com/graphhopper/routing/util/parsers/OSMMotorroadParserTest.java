package com.graphhopper.routing.util.parsers;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.Motorroad;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMMotorroadParserTest {

    private EncodingManager em;
    private IntsRef relFlags;
    private BooleanEncodedValue mrEnc;
    private OSMMotorroadParser parser;

    @Before
    public void setUp() {
        parser = new OSMMotorroadParser();
        em = new EncodingManager.Builder().add(parser).build();
        relFlags = em.createRelationFlags();
        mrEnc = em.getBooleanEncodedValue(Motorroad.KEY);
    }
    
    @Test
    public void testPrimaryMotorroad() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "primary");
        readerWay.setTag("motorroad", "yes");
        parser.handleWayTags(edgeFlags, readerWay, false, relFlags);
        assertEquals(true, mrEnc.getBool(false, edgeFlags));
    }
    
    @Test
    public void testMotorwayMotorroad() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef edgeFlags = em.createEdgeFlags();
        readerWay.setTag("highway", "motorway");
        readerWay.setTag("motorroad", "yes");
        parser.handleWayTags(edgeFlags, readerWay, false, relFlags);
        assertEquals(false, mrEnc.getBool(false, edgeFlags));
    }
}