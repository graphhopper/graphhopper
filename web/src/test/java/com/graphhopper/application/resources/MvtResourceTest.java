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
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.wdtinc.mapbox_vector_tile.adapt.jts.MvtReader;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TagKeyValueMapConverter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsLayer;
import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsMvt;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiLineString;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class MvtResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.vehicles", "car").
                putObject("graph.encoded_values", "road_class,road_environment,max_speed,surface").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("car").setVehicle("car").setWeighting("fastest")));
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
        JtsMvt result = MvtReader.loadMvt(is, new GeometryFactory(), new TagKeyValueMapConverter());
        final Map<String, JtsLayer> layerValues = result.getLayersByName();
        assertEquals(1, layerValues.size());
        assertTrue(layerValues.containsKey("roads"));
        JtsLayer layer = layerValues.values().iterator().next();
        MultiLineString multiLineString = (MultiLineString) layer.getGeometries().iterator().next();
        assertEquals(42, multiLineString.getCoordinates().length);
        assertEquals("Camì de les Pardines", getUserData(multiLineString).get(STREET_NAME));
    }

    @Test
    public void testDetailsInResponse() throws IOException {
        final Response response = clientTarget(app, "/mvt/15/16522/12102.mvt").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        JtsMvt result = MvtReader.loadMvt(is, new GeometryFactory(), new TagKeyValueMapConverter());
        final Map<String, JtsLayer> layerValues = result.getLayersByName();
        JtsLayer layer = layerValues.values().iterator().next();
        assertEquals(21, layer.getGeometries().size());

        Geometry geometry = layer.getGeometries().stream().
                filter(g -> "Avinguda de Tarragona".equals(getUserData(g).get(STREET_NAME)))
                .findFirst().get();
        assertEquals("road", getUserData(geometry).get("road_environment"));
        assertEquals("50.0 | 50.0", getUserData(geometry).get("max_speed"));
        assertEquals("primary", getUserData(geometry).get("road_class"));
    }

    private Map<String, Object> getUserData(Geometry g) {
        return (Map<String, Object>) g.getUserData();
    }
}
