package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.CarRoadAccessConditional;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMRoadAccessConditionalParserTest {

    private final EnumEncodedValue<CarRoadAccessConditional> restricted = CarRoadAccessConditional.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final OSMRoadAccessConditionalParser parser = new OSMRoadAccessConditionalParser(CarRoadAccessConditional.CONDITIONALS,
            (edgeId, access, b) -> restricted.setEnum(false, edgeId, access, b ? CarRoadAccessConditional.YES : CarRoadAccessConditional.NO), "2023-05-17");

    @Test
    public void testBasics() {
        String today = "2023 May 17";
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        assertEquals(CarRoadAccessConditional.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 - " + today + " )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.YES, restricted.getEnum(false, edgeId, edgeIntAccess));

        // range does not match => missing
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        // for now consider if seasonal range
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( Mar 23 - Aug 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "yes @ Apr-Nov");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.YES, restricted.getEnum(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testTaggingMistake() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "road");
        // ignore incomplete values
        way.setTag("access:conditional", "no @ 2023 Mar-Oct");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        // here the "1" will be interpreted as year -> incorrect range
        way.setTag("access:conditional", "no @ 1 Nov - 1 Mar");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarRoadAccessConditional.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));
    }
}
