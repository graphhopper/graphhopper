package com.graphhopper.routing.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CustomModelTest {

    @Test
    public void testMergeLimits() {
        CustomModel truck = new CustomModel().setVehicleWidth(3.);
        CustomModel car = new CustomModel().setVehicleWidth(2.);
        CustomModel bike = new CustomModel();

        assertEquals(2, car.merge(bike).getVehicleWidth(), .1);
        assertEquals(3, truck.merge(car).getVehicleWidth(), .1);
        try {
            car.merge(truck);
            fail("car is incompatible with truck as base");
        } catch (Exception ex) {
        }
    }

    @Test
    public void testMergeMap() {
        CustomModel truck = new CustomModel();
        truck.getPriority().put("road_class", createMap("primary", 1.5, "secondary", 2.0));
        CustomModel car = new CustomModel();
        car.getPriority().put("road_class", createMap("primary", 2.0, "tertiary", 1.2));

        car.merge(truck);
        Map map = (Map) car.getPriority().get("road_class");
        assertEquals(1.5 * 2.0, map.get("primary"));
        assertEquals(2.0, map.get("secondary"));
        assertEquals(1.2, map.get("tertiary"));

        truck.getPriority().put("road_class", createMap("primary", "incompatible"));
        try {
            car.merge(truck);
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