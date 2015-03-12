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
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.PointList;
import com.graphhopper.wrapper.*;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class WebHelperTest {
    @Test
    public void testDecode() throws Exception {
        PointList list = WebHelper.decodePolyline("_p~iF~ps|U", 1, false);
        assertEquals(Helper.createPointList(38.5, -120.2), list);

        list = WebHelper.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3, false);
        assertEquals(Helper.createPointList(38.5, -120.2, 40.7, -120.95, 43.252, -126.453), list);
    }

    @Test
    public void testEncode() throws Exception {
        assertEquals("_p~iF~ps|U", WebHelper.encodePolyline(
                Helper.createPointList(38.5, -120.2)));

        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", WebHelper.encodePolyline(
                Helper.createPointList(38.5, -120.2, 40.7, -120.95, 43.252, -126.453)));
    }

    @Test
    public void testBoth() throws Exception {
        PointList list = Helper.createPointList(38.5, -120.2, 43.252, -126.453,
                40.7, -120.95, 50.3139, 10.61279, 50.04303, 9.49768);
        String str = WebHelper.encodePolyline(list);
        assertEquals(list, WebHelper.decodePolyline(str, list.getSize(), false));

        list = Helper.createPointList(38.5, -120.2, 43.252, -126.453,
                40.7, -120.95, 40.70001, -120.95001);
        str = WebHelper.encodePolyline(list);
        assertEquals(list, WebHelper.decodePolyline(str, list.getSize(), false));
    }

    @Test
    public void testDecode3D() throws Exception {
        PointList list = WebHelper.decodePolyline("_p~iF~ps|Uo}@", 1, true);
        assertEquals(Helper.createPointList3D(38.5, -120.2, 10), list);

        list = WebHelper.decodePolyline("_p~iF~ps|Uo}@_ulLnnqC_anF_mqNvxq`@?", 3, true);
        assertEquals(Helper.createPointList3D(38.5, -120.2, 10, 40.7, -120.95, 1234, 43.252, -126.453, 1234), list);
    }

    @Test
    public void testEncode3D() throws Exception {
        assertEquals("_p~iF~ps|Uo}@", WebHelper.encodePolyline(Helper.createPointList3D(38.5, -120.2, 10)));
        assertEquals("_p~iF~ps|Uo}@_ulLnnqC_anF_mqNvxq`@?", WebHelper.encodePolyline(
                Helper.createPointList3D(38.5, -120.2, 10, 40.7, -120.95, 1234, 43.252, -126.453, 1234)));
    }

    @Test
    public void testWrapResponse() {
        Downloader downloader = new Downloader("GraphHopper Test") {
            @Override
            public InputStream fetch(String url) throws IOException {
                return getClass().getResourceAsStream("test.json");
            }
        };

        GraphHopperWeb instance = new GraphHopperWeb().setPointsEncoded(false);
        instance.setDownloader(downloader);

        GHResponse res = instance.route(new GHRequest(52.47379, 13.362808, 52.4736925, 13.3904394));
        GHRestResponse restRes = WebHelper.wrapResponse(res, false, false);

        // Info
        assertNull(restRes.getInfo().getErrors());

        // Paths
        PathsBean paths = restRes.getPaths();
        assertNotNull(paths);
        assertEquals(paths.getBbox(), Arrays.asList(13.362854, 52.469482, 13.385837, 52.473849));
        assertEquals(paths.getDistance(), 2138.3027624572337, 0.1);

        // Points
        PointsBean points = paths.getPoints();
        assertNotNull(points);
        assertEquals(points.getType(), "LineString");
        assertEquals(points.getCoordinates().size(), 17);

        // Instructions
        List<InstructionBean> instructions = paths.getInstructions();
        assertNotNull(instructions);
        assertEquals(instructions.size(), 5);

        // First Instruction
        InstructionBean firstInstruction = instructions.get(0);
        assertNotNull(firstInstruction);
        assertEquals(firstInstruction.getDistance(), 1268.519, 0.1);
        assertEquals(firstInstruction.getInterval(), Arrays.asList(0, 11));
        assertEquals(firstInstruction.getSign(), Instruction.CONTINUE_ON_STREET);
        assertEquals(firstInstruction.getText(), "Geradeaus auf A 100");
        assertEquals(firstInstruction.getTime(), 65237);
        assertNull(firstInstruction.getAnnotationText());
        assertNull(firstInstruction.getAnnotationImportance());
    }
}