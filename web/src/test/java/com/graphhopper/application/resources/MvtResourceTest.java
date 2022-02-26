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
                putObject("graph.flag_encoders", "car").
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
        Map map = attributes(multiLineString);
        assertEquals("Camì de les Pardines", map.get("name"));
    }

    @Test
    public void testWithDetailsInResponse() throws IOException {
        final Response response = clientTarget(app, "/mvt/15/16522/12102.mvt?details=max_speed&details=road_class&details=road_environment").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        JtsMvt result = MvtReader.loadMvt(is, new GeometryFactory(), new TagKeyValueMapConverter());
        final Map<String, JtsLayer> layerValues = result.getLayersByName();
        JtsLayer layer = layerValues.values().iterator().next();
        assertEquals(21, layer.getGeometries().size());

        Geometry geometry = layer.getGeometries().stream().
                filter(g -> attributes(g).get("name").equals("Avinguda de Tarragona, CG-1"))
                .findFirst().get();
        assertEquals("road", attributes(geometry).get("road_environment"));
        assertEquals(50.0, attributes(geometry).get("max_speed"));
        assertEquals("primary", attributes(geometry).get("road_class"));
    }

    private Map<String, Object> attributes(Geometry g) {
        return (Map<String, Object>) g.getUserData();
    }
}
