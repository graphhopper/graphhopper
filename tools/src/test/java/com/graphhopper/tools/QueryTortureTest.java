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
package com.graphhopper.tools;

import com.graphhopper.tools.QueryTorture.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class QueryTortureTest {
    @Test
    public void testGetQuery() {
        Query result = Query.parse("2016-05-04 16:37:37,647 [qtp1604002113-823] INFO  com.graphhopper.http.GHBaseServlet - point=46.444481%2C11.306992&point=46.07847%2C11.178589&locale=en_US&vehicle=car&weighting=fastest&elevation=true 127.0.0.1 en_US Directions API 195.232.147.121 [46.456721,11.258966, 46.15583,11.153478, 46.067933,11.223352, 46.456721,11.258966], took:0.008627106, , fastest, car, alternatives: 1, distance0: 146967.68084669442, time0: 112min, points0: 1507, debugInfo: idLookup:0.004824006s; , algoInit:2.6879E-5s, dijkstrabiCH-routing:5.69366E-4s, extract time:9.987E-5;, algoInit:1.6976E-5s, dijkstrabiCH-routing:3.22521E-4s, extract time:5.9076E-5;, algoInit:1.6084E-5s, dijkstrabiCH-routing:6.75566E-4s, extract time:8.2527E-5;, algoInit:2.6879E-5s, dijkstrabiCH-routing:5.69366E-4s, extract time:9.987E-5;, algoInit:1.6976E-5s, dijkstrabiCH-routing:3.22521E-4s, extract time:5.9076E-5;, algoInit:1.6084E-5s, dijkstrabiCH-routing:6.75566E-4s, extract time:8.2527E-5, simplify (1903->1507)");
        assertEquals("point=46.444481%2C11.306992&point=46.07847%2C11.178589&elevation=true&locale=en_US&weighting=fastest&vehicle=car", result.createQueryString());
        assertEquals(46.444481, result.start.lat, 1e-5);
        assertEquals(11.178589, result.end.lon, 1e-5);
    }
}
