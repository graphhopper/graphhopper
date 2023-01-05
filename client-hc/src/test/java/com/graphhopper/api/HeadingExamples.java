package com.graphhopper.api;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.Request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

@Disabled
public class HeadingExamples {
    static GraphHopperWeb hopper;

    @BeforeAll
    public static void setUp() {
        hopper = new GraphHopperWeb("http://localhost:8989/route");
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

    @Test
    public void with_heading_start_stop_lower_penalty() {
        GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
                setHeadings(Arrays.asList(270d, 180d)).
                putHint(Parameters.Routing.HEADING_PENALTY, 10).
                setProfile("car");
        GHResponse response = hopper.route(request);
        if (response.hasErrors())
            throw new RuntimeException(response.getErrors().toString());
        // same distance as without_heading
        assertEquals(84, Math.round(response.getBest().getDistance()));
    }
}