package com.graphhopper.routing.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CustomModelTest {

    // TODO NOW
//    @Test
//    public void testMergeLimits() {
//        CustomModel truck = new CustomModel().setVehicleWidth(3.);
//        CustomModel car = new CustomModel().setVehicleWidth(2.);
//        CustomModel bike = new CustomModel().setVehicleWeight(0.02);
//
//        assertEquals(2, CustomModel.merge(bike, car).getVehicleWidth(), .1);
//        assertNull(bike.getVehicleWidth());
//        assertNull(car.getVehicleWeight());
//
//        assertEquals(3, CustomModel.merge(car, truck).getVehicleWidth(), .1);
//        try {
//            CustomModel.merge(truck, car);
//            fail("car is incompatible with truck as base");
//        } catch (Exception ex) {
//        }
//    }

    @Test
    public void testMergeEmptyModel() {
        CustomModel emptyCar = new CustomModel();
        CustomModel car = new CustomModel();
        car.getPriority().put("road_class", createMap("primary", 2.0, "tertiary", 1.2));
        // empty entries means "extend base" profile, i.e. primary=1
        Map map = (Map) CustomModel.merge(emptyCar, car).getPriority().get("road_class");
        assertEquals(2.0, map.get("primary"));
        assertEquals(1.2, map.get("tertiary"));

        map = (Map) CustomModel.merge(car, emptyCar).getPriority().get("road_class");
        assertEquals(2.0, map.get("primary"));
        assertEquals(1.2, map.get("tertiary"));
    }

    @Test
    public void testMergeMap() {
        CustomModel truck = new CustomModel();
        truck.getPriority().put("road_class", createMap("primary", 1.5, "secondary", 2.0));
        CustomModel car = new CustomModel();
        car.getPriority().put("road_class", createMap("primary", 2.0, "tertiary", 1.2));

        Map map = (Map) CustomModel.merge(truck, car).getPriority().get("road_class");
        assertEquals(1.5 * 2.0, map.get("primary"));
        assertEquals(2.0, map.get("secondary"));
        assertEquals(1.2, map.get("tertiary"));

        // do not change argument of merge method
        assertEquals(1.5, (Double) ((Map) truck.getPriority().get("road_class")).get("primary"), .1);

        truck.getPriority().put("road_class", createMap("primary", "incompatible"));
        try {
            CustomModel.merge(car, truck);
            fail("we cannot merge this");
        } catch (Exception ex) {
        }
    }

    Map createMap(Object... objects) {
        Map map = new HashMap();
        for (int i = 0; i < objects.length; i += 2) {
            map.put(objects[i], objects[i + 1]);
        }
        return map;
    }
}