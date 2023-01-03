package com.graphhopper.example;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;

public class HeadingExample {
    static String cache = "target/routing-graph-cache";
    static GraphHopper hopper;

    @BeforeAll
    public static void setUp() {
        Helper.removeDir(new File(cache));
        hopper = new GraphHopper();
        hopper.setOSMFile("../core/files/andorra.osm.pbf");
        hopper.setGraphHopperLocation(cache);
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));
        hopper.importOrLoad();
    }

    @Test
    public void without_heading() {
        GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
                setProfile("car");
        GHResponse response = hopper.route(request);
        if (response.hasErrors())
            throw new RuntimeException(response.getErrors().toString());
        assertEquals(84, Math.round(response.getBest().getDistance()));
    }

    @Test
    public void with_heading_start() {
        GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
                setHeadings(Arrays.asList(270d)).
                setProfile("car");
        GHResponse response = hopper.route(request);
        if (response.hasErrors())
            throw new RuntimeException(response.getErrors().toString());
        assertEquals(264, Math.round(response.getBest().getDistance()));
    }

    @Test
    public void with_heading_start_stop() {
        GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
                setHeadings(Arrays.asList(270d, 180d)).
                setProfile("car");
        GHResponse response = hopper.route(request);
        if (response.hasErrors())
            throw new RuntimeException(response.getErrors().toString());
        assertEquals(434, Math.round(response.getBest().getDistance()));
    }

    @Test
    public void with_heading_stop() {
        GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
                setHeadings(Arrays.asList(Double.NaN, 180d)).
                setProfile("car");
        GHResponse response = hopper.route(request);
        if (response.hasErrors())
            throw new RuntimeException(response.getErrors().toString());
        assertEquals(201, Math.round(response.getBest().getDistance()));
    }

    @AfterAll
    public static void tearDown() {
        hopper.close();
    }
}
