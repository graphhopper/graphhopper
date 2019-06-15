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
package com.graphhopper.http.resources;

import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.wdtinc.mapbox_vector_tile.adapt.jts.MvtReader;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TagKeyValueMapConverter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsLayer;
import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsMvt;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiLineString;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class MvtResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car").
                put("graph.encoded_values", "road_class,road_environment,max_speed,surface").
                put("prepare.ch.weightings", "no").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicMvtQuery() throws IOException {
        final Response response = app.client().target("http://localhost:8080/mvt/15/16528/12099.mvt").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        JtsMvt result = MvtReader.loadMvt(is, new GeometryFactory(), new TagKeyValueMapConverter());
        final Map<String, JtsLayer> layerValues = result.getLayersByName();
        assertEquals(1, layerValues.size());
        assertTrue(layerValues.containsKey("roads"));
        JtsLayer layer = layerValues.values().iterator().next();
        MultiLineString multiLineString = (MultiLineString) layer.getGeometries().iterator().next();
        assertEquals(42, multiLineString.getCoordinates().length);
        Map map = (Map) multiLineString.getUserData();
        assertEquals("Camì de les Pardines", map.get("name"));
    }

    @Test
    public void testWithDetailsInResponse() throws IOException {
        final Response response = app.client().target("http://localhost:8080/mvt/15/16522/12102.mvt?details=max_speed&details=road_class&details=road_environment").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InputStream is = response.readEntity(InputStream.class);
        JtsMvt result = MvtReader.loadMvt(is, new GeometryFactory(), new TagKeyValueMapConverter());
        final Map<String, JtsLayer> layerValues = result.getLayersByName();
        JtsLayer layer = layerValues.values().iterator().next();
        List layerGeoList = (List) layer.getGeometries();
        Geometry geometry = (Geometry) layerGeoList.get(0);
        assertEquals(19, geometry.getCoordinates().length);
        assertEquals(21, layerGeoList.size());

        Map map = (Map) ((Geometry) layerGeoList.get(0)).getUserData();
        assertTrue(Double.isInfinite((Double) map.get("max_speed")));
        assertEquals("residential", map.get("road_class"));

        map = (Map) ((Geometry) layerGeoList.get(1)).getUserData();
        assertEquals(50, (Double) map.get("max_speed"), .1);
        assertEquals("secondary", map.get("road_class"));
        assertEquals("road", map.get("road_environment"));

        map = (Map) ((Geometry) layerGeoList.get(12)).getUserData();
        assertEquals("bridge", map.get("road_environment"));
    }
}
