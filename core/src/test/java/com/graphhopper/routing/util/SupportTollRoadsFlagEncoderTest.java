package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.PMap;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SupportTollRoadsFlagEncoderTest {
    private final PMap pMap = new PMap();
    private final SupportTollRoadsFlagEncoder supportTollRoadsFlagEncoder = new SupportTollRoadsFlagEncoder(pMap);
    private final ReaderWay readerWay = new ReaderWay(1);
    private final ReaderWay readerWayWithoutTollBit = new ReaderWay(2);

    @Before
    public void defineWayBits() {
        supportTollRoadsFlagEncoder.defineWayBits(0, 0);
        readerWay.setTag("toll", String.valueOf(Boolean.TRUE));
        readerWay.setTag("highway", "motorroad");
        readerWayWithoutTollBit.setTag("toll", String.valueOf(Boolean.FALSE));
        readerWayWithoutTollBit.setTag("highway", "motorroad");
    }

    @Test
    public void notAccept() {
        readerWay.setTag("toll", String.valueOf(Boolean.TRUE));
        assertEquals(supportTollRoadsFlagEncoder.acceptWay(readerWay), 0);

        readerWay.setTag("toll", "yes");
        assertEquals(supportTollRoadsFlagEncoder.acceptWay(readerWay), 0);
        readerWay.removeTag("toll");


        readerWay.setTag("barrier", "toll_booth");
        assertEquals(supportTollRoadsFlagEncoder.acceptWay(readerWay), 0);
        readerWay.removeTag("barrier");
    }

    @Test
    public void accept() {
        assertNotEquals(supportTollRoadsFlagEncoder.acceptWay(readerWayWithoutTollBit), 0);
    }

}