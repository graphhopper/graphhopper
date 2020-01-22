package com.graphhopper.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHResponse;
import com.graphhopper.jackson.Jackson;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GHResponseRepresentationTest {

    @Test
    public void testPTResponse() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        GHResponse ghResponse = objectMapper.readValue(fixture("fixtures/pt-response.json"), GHResponse.class);
        assertFalse(ghResponse.getBest().getLegs().isEmpty());
    }

}
