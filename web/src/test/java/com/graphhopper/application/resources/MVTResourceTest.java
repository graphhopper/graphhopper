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
package com.graphhopper.application.resources;

import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import no.ecc.vectortile.VectorTileDecoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Geometry;

import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class MVTResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.encoded_values", "road_class,road_environment,max_speed,surface").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                setProfiles(List.of(TestProfiles.constantSpeed("car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicMvtQuery() throws IOException {
        final Response response = clientTarget(app, "/mvt/15/16528/12099.mvt").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        VectorTileDecoder.FeatureIterable features = new VectorTileDecoder().decode(readInputStream(is));
        assertEquals(Arrays.asList("roads"), new ArrayList<>(features.getLayerNames()));
        VectorTileDecoder.Feature feature = features.iterator().next();
        Map<String, Object> attributes = feature.getAttributes();
        Geometry geometry = feature.getGeometry();
        assertEquals(51, geometry.getCoordinates().length);
        assertEquals("Camì de les Pardines", attributes.get(STREET_NAME));
    }

    @Test
    public void testDetailsInResponse() throws IOException {
        final Response response = clientTarget(app, "/mvt/15/16522/12102.mvt").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        List<VectorTileDecoder.Feature> features = new VectorTileDecoder().decode(readInputStream(is)).asList();
        assertEquals(28, features.size());

        VectorTileDecoder.Feature feature = features.stream()
                .filter(f -> "Avinguda de Tarragona".equals(f.getAttributes().get(STREET_NAME)))
                .findFirst().get();
        assertEquals("road", feature.getAttributes().get("road_environment"));
        assertEquals("50.0 | 50.0", feature.getAttributes().get("max_speed"));
        assertEquals("primary", feature.getAttributes().get("road_class"));
    }

    private static byte[] readInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1)
            buffer.write(data, 0, nRead);
        return buffer.toByteArray();
    }
}
