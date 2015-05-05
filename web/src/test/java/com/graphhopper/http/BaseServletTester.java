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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Downloader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class BaseServletTester
{
    private static GHServer server;
    protected static Logger logger = LoggerFactory.getLogger(BaseServletTester.class);
    protected static int port;
    protected Injector injector;

    public void setUpGuice( Module... modules )
    {
        injector = Guice.createInjector(/*Stage.DEVELOPMENT,*/modules);
    }

    /**
     * This method will start jetty with andorra area loaded as OSM.
     */
    public void setUpJetty( CmdArgs args )
    {
        if (injector != null)
            throw new UnsupportedOperationException("do not call guice before");

        bootJetty(args, 3);
    }

    private void bootJetty( CmdArgs args, int retryCount )
    {
        if (server != null)
            return;

        server = new GHServer(args);

        if (injector == null)
            setUpGuice(new DefaultModule(args), new GHServletModule(args));

        for (int i = 0; i < retryCount; i++)
        {
            port = 18080 + i;
            args.put("jetty.port", "" + port);
            try
            {
                logger.info("Trying to start jetty at port " + port);
                server.start(injector);
//                server.join();
                break;
            } catch (Exception ex)
            {
                server = null;
                logger.error("Cannot start jetty at port " + port + " " + ex.getMessage());
            }
        }
    }

    public static void shutdownJetty( boolean force )
    {
        // this is too slow so allow force == false. Then on setUpJetty a new server is created on a different port
        if (force && server != null)
            try
            {
                server.stop();
            } catch (Exception ex)
            {
                logger.error("Cannot stop jetty", ex);
            }

        server = null;
    }

    protected String getTestRouteAPIUrl()
    {
        String host = "localhost";
        return "http://" + host + ":" + port + "/route";
    }
    
    protected String getTestNearestAPIUrl()
    {
        String host = "localhost";
        return "http://" + host + ":" + port + "/nearest";
    }
    
    protected JSONObject query( String query ) throws Exception
    {
        String resQuery = "";
        for (String q : query.split("\\&"))
        {
            int index = q.indexOf("=");
            if (index > 0)
                resQuery += q.substring(0, index + 1) + WebHelper.encodeURL(q.substring(index + 1));
            else
                resQuery += WebHelper.encodeURL(q);

            resQuery += "&";
        }
        String url = getTestRouteAPIUrl() + "?" + resQuery;
        Downloader downloader = new Downloader("web integration tester");
        return new JSONObject(downloader.downloadAsString(url));
    } 
    
    protected JSONObject nearestQuery( String query ) throws Exception
    {
        String resQuery = "";
        for (String q : query.split("\\&"))
        {
            int index = q.indexOf("=");
            if (index > 0)
                resQuery += q.substring(0, index + 1) + WebHelper.encodeURL(q.substring(index + 1));
            else
                resQuery += WebHelper.encodeURL(q);

            resQuery += "&";
        }
        String url = getTestNearestAPIUrl() + "?" + resQuery;
        Downloader downloader = new Downloader("web integration tester");
        return new JSONObject(downloader.downloadAsString(url));
    } 
}
