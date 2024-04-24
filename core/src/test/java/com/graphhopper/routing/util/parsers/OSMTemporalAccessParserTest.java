package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.CarTemporalAccess;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EdgeBytesAccessArray;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMTemporalAccessParserTest {

    private final EnumEncodedValue<CarTemporalAccess> restricted = CarTemporalAccess.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final OSMTemporalAccessParser parser = new OSMTemporalAccessParser(CarTemporalAccess.CONDITIONALS,
            (edgeId, access, b) -> restricted.setEnum(false, edgeId, access, b ? CarTemporalAccess.YES : CarTemporalAccess.NO), "2023-05-17");

    @Test
    public void testBasics() {
        String today = "2023 May 17";
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        assertEquals(CarTemporalAccess.MISSING, restricted.getEnum(false, edgeId, edgeAccess));

        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.NO, restricted.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 - " + today + " )");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.NO, restricted.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.YES, restricted.getEnum(false, edgeId, edgeAccess));

        // for now consider if seasonal range
        edgeAccess = new EdgeBytesAccessArray(4);
        way.setTag("access:conditional", "no @ ( Mar 23 - Aug 23 )");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.NO, restricted.getEnum(false, edgeId, edgeAccess));

        // range does not match => inverse!
        edgeAccess = new EdgeBytesAccessArray(4);
        way.setTag("access:conditional", "no @ ( Jun 23 - Aug 23 )");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.YES, restricted.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 )");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.YES, restricted.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        way.setTag("access:conditional", "yes @ Apr-Nov");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.YES, restricted.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testTaggingMistake() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "road");
        // ignore incomplete values
        way.setTag("access:conditional", "no @ 2023 Mar-Oct");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.MISSING, restricted.getEnum(false, edgeId, edgeAccess));

        // here the "1" will be interpreted as year -> incorrect range
        way.setTag("access:conditional", "no @ 1 Nov - 1 Mar");
        parser.handleWayTags(edgeId, edgeAccess, way, BytesRef.EMPTY);
        assertEquals(CarTemporalAccess.MISSING, restricted.getEnum(false, edgeId, edgeAccess));
    }
}
