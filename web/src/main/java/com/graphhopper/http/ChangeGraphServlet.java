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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.storage.change.ChangeGraphResponse;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class defines a new endpoint to submit access and speed changes to the graph.
 *
 * @author Peter Karich
 */
public class ChangeGraphServlet extends GHBaseServlet {

    @Inject
    private GraphHopperAPI graphHopper;

    @Inject
    private GHJson gson;

    @Override
    protected void doPost(HttpServletRequest httpReq, HttpServletResponse httpRes) throws ServletException, IOException {
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getPathInfo() + " " + httpReq.getQueryString();
        float took = -1;
        StopWatch sw = new StopWatch().start();
        try {
            JsonFeatureCollection collection = gson.fromJson(new InputStreamReader(httpReq.getInputStream(), Helper.UTF_CS), JsonFeatureCollection.class);
            // TODO put changeGraph on GraphHopperAPI interface and remove cast (or some other solution)
            if (!(graphHopper instanceof GraphHopper)) {
                throw new IllegalStateException("Graph change API not supported with public transit.");
            }
            // TODO make asynchronous!
            ChangeGraphResponse rsp = ((GraphHopper) graphHopper).changeGraph(collection.getFeatures());
            ObjectNode resObject = jsonNodeFactory.objectNode();
            resObject.put("updates", rsp.getUpdateCount());
            // prepare the consumer to get some changes not immediately when returning after POST
            resObject.put("scheduled_updates", 0);

            httpRes.setHeader("X-GH-Took", "" + Math.round(took * 1000));
            writeJson(httpReq, httpRes, resObject);

            took = sw.stop().getSeconds();
            logger.info(infoStr + " " + took);
        } catch (IllegalArgumentException ex) {
            took = sw.stop().getSeconds();
            logger.warn(infoStr + " " + took + ", " + ex.getMessage());
            writeError(httpRes, 400, "Wrong arguments for endpoint /change, " + infoStr);
        } catch (Exception ex) {
            took = sw.stop().getSeconds();
            logger.error(infoStr + " " + took, ex);
            writeError(httpRes, 500, "Error at endpoint /change, " + infoStr);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        throw new IllegalArgumentException("GET not allowed");
    }
}
