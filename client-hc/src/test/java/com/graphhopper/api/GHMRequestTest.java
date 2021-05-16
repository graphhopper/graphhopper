package com.graphhopper.api;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GHMRequestTest {
    @Test
    public void testCompact() {
        GHMRequest request = new GHMRequest();
        for (int i = 0; i < 3; i++) {
            request.addFromPoint(new GHPoint());
            request.addFromPointHint("");
        }

        request.addToPoint(new GHPoint());
        request.addToPointHint("");

        request.compactPointHints();
        assertTrue(request.getToPointHints().isEmpty());
        assertTrue(request.getFromPointHints().isEmpty());
    }

    @Test
    public void testCompact2() {
        GHMRequest request = new GHMRequest();
        for (int i = 0; i < 3; i++) {
            request.addFromPoint(new GHPoint());
            request.addFromPointHint("");
        }

        request.addToPoint(new GHPoint());
        request.addToPointHint("x");

        request.compactPointHints();
        assertFalse(request.getToPointHints().isEmpty());
        assertTrue(request.getFromPointHints().isEmpty());
    }
}