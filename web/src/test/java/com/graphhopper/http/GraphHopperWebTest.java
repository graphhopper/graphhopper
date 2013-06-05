/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.http;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperWebTest {

    @Test
    public void testReadUnencoded() throws Exception {
        GraphHopperWeb instance = new GraphHopperWeb() {
            @Override
            InputStream fetch(String url) throws IOException {
                return getClass().getResourceAsStream("test.json");
            }
        }.encodePolyline(false);
        GHResponse res = instance.route(new GHRequest(11.561415, 49.9516, 11.560439, 49.950357));
        assertEquals(0.218915, res.distance(), 1e-5);
        assertEquals(7, res.points().size());
    }

    @Test
    public void testReadEncoded() throws Exception {
        GraphHopperWeb instance = new GraphHopperWeb() {
            @Override
            InputStream fetch(String url) throws IOException {
                return getClass().getResourceAsStream("test_encoded.json");
            }
        };
        GHResponse res = instance.route(new GHRequest(11.561415, 49.9516, 11.560439, 49.950357));
        assertEquals(0.218915, res.distance(), 1e-5);
        assertEquals(7, res.points().size());
    }
}
