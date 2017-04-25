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
package com.graphhopper.matching.http;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.graphhopper.util.CmdArgs;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.http.HttpStatus;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class BaseServletTester {

    private static final MediaType MT_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType MT_XML = MediaType.parse("application/gpx+xml; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private static MatchServer server;
    protected static Logger logger = LoggerFactory.getLogger(BaseServletTester.class);
    protected static int port;
    protected Injector injector;

    public void setUpGuice(Module... modules) {
        injector = Guice.createInjector(/*Stage.DEVELOPMENT,*/modules);
    }

    /**
     * This method will start jetty with andorra area loaded as OSM.
     */
    public void setUpJetty(CmdArgs args) {
        if (injector != null) {
            throw new UnsupportedOperationException("do not call guice before");
        }

        bootJetty(args, 3);
    }

    private void bootJetty(CmdArgs args, int retryCount) {
        if (server != null) {
            return;
        }

        server = new MatchServer(args);

        if (injector == null) {
            setUpGuice(server.createModule());
        }

        for (int i = 0; i < retryCount; i++) {
            port = 18080 + i;
            args.put("jetty.port", "" + port);
            try {
                logger.info("Trying to start jetty at port " + port);
                server.start(injector);
//                server.join();
                break;
            } catch (Exception ex) {
                server = null;
                logger.error("Cannot start jetty at port " + port + " " + ex.getMessage());
            }
        }
    }

    public static void shutdownJetty(boolean force) {
        // this is too slow so allow force == false. Then on setUpJetty a new server is created on a different port
        if (force && server != null) {
            try {
                server.stop();
            } catch (Exception ex) {
                logger.error("Cannot stop jetty", ex);
            }
        }

        server = null;
    }

    protected String getTestAPIUrl(String path) {
        String host = "localhost";
        return "http://" + host + ":" + port + "" + path;
    }

    protected String post(String path, int expectedStatusCode, String xmlOrJson) throws IOException {
        String url = getTestAPIUrl(path);
        MediaType type;
        if (xmlOrJson.startsWith("<")) {
            type = MT_XML;
        } else {
            type = MT_JSON;
        }
        Response rsp = client.newCall(new Request.Builder().url(url).
                post(RequestBody.create(type, xmlOrJson)).build()).execute();
        assertEquals(url + ", http status was:" + rsp.code(),
                HttpStatus.getMessage(expectedStatusCode), HttpStatus.getMessage(rsp.code()));
        return rsp.body().string();
    }

    protected String getResponse(String path, int expectedStatusCode) throws IOException {
        String url = getTestAPIUrl(path);
        Response rsp = client.newCall(new Request.Builder().url(url).build()).execute();
        assertEquals(url + ", http status was:" + rsp.code(),
                HttpStatus.getMessage(expectedStatusCode), HttpStatus.getMessage(rsp.code()));
        return rsp.body().string();
    }
}
