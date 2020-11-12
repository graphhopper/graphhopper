package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.SpatialRuleId;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class SpatialRuleParserTest {

    @Test
    public void testMixParserAdding() {
        EncodingManager em = new EncodingManager.Builder().add(new SpatialRuleParser()).build();
        assertTrue(em.hasEncodedValue(SpatialRuleId.KEY));
    }
}