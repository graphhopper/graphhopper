package com.graphhopper.routing.util.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.TactilePaving;
import com.graphhopper.storage.IntsRef;

public class OSMTactilePavingParserTest {
    private EnumEncodedValue<TactilePaving> tactilePavingEnc;
    private OSMTactilePavingParser parser;

    @BeforeEach
    public void setUp() {
        tactilePavingEnc = TactilePaving.create();
        tactilePavingEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMTactilePavingParser(tactilePavingEnc);
    }

    @Test
    public void testSimpleTags() {
        IntsRef relFlags = new IntsRef(2);

        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("highway", "footway");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(TactilePaving.MISSING, tactilePavingEnc.getEnum(false, edgeId, edgeIntAccess));

        readerWay.setTag("tactile_paving", "incorrect");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        assertEquals(TactilePaving.INCORRECT,
                        tactilePavingEnc.getEnum(false, edgeId, edgeIntAccess));
    }
}