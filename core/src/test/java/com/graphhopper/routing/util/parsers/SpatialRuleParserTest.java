package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpatialRuleParserTest {

    @Test
    public void testMixParserAdding() {
        EncodingManager em = new EncodingManager.Builder().add(new SpatialRuleParser(null, Country.create())).build();
        assertTrue(em.hasEncodedValue(Country.KEY));
    }
}