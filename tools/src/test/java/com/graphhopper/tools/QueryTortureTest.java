/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class QueryTortureTest
{
    @Test
    public void testGetQuery()
    {
        Query result = Query.parse("2013-08-07 18:06:50,905 [qtp1329318374-81] INFO  graphhopper.http.GraphHopperServlet - point=51.076329,13.738409&point=52.517037,13.38886&type=jsonp 46.4.67.134 en_US Wget/1.13.4 (linux-gnu) 51.076329, 13.738409->52.517037, 13.38886, distance: 189.4806800000001, time:123min, points:907, took:0.007393159, debug - idLookup:0.002483692s, algoInit:1.20837E-4s, dijkstraCH-routing:0.003138361s, extract time:1.66755E-4, simplify (1219->907):0.001040086s, instructions:2.26986E-4s, dijkstrabi, fastest, CAR");
        assertEquals("point=51.076329,13.738409&point=52.517037,13.38886&type=jsonp", result.createQueryString());
        assertEquals(51.076329, result.start.lat, 1e-5);
        assertEquals(13.38886, result.end.lon, 1e-5);
    }
}
