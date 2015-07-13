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
package com.graphhopper.http;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.util.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperWebTest
{
    // see also GraphHopperServletIT.testGraphHopperWeb for real routes against local jetty service    

    @Test
    public void testReadEncoded() throws Exception
    {
        Downloader downloader = new Downloader("GraphHopper Test")
        {
            @Override
            public InputStream fetch( HttpURLConnection conn, boolean readErrorStreamNoException ) throws IOException
            {
                return getClass().getResourceAsStream("test_encoded.json");
            }
        };
        GraphHopperWeb instance = new GraphHopperWeb();
        instance.setDownloader(downloader);
        GHResponse res = instance.route(new GHRequest(52.47379, 13.362808, 52.4736925, 13.3904394));
        assertEquals(2138.3, res.getDistance(), 1e-1);
        assertEquals(17, res.getPoints().getSize());
        assertEquals(5, res.getInstructions().getSize());
        assertEquals("(0,Geradeaus auf A 100,1268.519329705091,65237)", res.getInstructions().get(0).toString());
        assertEquals(11, res.getInstructions().get(0).getPoints().size());
    }
}
