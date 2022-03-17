package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMAccessParserTest {

    private final IntsRef relFlags = new IntsRef(1);

    BooleanEncodedValue init(OSMAccessParser parser) {
        EncodingManager encodingManager = new EncodingManager.Builder().build();
        List<EncodedValue> list = new ArrayList<>();
        parser.createEncodedValues(encodingManager, list);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        list.stream().forEach(ev -> ev.init(config));
        return (BooleanEncodedValue) list.get(0);
    }

    @Test
    public void testCar() {
        OSMAccessParser car = new OSMAccessParser("car_access", TransportationMode.CAR);
        BooleanEncodedValue accessEnc = init(car);

        ReaderWay way = new ReaderWay(0);
        way.setTag("oneway", "yes");
        assertAccess(true, false, car, way, accessEnc);

        way = new ReaderWay(0);
        way.setTag("oneway", "-1");
        assertAccess(false, true, car, way, accessEnc);

        way = new ReaderWay(0);
        way.setTag("vehicle:forward", "no");
        assertAccess(false, true, car, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("vehicle:backward", "no");
        assertAccess(true, false, car, way, accessEnc);

        way = new ReaderWay(0);
        way.setTag("hgv:forward", "no");
        assertAccess(true, true, car, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertAccess(true, true, car, way, accessEnc);

        // this is no oneway, i.e. default access
        way.setTag("vehicle:backward", "designated");
        assertAccess(true, true, car, way, accessEnc);
    }

    @Test
    public void testHGV() {
        OSMAccessParser hgv = new OSMAccessParser("hgv_access", TransportationMode.HGV);
        BooleanEncodedValue accessEnc = init(hgv);

        ReaderWay way = new ReaderWay(0);
        way.setTag("oneway", "yes");
        assertAccess(true, false, hgv, way, accessEnc);

        way = new ReaderWay(0);
        way.setTag("oneway", "-1");
        assertAccess(false, true, hgv, way, accessEnc);

        way = new ReaderWay(0);
        way.setTag("hgv:forward", "no");
        assertAccess(false, true, hgv, way, accessEnc);
    }

    @Test
    public void testBikeAccess() {
        OSMAccessParser bike = new OSMAccessParser("bike_access", TransportationMode.BIKE);
        BooleanEncodedValue accessEnc = init(bike);

        ReaderWay way = new ReaderWay(1);
        way.setTag("bicycle", "yes");
        way.setTag("vehicle", "no");
        assertAccess(true, true, bike, way, accessEnc);

        way.setTag("bicycle", "no");
        assertAccess(false, false, bike, way, accessEnc);

        way.setTag("bicycle", "designated");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("vehicle", "no");
        way.setTag("bicycle", "yes");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("access", "no");
        assertAccess(false, false, bike, way, accessEnc);
        way.setTag("bicycle", "yes");
        assertAccess(true, true, bike, way, accessEnc);
    }

    @Test
    public void testBikeOneway() {
        OSMAccessParser bike = new OSMAccessParser("bike_access", TransportationMode.BIKE);
        BooleanEncodedValue accessEnc = init(bike);

        ReaderWay way = new ReaderWay(0);
        way.setTag("oneway", "yes");
        assertAccess(true, false, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("vehicle:forward", "no");
        assertAccess(false, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("bicycle:forward", "no");
        assertAccess(false, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("vehicle:backward", "no");
        assertAccess(true, false, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "no");
        assertAccess(true, false, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("motor_vehicle:backward", "no");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("bicycle:backward", "yes");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway", "yes");
        way.setTag("bicycle:backward", "yes");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway", "-1");
        way.setTag("bicycle:forward", "yes");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("bicycle:forward", "use_sidepath");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway", "yes");
        way.setTag("cycleway", "opposite");
        assertAccess(true, true, bike, way, accessEnc);
        way = new ReaderWay(1);
        way.setTag("oneway", "-1");
        way.setTag("cycleway", "opposite");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway", "yes");
        way.setTag("cycleway:left", "opposite");
        assertAccess(true, true, bike, way, accessEnc);

        // if oneway:bicycle=no then this mean that oneway tag needs to be ignored https://wiki.openstreetmap.org/wiki/Key:oneway:bicycle
        way = new ReaderWay(0);
        way.setTag("oneway", "yes");
        way.setTag("oneway:bicycle", "no");
        assertAccess(true, true, bike, way, accessEnc);

        way = new ReaderWay(1);
        way.setTag("oneway:bicycle", "yes");
        assertAccess(true, false, bike, way, accessEnc);
    }

    @Test
    public void testFoot() {
        OSMAccessParser foot = new OSMAccessParser("foot_access", TransportationMode.FOOT);
        BooleanEncodedValue accessEnc = init(foot);

        ReaderWay way = new ReaderWay(0);
        way.setTag("oneway", "yes");
        assertAccess(true, true, foot, way, accessEnc);

        way.setTag("foot", "no");
        assertAccess(false, false, foot, way, accessEnc);
    }

    void assertAccess(boolean fwd, boolean bwd, OSMAccessParser parser, ReaderWay way, BooleanEncodedValue accessEnc) {
        IntsRef edgeFlags = new IntsRef(1);
        parser.handleWayTags(edgeFlags, way, relFlags);
        assertEquals(fwd, accessEnc.getBool(false, edgeFlags), "forward access");
        assertEquals(bwd, accessEnc.getBool(true, edgeFlags), "backward access");
    }
}