package com.graphhopper.routing.ev;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.ev.CarRoadAccess.NO;
import static com.graphhopper.routing.ev.CarRoadAccess.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CarRoadAccessTest {

    EnumEncodedValue<CarRoadAccess> enc = CarRoadAccess.create();
    EncodingManager em = new EncodingManager.Builder().add(enc).build();
    OSMRoadAccessParser<CarRoadAccess> parser = new OSMRoadAccessParser<>(
            enc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
            CarRoadAccess::countryHook, CarRoadAccess::find);

    @Test
    public void testBasics() {
        assertEquals(YES, CarRoadAccess.find("unknown"));
        assertEquals(NO, CarRoadAccess.find("no"));
    }

    @Test
    public void testParser() {
        assertEquals(YES, CarRoadAccess.find("unknown"));
        assertEquals(NO, CarRoadAccess.find("no"));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "agricultural");
        assertEquals(CarRoadAccess.AGRICULTURAL, getValue(way));
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertEquals(CarRoadAccess.FORESTRY, getValue(way));
        way.setTag("motor_vehicle", "forestry;agricultural");
        assertEquals(CarRoadAccess.FORESTRY, getValue(way)); // picks less restricted value
        way.setTag("motor_vehicle", "yes;forestry;agricultural");
        assertEquals(CarRoadAccess.YES, getValue(way));
    }

    CarRoadAccess getValue(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        parser.handleWayTags(edge.getEdge(), edgeIntAccess, way, new IntsRef(1));
        return edge.get(enc);
    }
}
