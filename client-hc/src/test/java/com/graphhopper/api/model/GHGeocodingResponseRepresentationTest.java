/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.api.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jackson.Jackson;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;

import static io.dropwizard.testing.FixtureHelpers.fixture;


public class GHGeocodingResponseRepresentationTest {

    @Test
    public void testGeocodingRepresentation() throws JsonProcessingException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        GHGeocodingResponse geocodingResponse = objectMapper.readValue(fixture("fixtures/geocoding-response.json"), GHGeocodingResponse.class);
        Envelope extent = geocodingResponse.getHits().get(0).getExtent();
        // Despite the unusual representation of the bounding box...
        Assert.assertEquals(10.0598605, extent.getMinX(), 0.0);
        Assert.assertEquals(10.0612079, extent.getMaxX(), 0.0);
        Assert.assertEquals(53.7445489, extent.getMinY(), 0.0);
        Assert.assertEquals(53.7456315, extent.getMaxY(), 0.0);
    }

}
